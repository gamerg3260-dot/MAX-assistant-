package com.example.ai

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.security.SecureKeyManager
import com.example.voice.RealtimeAudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class RealtimeConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    STREAMING,
    INTERRUPTED,
    ERROR
}

/**
 * Low-latency bi-directional WebSocket client for MAX Assistant real-time audio and voice streaming.
 * Handles full-duplex live session streaming, real-time audio playback via RealtimeAudioPlayer,
 * and zero-latency client-side interruption signals for real-time barge-in.
 */
class MaxRealtimeWebSocketManager(
    private val context: Context,
    private val realtimeAudioPlayer: RealtimeAudioPlayer
) {
    private val tag = "MaxRealtimeWebSocket"
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite for WebSockets
            .writeTimeout(5, TimeUnit.SECONDS)
            .pingInterval(10, TimeUnit.SECONDS)
            .build()
    }

    private var activeWebSocket: WebSocket? = null
    private val isSessionActive = AtomicBoolean(false)
    private var pingStartTime = 0L

    // State flows
    private val _connectionState = MutableStateFlow(RealtimeConnectionState.DISCONNECTED)
    val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _latencyMs = MutableStateFlow(38L) // Baseline simulated/real ping
    val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

    private val _liveAssistantText = MutableStateFlow("")
    val liveAssistantText: StateFlow<String> = _liveAssistantText.asStateFlow()

    private val _isStreamingAudio = MutableStateFlow(false)
    val isStreamingAudio: StateFlow<Boolean> = _isStreamingAudio.asStateFlow()

    var onTextDeltaReceived: ((textDelta: String) -> Unit)? = null
    var onTurnCompleted: (() -> Unit)? = null
    var onErrorListener: ((errorMsg: String) -> Unit)? = null

    /**
     * Connects to the real-time bi-directional streaming endpoint.
     */
    fun connect() {
        if (isSessionActive.get()) return

        val apiKey = SecureKeyManager.getApiKey(context)
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(tag, "Cannot establish real-time WebSocket: API key missing.")
            _connectionState.value = RealtimeConnectionState.ERROR
            return
        }

        _connectionState.value = RealtimeConnectionState.CONNECTING

        val wsUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        pingStartTime = System.currentTimeMillis()

        try {
            activeWebSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val rtt = System.currentTimeMillis() - pingStartTime
                    _latencyMs.value = rtt.coerceAtLeast(25L)
                    _isConnected.value = true
                    _connectionState.value = RealtimeConnectionState.CONNECTED
                    isSessionActive.set(true)
                    Log.i(tag, "Real-time Bi-directional WebSocket connected successfully! Latency: ${_latencyMs.value}ms")

                    // Send session setup configuration
                    sendSessionSetupMessage(webSocket)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleServerTextMessage(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    handleServerAudioBinary(bytes.toByteArray())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(tag, "WebSocket closing: $code / $reason")
                    _isConnected.value = false
                    _connectionState.value = RealtimeConnectionState.DISCONNECTED
                    isSessionActive.set(false)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(tag, "WebSocket connection failure or live fallback: ${t.message}")
                    _isConnected.value = false
                    _connectionState.value = RealtimeConnectionState.ERROR
                    isSessionActive.set(false)
                    onErrorListener?.invoke("Live connection error: ${t.localizedMessage}")
                }
            })
        } catch (e: Exception) {
            Log.e(tag, "Failed to initiate WebSocket: ${e.message}", e)
            _connectionState.value = RealtimeConnectionState.ERROR
        }
    }

    private fun sendSessionSetupMessage(ws: WebSocket) {
        try {
            val setupJson = JSONObject().apply {
                val setupObj = JSONObject().apply {
                    put("model", "models/gemini-2.0-flash-exp")
                    val genConfig = JSONObject().apply {
                        val responseModalities = JSONArray().apply {
                            put("AUDIO")
                            put("TEXT")
                        }
                        put("responseModalities", responseModalities)
                        val speechConfig = JSONObject().apply {
                            val voiceConfig = JSONObject().apply {
                                val prebuiltVoice = JSONObject().apply {
                                    put("voiceName", "Puck")
                                }
                                put("prebuiltVoiceConfig", prebuiltVoice)
                            }
                            put("voiceConfig", voiceConfig)
                        }
                        put("speechConfig", speechConfig)
                    }
                    put("generationConfig", genConfig)
                }
                put("setup", setupObj)
            }
            ws.send(setupJson.toString())
        } catch (e: Exception) {
            Log.e(tag, "Error sending setup message: ${e.message}")
        }
    }

    /**
     * Parses server streaming text messages and real-time response chunks.
     */
    private fun handleServerTextMessage(jsonString: String) {
        try {
            val root = JSONObject(jsonString)
            val serverContent = root.optJSONObject("serverContent")
            if (serverContent != null) {
                val modelTurn = serverContent.optJSONObject("modelTurn")
                val parts = modelTurn?.optJSONArray("parts")
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        val text = part.optString("text")
                        if (text.isNotBlank()) {
                            _liveAssistantText.value += text
                            _connectionState.value = RealtimeConnectionState.STREAMING
                            onTextDeltaReceived?.invoke(text)
                        }

                        // Check for embedded inline audio
                        val inlineData = part.optJSONObject("inlineData")
                        if (inlineData != null) {
                            val base64Data = inlineData.optString("data")
                            if (base64Data.isNotBlank()) {
                                val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                _isStreamingAudio.value = true
                                realtimeAudioPlayer.enqueueAudioChunk(audioBytes)
                            }
                        }
                    }
                }

                val turnComplete = serverContent.optBoolean("turnComplete", false)
                if (turnComplete) {
                    _connectionState.value = RealtimeConnectionState.CONNECTED
                    _isStreamingAudio.value = false
                    onTurnCompleted?.invoke()
                }

                val interrupted = serverContent.optBoolean("interrupted", false)
                if (interrupted) {
                    Log.i(tag, "Server confirmed turn interruption.")
                    _connectionState.value = RealtimeConnectionState.INTERRUPTED
                    realtimeAudioPlayer.interruptNow()
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Error parsing WebSocket incoming message: ${e.message}")
        }
    }

    private fun handleServerAudioBinary(bytes: ByteArray) {
        _isStreamingAudio.value = true
        _connectionState.value = RealtimeConnectionState.STREAMING
        realtimeAudioPlayer.enqueueAudioChunk(bytes)
    }

    /**
     * Sends user text prompt over real-time bi-directional connection.
     */
    fun sendTextMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        _liveAssistantText.value = ""

        if (!isSessionActive.get() || activeWebSocket == null) {
            connect()
        }

        try {
            val msgJson = JSONObject().apply {
                val clientContent = JSONObject().apply {
                    val turns = JSONArray().apply {
                        val turn = JSONObject().apply {
                            put("role", "user")
                            val parts = JSONArray().apply {
                                val part = JSONObject().apply {
                                    put("text", trimmed)
                                }
                                put(part)
                            }
                            put("parts", parts)
                        }
                        put(turn)
                    }
                    put("turns", turns)
                    put("turnComplete", true)
                }
                put("clientContent", clientContent)
            }
            activeWebSocket?.send(msgJson.toString())
            _connectionState.value = RealtimeConnectionState.STREAMING
            Log.d(tag, "Sent real-time client text message: \"$trimmed\"")
        } catch (e: Exception) {
            Log.e(tag, "Error sending text message over WebSocket: ${e.message}")
        }
    }

    /**
     * Sends real-time streaming microphone audio chunk (PCM 16kHz) to server.
     */
    fun sendAudioChunk(pcmData: ByteArray) {
        if (pcmData.isEmpty() || !isSessionActive.get()) return

        try {
            val base64Pcm = Base64.encodeToString(pcmData, Base64.NO_WRAP)
            val chunkJson = JSONObject().apply {
                val realtimeInput = JSONObject().apply {
                    val mediaChunks = JSONArray().apply {
                        val chunk = JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Pcm)
                        }
                        put(chunk)
                    }
                    put("mediaChunks", mediaChunks)
                }
                put("realtimeInput", realtimeInput)
            }
            activeWebSocket?.send(chunkJson.toString())
        } catch (e: Exception) {
            Log.w(tag, "Error streaming audio frame over WebSocket: ${e.message}")
        }
    }

    /**
     * Instantly transmits an interruption signal over WebSocket and flushes local audio buffers.
     * Tells the server to cease streaming tokens for the current turn.
     */
    fun sendInterruptSignal() {
        _connectionState.value = RealtimeConnectionState.INTERRUPTED
        _isStreamingAudio.value = false

        // Flush local audio playback instantly
        realtimeAudioPlayer.interruptNow()

        if (isSessionActive.get() && activeWebSocket != null) {
            try {
                val interruptJson = JSONObject().apply {
                    val clientContent = JSONObject().apply {
                        put("turnComplete", true)
                        put("interrupted", true)
                    }
                    put("clientContent", clientContent)
                }
                activeWebSocket?.send(interruptJson.toString())
                Log.d(tag, "⚡ Transmitted real-time interrupt signal to WebSocket server.")
            } catch (e: Exception) {
                Log.w(tag, "Error sending interrupt signal: ${e.message}")
            }
        }
    }

    fun disconnect() {
        try {
            isSessionActive.set(false)
            activeWebSocket?.close(1000, "Client closed session")
            activeWebSocket = null
            _isConnected.value = false
            _connectionState.value = RealtimeConnectionState.DISCONNECTED
            realtimeAudioPlayer.interruptNow()
            Log.d(tag, "WebSocket disconnected.")
        } catch (e: Exception) {
            Log.e(tag, "Error closing WebSocket: ${e.message}")
        }
    }
}
