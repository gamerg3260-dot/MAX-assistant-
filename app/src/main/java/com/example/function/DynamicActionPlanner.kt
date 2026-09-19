package com.example.function

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * High-Performance Dynamic Action Planner & Execution Engine.
 * 
 * Enables MAX Assistant to:
 * 1. Dynamically evaluate natural language queries requiring one or multiple tasks.
 * 2. Formulate sequence plans (DAGs of actions) without hardcoded switch-cases.
 * 3. Execute actions dynamically via [DynamicFunctionRegistry].
 * 4. Collate and summarize results for synthesized speech and UI telemetry.
 */
class DynamicActionPlanner(
    private val registry: DynamicFunctionRegistry
) {
    private val tag = "DynamicActionPlanner"

    private val _activePlan = MutableStateFlow<DynamicActionPlan?>(null)
    val activePlan: StateFlow<DynamicActionPlan?> = _activePlan.asStateFlow()

    private val _lastExecutionSummary = MutableStateFlow<String?>(null)
    val lastExecutionSummary: StateFlow<String?> = _lastExecutionSummary.asStateFlow()

    /**
     * Evaluates whether a query contains multiple sub-actions or requires dynamic planning.
     */
    fun canDecomposeDynamically(query: String): Boolean {
        val lower = query.lowercase(Locale.ROOT)
        val conjunctions = listOf(" and ", " then ", " aur ", " & ", " also ", " next ", " fir ")
        if (conjunctions.any { lower.contains(it) }) return true

        // Compound keywords
        if (lower.contains("routine") || lower.contains("mode") || lower.contains("macro")) return true

        return false
    }

    /**
     * Formulates an action plan from a natural language query using the local fast decomposer.
     */
    fun formulateLocalActionPlan(query: String): DynamicActionPlan {
        val planId = UUID.randomUUID().toString()
        val steps = mutableListOf<PlannedActionStep>()

        // Split query into discrete sub-intents based on linguistic connectors
        val splitRegex = Regex("""\b(and then|and|then|aur fir|aur|also|next|fir)\b|,|;""", RegexOption.IGNORE_CASE)
        val rawSegments = query.split(splitRegex)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        var stepIndex = 0
        for (segment in rawSegments) {
            val step = matchSegmentToFunction(segment, stepIndex)
            if (step != null) {
                steps.add(step)
                stepIndex++
            }
        }

        val plan = DynamicActionPlan(
            planId = planId,
            originalQuery = query,
            steps = steps,
            formulatedVia = "LOCAL_EDGE_DECOMPOSER"
        )
        _activePlan.value = plan
        return plan
    }

    /**
     * Formulates an action plan by querying Gemini LLM with dynamic tool declarations.
     */
    suspend fun formulatePlanViaGemini(
        query: String,
        geminiService: com.example.ai.GeminiAutoResponderService
    ): DynamicActionPlan? = withContext(Dispatchers.IO) {
        val toolsJson = registry.toGeminiToolsJson()
        val result = geminiService.executeGeminiFunctionCalling(
            prompt = query,
            toolsJson = toolsJson
        )

        return@withContext when (result) {
            is com.example.ai.GeminiFunctionCallResult.Calls -> {
                formulatePlanFromGeminiToolCalls(query, result.functionCalls)
            }
            is com.example.ai.GeminiFunctionCallResult.TextOnly -> {
                // If Gemini answered in text only, fallback to local decomposition
                formulateLocalActionPlan(query)
            }
            is com.example.ai.GeminiFunctionCallResult.Error -> {
                Log.w(tag, "Gemini function calling failed: ${result.message}, falling back to edge decomposition")
                formulateLocalActionPlan(query)
            }
        }
    }
    fun formulatePlanFromGeminiToolCalls(query: String, toolCallsJson: JSONArray): DynamicActionPlan {
        val planId = UUID.randomUUID().toString()
        val steps = mutableListOf<PlannedActionStep>()

        for (i in 0 until toolCallsJson.length()) {
            val callObj = toolCallsJson.optJSONObject(i) ?: continue
            val funcName = callObj.optString("name")
            val argsObj = callObj.optJSONObject("args") ?: JSONObject()

            val argsMap = mutableMapOf<String, Any?>()
            val keys = argsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                argsMap[key] = argsObj.opt(key)
            }

            val step = PlannedActionStep(
                stepIndex = i,
                functionName = funcName,
                arguments = argsMap,
                description = "Execute $funcName with $argsMap"
            )
            steps.add(step)
        }

        val plan = DynamicActionPlan(
            planId = planId,
            originalQuery = query,
            steps = steps,
            formulatedVia = "GEMINI_FUNCTION_CALLING"
        )
        _activePlan.value = plan
        return plan
    }

    /**
     * Executes a formulated dynamic action plan step-by-step.
     */
    suspend fun executePlan(plan: DynamicActionPlan): FunctionExecutionResult = withContext(Dispatchers.Default) {
        val executedSummaries = mutableListOf<String>()
        var allSucceeded = true

        Log.i(tag, "Executing dynamic action plan ${plan.planId} (${plan.steps.size} steps)...")

        for (step in plan.steps) {
            val func = registry.getFunction(step.functionName)
            if (func == null) {
                step.isExecuted = true
                step.result = FunctionExecutionResult(
                    isSuccess = false,
                    resultSummary = "Unknown function: ${step.functionName}",
                    error = "Not registered"
                )
                allSucceeded = false
                continue
            }

            Log.d(tag, "Executing step #${step.stepIndex}: ${step.functionName} with args: ${step.arguments}")
            val result = try {
                func.execute(step.arguments)
            } catch (e: Exception) {
                Log.e(tag, "Step execution failed: ${e.message}", e)
                FunctionExecutionResult(
                    isSuccess = false,
                    resultSummary = "Failed executing ${step.functionName}: ${e.message}",
                    error = e.message
                )
            }

            step.isExecuted = true
            step.result = result

            if (!result.isSuccess) {
                allSucceeded = false
            }
            executedSummaries.add(result.resultSummary)
        }

        plan.isCompleted = true
        val finalMessage = if (executedSummaries.isNotEmpty()) {
            executedSummaries.joinToString(" ")
        } else {
            "No dynamic actions executed."
        }
        plan.finalFeedbackMessage = finalMessage
        _lastExecutionSummary.value = finalMessage

        return@withContext FunctionExecutionResult(
            isSuccess = allSucceeded,
            resultSummary = finalMessage,
            feedbackSpeech = finalMessage
        )
    }

    /**
     * Matches an individual query segment to a registered dynamic function schema.
     */
    private fun matchSegmentToFunction(segment: String, stepIndex: Int): PlannedActionStep? {
        val lower = segment.lowercase(Locale.ROOT)

        // 1. Hardware Toggles
        if (lower.contains("flashlight") || lower.contains("torch") || lower.contains("flash")) {
            val state = if (lower.contains("off") || lower.contains("band")) "off" else "on"
            return PlannedActionStep(
                stepIndex = stepIndex,
                functionName = "toggle_hardware_setting",
                arguments = mapOf("target" to "flashlight", "state" to state),
                description = "Toggle Flashlight ($state)"
            )
        }

        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            val state = if (lower.contains("off") || lower.contains("band")) "off" else "on"
            return PlannedActionStep(
                stepIndex = stepIndex,
                functionName = "toggle_hardware_setting",
                arguments = mapOf("target" to "wifi", "state" to state),
                description = "Toggle Wi-Fi ($state)"
            )
        }

        if (lower.contains("bluetooth")) {
            val state = if (lower.contains("off") || lower.contains("band")) "off" else "on"
            return PlannedActionStep(
                stepIndex = stepIndex,
                functionName = "toggle_hardware_setting",
                arguments = mapOf("target" to "bluetooth", "state" to state),
                description = "Toggle Bluetooth ($state)"
            )
        }

        if (lower.contains("volume") || lower.contains("sound")) {
            val numRegex = Regex("""\b(\d+)\b""")
            val level = numRegex.find(lower)?.groupValues?.get(1)?.toIntOrNull()
            val state = when {
                lower.contains("up") || lower.contains("increase") || lower.contains("badhao") -> "up"
                lower.contains("down") || lower.contains("decrease") || lower.contains("kam") -> "down"
                else -> "set"
            }
            return PlannedActionStep(
                stepIndex = stepIndex,
                functionName = "toggle_hardware_setting",
                arguments = mapOf("target" to "volume", "state" to state, "level" to level),
                description = "Adjust Volume"
            )
        }

        if (lower.contains("brightness")) {
            val numRegex = Regex("""\b(\d+)\b""")
            val level = numRegex.find(lower)?.groupValues?.get(1)?.toIntOrNull()
            val state = if (lower.contains("up") || lower.contains("increase")) "up" else "down"
            return PlannedActionStep(
                stepIndex = stepIndex,
                functionName = "toggle_hardware_setting",
                arguments = mapOf("target" to "brightness", "state" to state, "level" to level),
                description = "Adjust Brightness"
            )
        }

        if (lower.contains("dnd") || lower.contains("silent") || lower.contains("mute")) {
            return PlannedActionStep(
                stepIndex = stepIndex,
                functionName = "toggle_hardware_setting",
                arguments = mapOf("target" to "sound_mode", "state" to "silent"),
                description = "Enable Silent/DND mode"
            )
        }

        // 2. Application Launches
        val launchKeywords = listOf("open", "launch", "kholo", "start")
        for (kw in launchKeywords) {
            if (lower.contains(kw)) {
                val appName = lower.substringAfter(kw).trim()
                    .replace(Regex("""^(the|app|application)\s+"""), "")
                    .trim()
                if (appName.isNotBlank()) {
                    return PlannedActionStep(
                        stepIndex = stepIndex,
                        functionName = "launch_application",
                        arguments = mapOf("appName" to appName),
                        description = "Launch App: $appName"
                    )
                }
            }
        }

        // 3. Direct Calling
        if (lower.contains("call") || lower.contains("dial")) {
            val recipient = lower.substringAfter("call").substringAfter("dial").trim()
                .replace(Regex("""^(to|the)\s+"""), "")
                .trim()
            if (recipient.isNotBlank()) {
                return PlannedActionStep(
                    stepIndex = stepIndex,
                    functionName = "make_phone_call",
                    arguments = mapOf("recipient" to recipient),
                    description = "Call $recipient"
                )
            }
        }

        // 4. Camera & Selfie
        if (lower.contains("selfie") || (lower.contains("camera") && lower.contains("front"))) {
            return PlannedActionStep(
                stepIndex = stepIndex,
                functionName = "control_camera",
                arguments = mapOf("action" to "take_selfie"),
                description = "Capture Selfie"
            )
        } else if (lower.contains("photo") || lower.contains("picture") || lower.contains("take a picture")) {
            return PlannedActionStep(
                stepIndex = stepIndex,
                functionName = "control_camera",
                arguments = mapOf("action" to "take_photo"),
                description = "Capture Photo"
            )
        }

        // 5. Accessibility Scroll
        if (lower.contains("scroll") || lower.contains("swipe")) {
            val action = if (lower.contains("up") || lower.contains("upar")) "scroll_up" else "scroll_down"
            return PlannedActionStep(
                stepIndex = stepIndex,
                functionName = "navigate_accessibility",
                arguments = mapOf("action" to action),
                description = "Accessibility $action"
            )
        }

        // 6. Device Status
        if (lower.contains("battery") || lower.contains("charging")) {
            return PlannedActionStep(
                stepIndex = stepIndex,
                functionName = "query_device_status",
                arguments = mapOf("queryType" to "battery"),
                description = "Query Battery Level"
            )
        }

        if (lower.contains("time") || lower.contains("samay")) {
            return PlannedActionStep(
                stepIndex = stepIndex,
                functionName = "query_device_status",
                arguments = mapOf("queryType" to "time"),
                description = "Query Current Time"
            )
        }

        return null
    }
}
