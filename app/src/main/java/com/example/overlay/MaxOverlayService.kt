package com.example.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.AutoResponderApp
import com.example.MainActivity
import com.example.R
import com.example.ai.AiResult
import com.example.ui.SiriGlowWaveVisualizer
import com.example.ui.SiriThemeColors
import com.example.ui.SiriThemePresets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android System Alert Window Overlay Service that renders MAX Assistant over any app or lock screen.
 * Displays an interactive floating Siri-style bottom bar with glowing wave animations,
 * direct Gemini AI routing, and voice TTS responses.
 */
class MaxOverlayService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    companion object {
        private const val TAG = "MaxOverlayService"
        const val ACTION_SHOW = "com.example.overlay.ACTION_SHOW"
        const val ACTION_HIDE = "com.example.overlay.ACTION_HIDE"
        const val ACTION_START_LISTENING = "com.example.overlay.ACTION_START_LISTENING"
        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "max_assistant_overlay_channel"

        private val _isOverlayActive = MutableStateFlow(false)
        val isOverlayActive: StateFlow<Boolean> = _isOverlayActive.asStateFlow()

        private val _overlayStatus = MutableStateFlow("Tap mic to speak with MAX")
        val overlayStatus: StateFlow<String> = _overlayStatus.asStateFlow()

        private val _overlayResponse = MutableStateFlow<String?>(null)
        val overlayResponse: StateFlow<String?> = _overlayResponse.asStateFlow()

        private val _isListening = MutableStateFlow(false)
        val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

        private val _isProcessing = MutableStateFlow(false)
        val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

        private val _isSpeaking = MutableStateFlow(false)
        val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

        private val _rmsDbLevel = MutableStateFlow(0f)
        val rmsDbLevel: StateFlow<Float> = _rmsDbLevel.asStateFlow()

        fun showOverlay(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW permission missing")
                return
            }
            val intent = Intent(context, MaxOverlayService::class.java).apply {
                action = ACTION_SHOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun hideOverlay(context: Context) {
            val intent = Intent(context, MaxOverlayService::class.java).apply {
                action = ACTION_HIDE
            }
            context.startService(intent)
        }

        fun startListening(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                return
            }
            val intent = Intent(context, MaxOverlayService::class.java).apply {
                action = ACTION_START_LISTENING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MaxOverlayService onCreate()")
        createNotificationChannel()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_SHOW
        Log.d(TAG, "onStartCommand action=$action")

        startForegroundWithNotification()

        when (action) {
            ACTION_HIDE -> {
                removeOverlayView()
                stopSelf()
            }
            ACTION_START_LISTENING -> {
                showOverlayView()
                triggerSpeechRecognition()
            }
            else -> {
                showOverlayView()
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            try {
                startForeground(NOTIFICATION_ID, notification, type)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to startForeground with FOREGROUND_SERVICE_TYPE_MICROPHONE", e)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showOverlayView() {
        if (composeView != null) {
            _isOverlayActive.value = true
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Overlay permission not granted.")
            stopSelf()
            return
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            x = 0
            y = 0
        }

        val newLifecycleOwner = OverlayLifecycleOwner()
        newLifecycleOwner.onCreate()
        newLifecycleOwner.onStart()
        lifecycleOwner = newLifecycleOwner

        val newComposeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        }

        newComposeView.setViewTreeLifecycleOwner(newLifecycleOwner)
        newComposeView.setViewTreeViewModelStoreOwner(newLifecycleOwner)
        newComposeView.setViewTreeSavedStateRegistryOwner(newLifecycleOwner)

        newComposeView.setContent {
            OverlayContent(
                onMicClick = { triggerSpeechRecognition() },
                onCloseClick = {
                    hideOverlay(this)
                },
                onOpenAppClick = {
                    val appIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(appIntent)
                }
            )
        }

        try {
            windowManager?.addView(newComposeView, params)
            composeView = newComposeView
            _isOverlayActive.value = true
            Log.d(TAG, "Overlay view added successfully to WindowManager.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view to WindowManager", e)
        }
    }

    private fun removeOverlayView() {
        composeView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view", e)
            }
        }
        composeView = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
        _isOverlayActive.value = false
    }

    private fun triggerSpeechRecognition() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _overlayStatus.value = "Microphone permission needed. Open MAX Assistant."
            return
        }

        val app = AutoResponderApp.instance
        val stt = app.maxSttManager
        val gemini = app.geminiService
        val settingsRepo = app.settingsRepository
        val elevenLabs = app.elevenLabsTtsService
        val elevenLabsKey = app.elevenLabsKeyManager
        val announcer = app.callAnnouncer

        _isListening.value = true
        _isProcessing.value = false
        _isSpeaking.value = false
        _overlayStatus.value = "Listening to your voice..."

        stt.onSpeechRecognizedListener = { spokenQuery ->
            _isListening.value = false
            _isProcessing.value = true
            _overlayStatus.value = "User: \"$spokenQuery\" • Thinking with Gemini..."

            serviceScope.launch {
                val settings = settingsRepo.settings.value
                val result = gemini.generateMaxVoiceResponse(spokenQuery, settings)

                _isProcessing.value = false
                when (result) {
                    is AiResult.Success -> {
                        val reply = result.text
                        _overlayStatus.value = "MAX Assistant Response"
                        _overlayResponse.value = reply
                        _isSpeaking.value = true

                        if (elevenLabsKey.hasValidApiKey()) {
                            val audioRes = elevenLabs.generateSpeech(reply, "21m00Tcm4TlvDq8ikWAM")
                            when (audioRes) {
                                is com.example.voice.ElevenLabsResult.Success -> {
                                    elevenLabs.playAudio(audioRes.audioFile) {
                                        _isSpeaking.value = false
                                        _overlayStatus.value = "Tap mic to speak with MAX"
                                    }
                                }
                                is com.example.voice.ElevenLabsResult.Error -> {
                                    speakWithAndroidTts(reply, announcer)
                                }
                            }
                        } else {
                            speakWithAndroidTts(reply, announcer)
                        }
                    }
                    is AiResult.Error -> {
                        val err = "Gemini AI: ${result.message}"
                        _overlayStatus.value = err
                        _isSpeaking.value = false
                    }
                }
            }
        }

        stt.onErrorListener = { errorMsg ->
            _isListening.value = false
            _isProcessing.value = false
            _overlayStatus.value = "Speech recognition: $errorMsg"
        }

        serviceScope.launch {
            stt.rmsDbLevel.collect { db ->
                _rmsDbLevel.value = db
            }
        }

        stt.startListening(preferredLanguage = "hi-IN")
    }

    private fun speakWithAndroidTts(text: String, announcer: com.example.voice.CallAnnouncer) {
        _isSpeaking.value = true
        announcer.announceCaller(
            callerNameOrNumber = text,
            template = "{name}",
            repeatCount = 1,
            onDone = {
                _isSpeaking.value = false
                _overlayStatus.value = "Tap mic to speak with MAX"
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MaxOverlayService onDestroy()")
        removeOverlayView()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MAX Assistant Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows floating Siri-style overlay over system applications"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("MAX Assistant System Overlay")
            .setContentText("Active floating assistant ready on screen")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

/**
 * Visual Compose representation rendered inside the System Alert Window.
 */
@Composable
fun OverlayContent(
    onMicClick: () -> Unit,
    onCloseClick: () -> Unit,
    onOpenAppClick: () -> Unit
) {
    val status by MaxOverlayService.overlayStatus.collectAsState()
    val response by MaxOverlayService.overlayResponse.collectAsState()
    val isListening by MaxOverlayService.isListening.collectAsState()
    val isProcessing by MaxOverlayService.isProcessing.collectAsState()
    val isSpeaking by MaxOverlayService.isSpeaking.collectAsState()
    val rmsLevel by MaxOverlayService.rmsDbLevel.collectAsState()

    val currentTheme = SiriThemePresets.SiriSpectrum

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xF2060B14),
        border = BorderStroke(
            1.5.dp,
            Brush.horizontalGradient(
                colors = listOf(
                    currentTheme.primaryAccent,
                    currentTheme.secondaryAccent,
                    currentTheme.glowColor
                )
            )
        ),
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .border(1.dp, currentTheme.primaryAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.max_siri_logo),
                            contentDescription = "MAX Siri Logo",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isListening) Color(0xFFEF4444)
                                else if (isProcessing) currentTheme.secondaryAccent
                                else if (isSpeaking) Color(0xFF10B981)
                                else currentTheme.primaryAccent
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MAX Assistant",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Open in App
                    IconButton(
                        onClick = onOpenAppClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open MAX App",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Close overlay
                    IconButton(
                        onClick = onCloseClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss Overlay",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Status message
            Text(
                text = status,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            // Response Box if generated
            AnimatedVisibility(
                visible = !response.isNullOrBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, currentTheme.primaryAccent.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = response.orEmpty(),
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action Row: Mic Trigger & Glow Wave
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Interactive Mic Button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = if (isListening) listOf(Color(0xFFEF4444), Color(0xFFDC2626))
                                else listOf(currentTheme.primaryAccent, currentTheme.secondaryAccent)
                            )
                        )
                        .clickable { onMicClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Siri-Style Glowing Wave Animation
                SiriGlowWaveVisualizer(
                    isListening = isListening,
                    isProcessing = isProcessing,
                    isSpeaking = isSpeaking,
                    rmsDbLevel = rmsLevel,
                    theme = currentTheme,
                    height = 36.dp,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
