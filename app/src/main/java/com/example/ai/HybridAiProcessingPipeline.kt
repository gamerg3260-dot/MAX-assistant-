package com.example.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.ai.edgenlu.EdgeIntent
import com.example.ai.edgenlu.EdgeNluResult
import com.example.ai.edgenlu.QuantizedEdgeNluEngine
import com.example.voice.CommandRouteResult
import com.example.voice.LocalActionCategory
import com.example.voice.LocalVoiceCommandRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Origin source of AI execution in the Hybrid Pipeline.
 */
enum class AiExecutionSource {
    EDGE_NLU_OFFLINE,
    LOCAL_RULE_CACHE,
    CLOUD_GEMINI_HYBRID
}

/**
 * Telemetry details for the Hybrid AI Pipeline execution.
 */
data class AiPipelineTelemetry(
    val query: String = "",
    val executionSource: AiExecutionSource = AiExecutionSource.EDGE_NLU_OFFLINE,
    val matchedIntent: String = "NONE",
    val latencyMs: Long = 0L,
    val edgeNluLatencyMs: Long = 0L,
    val cloudLatencyMs: Long = 0L,
    val isOfflineExecuted: Boolean = true,
    val confidenceScore: Float = 1.0f,
    val extractedSlotsSummary: String = "",
    val statusSummary: String = "Idle"
)

/**
 * Comprehensive Hybrid AI Processing Pipeline for MAX Assistant.
 * 
 * Orchestrates Edge AI & Cloud Generative Models:
 * - Stage 1: Quantized Edge NLU parsing & sub-word slot extraction (< 2ms).
 * - Stage 2: Advanced Local Intent Router for 100% offline device control with 0ms network latency.
 * - Stage 3: Intelligent Cloud Gemini API delegation for complex multi-turn or creative reasoning.
 */
class HybridAiProcessingPipeline(
    private val context: Context,
    private val localRouter: LocalVoiceCommandRouter,
    private val geminiService: GeminiAutoResponderService
) {
    private val tag = "HybridAiPipeline"

    private val _telemetry = MutableStateFlow(AiPipelineTelemetry())
    val telemetry: StateFlow<AiPipelineTelemetry> = _telemetry.asStateFlow()

    /**
     * Executes the hybrid pipeline on an incoming user query.
     * Returns the finalized textual response, executes any associated hardware action,
     * and publishes real-time telemetry metrics.
     */
    suspend fun processQuery(
        rawQuery: String,
        settings: com.example.data.repository.AppSettings = com.example.AutoResponderApp.instance.settingsRepository.settings.value
    ): HybridQueryResult = withContext(Dispatchers.Default) {
        val totalStartTime = System.currentTimeMillis()
        val trimmed = rawQuery.trim()

        if (trimmed.isEmpty()) {
            return@withContext HybridQueryResult(
                response = "I'm listening. How can MAX help you?",
                source = AiExecutionSource.EDGE_NLU_OFFLINE,
                isHandledLocally = true,
                latencyMs = 0
            )
        }

        // 1. Stage 1: Quantized Edge NLU Intent & Slot Extraction (Offline, <2ms)
        val edgeResult: EdgeNluResult = QuantizedEdgeNluEngine.analyze(trimmed)
        val edgeNluTime = edgeResult.inferenceTimeMs
        Log.i(tag, "Stage 1 (Edge NLU): Intent=${edgeResult.intent.id}, Conf=${edgeResult.confidence}, Slots=${edgeResult.slots.rawEntities} in ${edgeNluTime}ms")

        // 2. Stage 2: Local Intent Router Execution (Offline Guaranteed, 0ms Network Latency)
        val localRouteResult = localRouter.routeCommand(trimmed)

        if (localRouteResult is CommandRouteResult.LocalAction) {
            val totalLatency = System.currentTimeMillis() - totalStartTime
            val slotsStr = edgeResult.slots.rawEntities.entries.joinToString(", ") { "${it.key}=${it.value}" }

            _telemetry.value = AiPipelineTelemetry(
                query = trimmed,
                executionSource = AiExecutionSource.EDGE_NLU_OFFLINE,
                matchedIntent = edgeResult.intent.id,
                latencyMs = totalLatency,
                edgeNluLatencyMs = edgeNluTime,
                cloudLatencyMs = 0L,
                isOfflineExecuted = true,
                confidenceScore = edgeResult.confidence,
                extractedSlotsSummary = slotsStr.ifBlank { "Direct Action" },
                statusSummary = "Executed 100% Offline via Edge NLU (${totalLatency}ms)"
            )

            // Trigger any deferred post-speech action immediately
            localRouteResult.postSpeechAction?.invoke()

            return@withContext HybridQueryResult(
                response = localRouteResult.feedbackMessage,
                source = AiExecutionSource.EDGE_NLU_OFFLINE,
                isHandledLocally = true,
                latencyMs = totalLatency,
                edgeNluResult = edgeResult,
                actionCategory = localRouteResult.category
            )
        }

        // 3. Stage 3: Fast Conversational Rule Match
        val fastRuleReply = FastLocalRuleEngine.evaluate(trimmed)
        if (fastRuleReply != null) {
            val totalLatency = System.currentTimeMillis() - totalStartTime
            _telemetry.value = AiPipelineTelemetry(
                query = trimmed,
                executionSource = AiExecutionSource.LOCAL_RULE_CACHE,
                matchedIntent = "fast_rule_cache",
                latencyMs = totalLatency,
                edgeNluLatencyMs = edgeNluTime,
                cloudLatencyMs = 0L,
                isOfflineExecuted = true,
                confidenceScore = 1.0f,
                extractedSlotsSummary = "Instant Rule Match",
                statusSummary = "Instant Match (0ms network latency)"
            )

            return@withContext HybridQueryResult(
                response = fastRuleReply,
                source = AiExecutionSource.LOCAL_RULE_CACHE,
                isHandledLocally = true,
                latencyMs = totalLatency,
                edgeNluResult = edgeResult
            )
        }

        // 4. Stage 4: Check Network Availability before Cloud Gemini Delegation
        val isOnline = isNetworkConnected()
        if (!isOnline) {
            val totalLatency = System.currentTimeMillis() - totalStartTime
            val offlineFallback = "You're currently offline. I've handled all local commands offline, but open-domain questions require an internet connection."
            _telemetry.value = AiPipelineTelemetry(
                query = trimmed,
                executionSource = AiExecutionSource.EDGE_NLU_OFFLINE,
                matchedIntent = "offline_graceful_fallback",
                latencyMs = totalLatency,
                edgeNluLatencyMs = edgeNluTime,
                cloudLatencyMs = 0L,
                isOfflineExecuted = true,
                confidenceScore = edgeResult.confidence,
                extractedSlotsSummary = "Offline fallback",
                statusSummary = "Offline Guard: Local commands operational"
            )

            return@withContext HybridQueryResult(
                response = offlineFallback,
                source = AiExecutionSource.EDGE_NLU_OFFLINE,
                isHandledLocally = true,
                latencyMs = totalLatency,
                edgeNluResult = edgeResult
            )
        }

        // 5. Stage 5: Cloud Gemini LLM Delegation for Generative / Open-Domain Queries
        val cloudStartTime = System.currentTimeMillis()
        Log.i(tag, "Stage 3: Delegating open-domain query to Gemini Cloud LLM: \"$trimmed\"")

        val geminiResult = geminiService.generateMaxVoiceResponse(
            userQuery = trimmed,
            settings = settings
        )
        val cloudLatency = System.currentTimeMillis() - cloudStartTime
        val totalLatency = System.currentTimeMillis() - totalStartTime

        val finalResponseText = when (geminiResult) {
            is AiResult.Success -> geminiResult.text
            is AiResult.Error -> geminiResult.message
        }

        _telemetry.value = AiPipelineTelemetry(
            query = trimmed,
            executionSource = AiExecutionSource.CLOUD_GEMINI_HYBRID,
            matchedIntent = "gemini_generative_reasoning",
            latencyMs = totalLatency,
            edgeNluLatencyMs = edgeNluTime,
            cloudLatencyMs = cloudLatency,
            isOfflineExecuted = false,
            confidenceScore = 0.95f,
            extractedSlotsSummary = "Cloud Gemini 2.5 Flash",
            statusSummary = "Hybrid Cloud LLM (${totalLatency}ms, Edge: ${edgeNluTime}ms, Cloud: ${cloudLatency}ms)"
        )

        return@withContext HybridQueryResult(
            response = finalResponseText,
            source = AiExecutionSource.CLOUD_GEMINI_HYBRID,
            isHandledLocally = false,
            latencyMs = totalLatency,
            edgeNluResult = edgeResult
        )
    }

    private fun isNetworkConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

/**
 * Result data class returned by the Hybrid AI Pipeline.
 */
data class HybridQueryResult(
    val response: String,
    val source: AiExecutionSource,
    val isHandledLocally: Boolean,
    val latencyMs: Long,
    val edgeNluResult: EdgeNluResult? = null,
    val actionCategory: LocalActionCategory? = null
)
