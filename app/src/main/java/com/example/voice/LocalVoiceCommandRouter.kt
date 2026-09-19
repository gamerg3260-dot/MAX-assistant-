package com.example.voice

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.example.accessibility.MaxAccessibilityService
import com.example.ai.FastLocalRuleEngine
import com.example.camera.MaxCameraManager
import com.example.launcher.AppLauncherManager
import com.example.sos.EmergencySosManager
import com.example.telephony.DirectCallManager
import com.example.toggle.DeviceToggleManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result of Local Command Parsing and Intent Routing.
 */
sealed class CommandRouteResult {
    /**
     * Represents a locally handled intent executed directly on device (0ms latency, bypasses LLM).
     */
    data class LocalAction(
        val category: LocalActionCategory,
        val feedbackMessage: String,
        val actionType: String,
        val launchIntent: Intent? = null,
        val postSpeechAction: (() -> Unit)? = null
    ) : CommandRouteResult()

    /**
     * Complex natural language query requiring Gemini LLM generative reasoning.
     */
    data class ComplexAiQuery(
        val cleanedQuery: String,
        val originalQuery: String
    ) : CommandRouteResult()
}

enum class LocalActionCategory {
    APP_LAUNCH,
    DEVICE_TOGGLE,
    DIRECT_CALL,
    EMERGENCY_SOS,
    CAMERA_CONTROL,
    ACCESSIBILITY,
    LOCAL_UTILITY,
    CONVERSATIONAL_RULE
}

/**
 * High-Performance Local Command Parser & Intelligent Voice Query Router for MAX Assistant.
 * 
 * - Parses and executes direct device actions immediately (0ms latency, no network / LLM overhead).
 * - Routes only complex natural language and open-domain queries to the Gemini API.
 */
class LocalVoiceCommandRouter(
    private val context: Context,
    private val directCallManager: DirectCallManager,
    private val appLauncherManager: AppLauncherManager,
    private val deviceToggleManager: DeviceToggleManager,
    private val emergencySosManager: EmergencySosManager,
    private val maxCameraManager: MaxCameraManager
) {
    private val tag = "LocalVoiceCmdRouter"

    /**
     * Evaluates speech input. Returns [CommandRouteResult.LocalAction] if a local action or rule
     * can be executed directly on device, or [CommandRouteResult.ComplexAiQuery] to route to Gemini API.
     */
    fun routeCommand(spokenQuery: String): CommandRouteResult {
        val trimmed = spokenQuery.trim()
        if (trimmed.isBlank()) {
            return CommandRouteResult.LocalAction(
                category = LocalActionCategory.CONVERSATIONAL_RULE,
                feedbackMessage = "I'm listening. How can MAX help you?",
                actionType = "EMPTY_QUERY"
            )
        }

        val lower = trimmed.lowercase(Locale.ROOT)
            .replace(Regex("""^[.,!?:;'"\-]+|[.,!?:;'"\-]+$"""), "")
            .trim()

        Log.i(tag, "Routing query: \"$trimmed\" (normalized: \"$lower\")")

        // 1. Direct Calling & Phone Dialer Intent
        val callRes = directCallManager.processVoiceCallCommand(trimmed)
        if (callRes.isHandled) {
            Log.i(tag, "Matched DIRECT_CALL intent: ${callRes.feedbackMessage}")
            return CommandRouteResult.LocalAction(
                category = LocalActionCategory.DIRECT_CALL,
                feedbackMessage = callRes.feedbackMessage,
                actionType = "DIRECT_CALL",
                launchIntent = callRes.callIntent
            )
        }

        // 2. Emergency SOS & Live Location
        val sosRes = emergencySosManager.processVoiceSosCommand(trimmed)
        if (sosRes.isHandled) {
            Log.i(tag, "Matched EMERGENCY_SOS intent: ${sosRes.feedbackMessage}")
            return CommandRouteResult.LocalAction(
                category = LocalActionCategory.EMERGENCY_SOS,
                feedbackMessage = sosRes.feedbackMessage,
                actionType = sosRes.actionTaken ?: "EMERGENCY_SOS"
            )
        }

        // 3. Camera & Selfie Triggers
        val cameraRes = maxCameraManager.processVoiceCameraCommand(trimmed)
        if (cameraRes.isHandled) {
            Log.i(tag, "Matched CAMERA_CONTROL intent: ${cameraRes.feedbackMessage}")
            return CommandRouteResult.LocalAction(
                category = LocalActionCategory.CAMERA_CONTROL,
                feedbackMessage = cameraRes.feedbackMessage,
                actionType = cameraRes.actionTaken ?: "CAMERA_CONTROL"
            )
        }

        // 4. Accessibility & UI Navigation Actions (Scroll, Back, Home)
        val accessRes = MaxAccessibilityService.processVoiceAccessibilityCommand(trimmed)
        if (accessRes.isHandled) {
            Log.i(tag, "Matched ACCESSIBILITY intent: ${accessRes.feedbackMessage}")
            return CommandRouteResult.LocalAction(
                category = LocalActionCategory.ACCESSIBILITY,
                feedbackMessage = accessRes.feedbackMessage,
                actionType = accessRes.actionTaken ?: "ACCESSIBILITY"
            )
        }

        // 5. Mobile Hardware & Quick Settings Toggles (Flashlight, Wi-Fi, Sound, Brightness, DND)
        val toggleRes = deviceToggleManager.processVoiceToggleCommand(trimmed)
        if (toggleRes.isHandled) {
            Log.i(tag, "Matched DEVICE_TOGGLE intent: ${toggleRes.feedbackMessage}")
            return CommandRouteResult.LocalAction(
                category = LocalActionCategory.DEVICE_TOGGLE,
                feedbackMessage = toggleRes.feedbackMessage,
                actionType = toggleRes.actionTaken ?: "DEVICE_TOGGLE"
            )
        }

        // 6. Installed App Launching & System Utilities
        val appLaunchRes = appLauncherManager.processVoiceAppLaunchCommand(trimmed)
        if (appLaunchRes.isHandled) {
            Log.i(tag, "Matched APP_LAUNCH intent: ${appLaunchRes.feedbackMessage}")
            return CommandRouteResult.LocalAction(
                category = LocalActionCategory.APP_LAUNCH,
                feedbackMessage = appLaunchRes.feedbackMessage,
                actionType = "APP_LAUNCH",
                launchIntent = appLaunchRes.launchIntent,
                postSpeechAction = {
                    appLaunchRes.launchIntent?.let { appLauncherManager.launchIntentNow(it) }
                }
            )
        }

        // 7. Local Device Utilities (Battery, Time, Date, Math Calculations)
        val utilityResult = evaluateLocalUtility(lower, trimmed)
        if (utilityResult != null) {
            Log.i(tag, "Matched LOCAL_UTILITY intent: ${utilityResult.feedbackMessage}")
            return utilityResult
        }

        // 8. Fast Conversational Rules & Identity (Greetings, Thanks, Creator, Goodbyes)
        val conversationalResult = evaluateConversationalRule(lower, trimmed)
        if (conversationalResult != null) {
            Log.i(tag, "Matched CONVERSATIONAL_RULE intent: ${conversationalResult.feedbackMessage}")
            return conversationalResult
        }

        // 9. If no local intent matched -> Route to Gemini API for complex natural language reasoning
        Log.i(tag, "No local command matched. Routing query to Gemini API: \"$trimmed\"")
        return CommandRouteResult.ComplexAiQuery(
            cleanedQuery = trimmed,
            originalQuery = spokenQuery
        )
    }

    /**
     * Evaluates local utilities like Battery Level, Current Time, Date, Day, and Basic Math.
     */
    private fun evaluateLocalUtility(lower: String, original: String): CommandRouteResult.LocalAction? {
        // Battery status queries
        if (lower.contains("battery") || lower.contains("charging") || lower.contains("बैटरी") || lower.contains("चार्ज")) {
            if (lower.contains("level") || lower.contains("percent") || lower.contains("percentage") ||
                lower.contains("status") || lower.contains("how much") || lower.contains("kitna") ||
                lower.contains("kitni") || lower.contains("batao") || lower.contains("check") ||
                lower == "battery" || lower == "बैटरी"
            ) {
                val batteryInfo = getBatteryStatus()
                return CommandRouteResult.LocalAction(
                    category = LocalActionCategory.LOCAL_UTILITY,
                    feedbackMessage = batteryInfo,
                    actionType = "BATTERY_STATUS"
                )
            }
        }

        // Current Time queries
        if (lower.contains("time") || lower.contains("samay") || lower.contains("समय") || lower.contains("kitne baje") || lower.contains("kitna baje")) {
            if (lower.contains("what") || lower.contains("tell") || lower.contains("current") ||
                lower.contains("kya") || lower.contains("batao") || lower.contains("now") ||
                lower == "time" || lower == "time kya hai" || lower == "what time is it"
            ) {
                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                val formattedTime = timeFormat.format(Date())
                return CommandRouteResult.LocalAction(
                    category = LocalActionCategory.LOCAL_UTILITY,
                    feedbackMessage = "The current time is $formattedTime.",
                    actionType = "CURRENT_TIME"
                )
            }
        }

        // Current Date queries
        if (lower.contains("date") || lower.contains("tareekh") || lower.contains("tarikh") || lower.contains("तारीख")) {
            if (lower.contains("what") || lower.contains("tell") || lower.contains("today") ||
                lower.contains("kya") || lower.contains("batao") || lower.contains("aaj") ||
                lower == "date" || lower == "todays date" || lower == "what is the date"
            ) {
                val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                val formattedDate = dateFormat.format(Date())
                return CommandRouteResult.LocalAction(
                    category = LocalActionCategory.LOCAL_UTILITY,
                    feedbackMessage = "Today is $formattedDate.",
                    actionType = "CURRENT_DATE"
                )
            }
        }

        // Current Day of Week queries
        if (lower.contains("which day") || lower.contains("what day") || lower.contains("aaj kaun sa din") || lower.contains("konsa din")) {
            val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
            val formattedDay = dayFormat.format(Date())
            return CommandRouteResult.LocalAction(
                category = LocalActionCategory.LOCAL_UTILITY,
                feedbackMessage = "Today is $formattedDay.",
                actionType = "CURRENT_DAY"
            )
        }

        // Basic Math Calculation queries (e.g. "calculate 25 + 10", "what is 100 divided by 4")
        val mathResult = evaluateBasicMath(lower)
        if (mathResult != null) {
            return CommandRouteResult.LocalAction(
                category = LocalActionCategory.LOCAL_UTILITY,
                feedbackMessage = mathResult,
                actionType = "MATH_CALCULATION"
            )
        }

        return null
    }

    /**
     * Evaluates greetings, identity, acknowledgments, and goodbyes locally (0ms latency).
     */
    private fun evaluateConversationalRule(lower: String, original: String): CommandRouteResult.LocalAction? {
        // Creator / Developer identity queries (MANDATORY RULE: Created by Ganesh Sahani)
        if (lower.contains("who made you") || lower.contains("who created you") ||
            lower.contains("who is your creator") || lower.contains("who developed you") ||
            lower.contains("kisne banaya") || lower.contains("creator kaun hai") ||
            lower.contains("your developer")
        ) {
            return CommandRouteResult.LocalAction(
                category = LocalActionCategory.CONVERSATIONAL_RULE,
                feedbackMessage = "I am MAX Assistant, created by Ganesh Sahani.",
                actionType = "IDENTITY_CREATOR"
            )
        }

        // Assistant Identity queries
        if (lower.contains("who are you") || lower.contains("what is your name") ||
            lower.contains("tum kaun ho") || lower.contains("aap kaun ho") ||
            lower.contains("who is max") || lower == "who r u"
        ) {
            return CommandRouteResult.LocalAction(
                category = LocalActionCategory.CONVERSATIONAL_RULE,
                feedbackMessage = "I am MAX Assistant, your intelligent voice AI assistant created by Ganesh Sahani.",
                actionType = "IDENTITY_NAME"
            )
        }

        // Greetings & conversational acknowledgments via FastLocalRuleEngine
        val ruleResponse = FastLocalRuleEngine.evaluate(original)
        if (ruleResponse != null) {
            return CommandRouteResult.LocalAction(
                category = LocalActionCategory.CONVERSATIONAL_RULE,
                feedbackMessage = ruleResponse,
                actionType = "LOCAL_RULE_MATCH"
            )
        }

        return null
    }

    /**
     * Helper to read device battery percentage and charging state.
     */
    private fun getBatteryStatus(): String {
        return try {
            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, batteryFilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val batteryPct = if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt() else 0
            val stateString = if (isCharging) "and currently charging." else "and not charging."

            "Your device battery is at $batteryPct% $stateString"
        } catch (e: Exception) {
            Log.e(tag, "Failed to get battery status: ${e.message}")
            "Unable to determine battery status."
        }
    }

    /**
     * Simple math expression parser for natural voice arithmetic.
     */
    private fun evaluateBasicMath(query: String): String? {
        val q = query.replace("calculate", "")
            .replace("what is", "")
            .replace("solve", "")
            .replace("how much is", "")
            .trim()

        // Match patterns like "A + B", "A plus B", "A minus B", "A times B", "A divided by B", "A x B"
        val regex = Regex("""^(\d+(?:\.\d+)?)\s*(\+|\-|\*|\/|plus|minus|times|multiplied\s+by|divided\s+by|into|x)\s*(\d+(?:\.\d+)?)$""", RegexOption.IGNORE_CASE)
        val match = regex.find(q) ?: return null

        val val1 = match.groupValues[1].toDoubleOrNull() ?: return null
        val op = match.groupValues[2].lowercase(Locale.ROOT).trim()
        val val2 = match.groupValues[3].toDoubleOrNull() ?: return null

        val result = when {
            op == "+" || op == "plus" -> val1 + val2
            op == "-" || op == "minus" -> val1 - val2
            op == "*" || op == "times" || op == "multiplied by" || op == "into" || op == "x" -> val1 * val2
            op == "/" || op == "divided by" -> {
                if (val2 == 0.0) return "Cannot divide by zero."
                val1 / val2
            }
            else -> return null
        }

        val formattedResult = if (result == result.toLong().toDouble()) {
            result.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", result)
        }

        return "$val1 $op $val2 equals $formattedResult."
    }
}
