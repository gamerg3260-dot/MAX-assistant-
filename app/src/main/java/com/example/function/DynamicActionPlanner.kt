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
 * High-Performance Dynamic Action Planner & Step-by-Step Execution Engine.
 * 
 * Capabilities:
 * 1. Decomposes complex multi-step user requests into discrete, sequential sub-tasks.
 * 2. Checks and validates required Android permissions before executing hardware toggles or notification access.
 * 3. Executes each action step-by-step using strict try-catch isolation.
 * 4. Gracefully falls back on permission absence without crashing or failing silently.
 * 5. Provides immediate verbal or visual feedback to the user before and after executing each step.
 */
class DynamicActionPlanner(
    private val registry: DynamicFunctionRegistry
) {
    private val tag = "DynamicActionPlanner"

    private val _activePlan = MutableStateFlow<DynamicActionPlan?>(null)
    val activePlan: StateFlow<DynamicActionPlan?> = _activePlan.asStateFlow()

    private val _lastExecutionSummary = MutableStateFlow<String?>(null)
    val lastExecutionSummary: StateFlow<String?> = _lastExecutionSummary.asStateFlow()

    private val _currentExecutingStep = MutableStateFlow<PlannedActionStep?>(null)
    val currentExecutingStep: StateFlow<PlannedActionStep?> = _currentExecutingStep.asStateFlow()

    /**
     * Evaluates whether a query contains multiple sub-actions or requires dynamic planning.
     */
    fun canDecomposeDynamically(query: String): Boolean {
        val lower = query.lowercase(Locale.ROOT)
        val conjunctions = listOf(" and ", " then ", " aur ", " & ", " also ", " next ", " fir ", " after that ")
        if (conjunctions.any { lower.contains(it) }) return true

        // Compound keywords
        if (lower.contains("routine") || lower.contains("mode") || lower.contains("macro")) return true

        // Compound check: e.g. "turn on bluetooth, set volume to 80, check whatsapp"
        if (lower.contains(",") && (lower.contains("bluetooth") || lower.contains("volume") || lower.contains("whatsapp") || lower.contains("wifi") || lower.contains("light"))) {
            return true
        }

        return false
    }

    /**
     * Formulates an action plan from a natural language query using the local fast decomposer.
     */
    fun formulateLocalActionPlan(query: String): DynamicActionPlan {
        val planId = UUID.randomUUID().toString()
        val steps = mutableListOf<PlannedActionStep>()

        // Split query into discrete sub-intents based on linguistic connectors and delimiters
        val splitRegex = Regex("""\b(and then|after that|and|then|aur fir|aur|also|next|fir)\b|[,;]""", RegexOption.IGNORE_CASE)
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
                description = "Execute $funcName",
                status = StepStatus.PENDING
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
     * Executes a formulated dynamic action plan step-by-step with strict try-catch safety,
     * status broadcasting, and graceful permission handling.
     */
    suspend fun executePlan(
        plan: DynamicActionPlan,
        onStepStarting: ((PlannedActionStep) -> Unit)? = null,
        onStepFinished: ((PlannedActionStep, FunctionExecutionResult) -> Unit)? = null
    ): FunctionExecutionResult = withContext(Dispatchers.Default) {
        val successfulSummaries = mutableListOf<String>()
        val permissionWarnings = mutableListOf<String>()
        val failedSummaries = mutableListOf<String>()
        var hasFailures = false

        Log.i(tag, "Starting sequential execution for action plan ${plan.planId} (${plan.steps.size} steps)...")

        for (step in plan.steps) {
            plan.currentExecutingStepIndex = step.stepIndex
            step.status = StepStatus.EXECUTING
            _currentExecutingStep.value = step
            _activePlan.value = plan.copy()

            // Immediate visual/verbal hook before step execution
            onStepStarting?.invoke(step)

            val func = registry.getFunction(step.functionName)
            if (func == null) {
                step.isExecuted = true
                step.status = StepStatus.FAILED
                val errResult = FunctionExecutionResult(
                    isSuccess = false,
                    resultSummary = "Unknown function: ${step.functionName}",
                    error = "Not registered"
                )
                step.result = errResult
                failedSummaries.add("Unrecognized action ${step.description}")
                hasFailures = true
                onStepFinished?.invoke(step, errResult)
                continue
            }

            Log.d(tag, "Executing step #${step.stepIndex + 1}/${plan.steps.size}: ${step.description} (${step.functionName})")
            
            val result = try {
                func.execute(step.arguments)
            } catch (e: Exception) {
                Log.e(tag, "Exception during step execution: ${e.message}", e)
                FunctionExecutionResult(
                    isSuccess = false,
                    resultSummary = "Error during ${step.description}: ${e.localizedMessage ?: "Unknown error"}",
                    error = e.message
                )
            }

            step.isExecuted = true
            step.result = result

            if (result.requiresPermission) {
                step.status = StepStatus.PERMISSION_REQUIRED
                permissionWarnings.add(result.resultSummary)
            } else if (result.isSuccess) {
                step.status = StepStatus.SUCCESS
                successfulSummaries.add(result.resultSummary)
            } else {
                step.status = StepStatus.FAILED
                hasFailures = true
                failedSummaries.add(result.resultSummary)
            }

            _activePlan.value = plan.copy()
            onStepFinished?.invoke(step, result)
        }

        plan.isCompleted = true
        plan.currentExecutingStepIndex = -1
        _currentExecutingStep.value = null

        // Synthesize a graceful consolidated feedback message
        val finalMessageBuilder = StringBuilder()
        if (successfulSummaries.isNotEmpty()) {
            finalMessageBuilder.append(successfulSummaries.joinToString(". ")).append(".")
        }
        if (permissionWarnings.isNotEmpty()) {
            if (finalMessageBuilder.isNotEmpty()) finalMessageBuilder.append(" ")
            finalMessageBuilder.append("Note: ").append(permissionWarnings.joinToString(" "))
        }
        if (failedSummaries.isNotEmpty()) {
            if (finalMessageBuilder.isNotEmpty()) finalMessageBuilder.append(" ")
            finalMessageBuilder.append("Failed: ").append(failedSummaries.joinToString(". "))
        }

        val finalMessage = if (finalMessageBuilder.isNotBlank()) {
            finalMessageBuilder.toString().trim()
        } else {
            "All planned actions processed."
        }

        plan.finalFeedbackMessage = finalMessage
        _lastExecutionSummary.value = finalMessage
        _activePlan.value = plan.copy()

        return@withContext FunctionExecutionResult(
            isSuccess = !hasFailures && permissionWarnings.isEmpty(),
            resultSummary = finalMessage,
            feedbackSpeech = finalMessage,
            requiresPermission = permissionWarnings.isNotEmpty()
        )
    }

    /**
     * Matches an individual query segment to a registered dynamic function schema.
     */
    private fun matchSegmentToFunction(segment: String, stepIndex: Int): PlannedActionStep? {
        val lower = segment.lowercase(Locale.ROOT).trim()

        // 1. WhatsApp & Notification Reading
        if (lower.contains("whatsapp") || lower.contains("whats app")) {
            return PlannedActionStep(
                stepIndex = stepIndex,
                functionName = "check_whatsapp_messages",
                arguments = mapOf("limit" to 3),
                description = "Check WhatsApp Messages",
                status = StepStatus.PENDING
            )
        }

        // 2. Hardware Toggles
        if (lower.contains("flashlight") || lower.contains("torch") || lower.contains("flash")) {
            val state = if (lower.contains("off") || lower.contains("band") || lower.contains("stop")) "off" else "on"
            return PlannedActionStep(
                stepIndex = stepIndex,
                functionName = "toggle_hardware_setting",
                arguments = mapOf("target" to "flashlight", "state" to state),
                description = "Turn $state Flashlight",
                status = StepStatus.PENDING
            )
        }

        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            val state = if (lower.contains("off") || lower.contains("band") || lower.contains("disable")) "off" else "on"
            return PlannedActionStep(
                stepIndex = stepIndex,
                functionName = "toggle_hardware_setting",
                arguments = mapOf("target" to "wifi", "state" to state),
                description = "Turn $state Wi-Fi",
                status = StepStatus.PENDING
            )
        }

        if (lower.contains("bluetooth")) {
            val state = if (lower.contains("off") || lower.contains("band") || lower.contains("disable")) "off" else "on"
            return PlannedActionStep(
                stepIndex = stepIndex,
                functionName = "toggle_hardware_setting",
                arguments = mapOf("target" to "bluetooth", "state" to state),
                description = "Turn $state Bluetooth",
                status = StepStatus.PENDING
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
                description = if (level != null) "Set Volume to $level%" else "Adjust Volume ($state)",
                status = StepStatus.PENDING
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
                description = if (level != null) "Set Brightness to $level%" else "Adjust Brightness",
                status = StepStatus.PENDING
            )
        }

        if (lower.contains("dnd") || lower.contains("silent") || lower.contains("mute")) {
            return PlannedActionStep(
                stepIndex = stepIndex,
                functionName = "toggle_hardware_setting",
                arguments = mapOf("target" to "sound_mode", "state" to "silent"),
                description = "Enable Silent Mode",
                status = StepStatus.PENDING
            )
        }

        // 3. Application Launches
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
                        description = "Launch $appName",
                        status = StepStatus.PENDING
                    )
                }
            }
        }

        // 4. Direct Calling
        if (lower.contains("call") || lower.contains("dial")) {
            val recipient = lower.substringAfter("call").substringAfter("dial").trim()
                .replace(Regex("""^(to|the)\s+"""), "")
                .trim()
            if (recipient.isNotBlank()) {
                return PlannedActionStep(
                    stepIndex = stepIndex,
                    functionName = "make_phone_call",
                    arguments = mapOf("recipient" to recipient),
                    description = "Call $recipient",
                    status = StepStatus.PENDING
                )
            }
        }

        // 5. Camera & Selfie
        if (lower.contains("selfie") || (lower.contains("camera") && lower.contains("front"))) {
            return PlannedActionStep(
                stepIndex = stepIndex,
                functionName = "control_camera",
                arguments = mapOf("action" to "take_selfie"),
                description = "Capture Selfie",
                status = StepStatus.PENDING
            )
        } else if (lower.contains("photo") || lower.contains("picture") || lower.contains("take a picture")) {
            return PlannedActionStep(
                stepIndex = stepIndex,
                functionName = "control_camera",
                arguments = mapOf("action" to "take_photo"),
                description = "Capture Photo",
                status = StepStatus.PENDING
            )
        }

        // 6. Accessibility Scroll
        if (lower.contains("scroll") || lower.contains("swipe")) {
            val action = if (lower.contains("up") || lower.contains("upar")) "scroll_up" else "scroll_down"
            return PlannedActionStep(
                stepIndex = stepIndex,
                functionName = "navigate_accessibility",
                arguments = mapOf("action" to action),
                description = "Accessibility ${action.replace("_", " ")}",
                status = StepStatus.PENDING
            )
        }

        // 7. Device Status
        if (lower.contains("battery") || lower.contains("charging")) {
            return PlannedActionStep(
                stepIndex = stepIndex,
                functionName = "query_device_status",
                arguments = mapOf("queryType" to "battery"),
                description = "Check Battery Status",
                status = StepStatus.PENDING
            )
        }

        if (lower.contains("time") || lower.contains("samay")) {
            return PlannedActionStep(
                stepIndex = stepIndex,
                functionName = "query_device_status",
                arguments = mapOf("queryType" to "time"),
                description = "Check Time",
                status = StepStatus.PENDING
            )
        }

        return null
    }
}
