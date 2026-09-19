package com.example.function

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import com.example.accessibility.MaxAccessibilityService
import com.example.camera.MaxCameraManager
import com.example.launcher.AppLauncherManager
import com.example.sos.EmergencySosManager
import com.example.telephony.DirectCallManager
import com.example.telephony.SmsSender
import com.example.toggle.DeviceToggleManager
import com.example.toggle.SoundMode
import com.example.toggle.ToggleResult
import com.example.whatsapp.WhatsAppControlManager
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Extensible Dynamic Function Registry for MAX Assistant.
 * Exposes device, telephony, application, camera, and system capabilities as
 * dynamically callable functions and formats them for Gemini tool calling.
 */
class DynamicFunctionRegistry(
    private val context: Context,
    private val directCallManager: DirectCallManager,
    private val appLauncherManager: AppLauncherManager,
    private val deviceToggleManager: DeviceToggleManager,
    private val emergencySosManager: EmergencySosManager,
    private val maxCameraManager: MaxCameraManager,
    private val smsSender: SmsSender,
    private val whatsAppManager: WhatsAppControlManager
) {
    private val tag = "DynamicFunctionRegistry"
    private val functionsMap = mutableMapOf<String, DynamicFunction>()

    init {
        registerCoreFunctions()
    }

    private fun registerCoreFunctions() {
        // 1. Hardware Toggle & Settings Control
        register(
            DynamicFunction(
                name = "toggle_hardware_setting",
                description = "Controls device hardware settings like Flashlight/Torch, Wi-Fi, Bluetooth, Sound Mode, Volume, Screen Brightness, or Hotspot.",
                category = FunctionCategory.HARDWARE_CONTROL,
                parameters = listOf(
                    FunctionParameter(
                        name = "target",
                        type = "string",
                        description = "Hardware feature to control: 'flashlight', 'wifi', 'bluetooth', 'volume', 'brightness', 'sound_mode', 'dnd', or 'hotspot'",
                        allowedValues = listOf("flashlight", "wifi", "bluetooth", "volume", "brightness", "sound_mode", "dnd", "hotspot")
                    ),
                    FunctionParameter(
                        name = "state",
                        type = "string",
                        description = "Desired state: 'on', 'off', 'toggle', 'up', 'down', 'set'",
                        isRequired = false
                    ),
                    FunctionParameter(
                        name = "level",
                        type = "integer",
                        description = "Target percentage or level (0-100) for volume or brightness",
                        isRequired = false
                    )
                )
            ) { args ->
                val target = args["target"]?.toString()?.lowercase(Locale.ROOT) ?: "flashlight"
                val state = args["state"]?.toString()?.lowercase(Locale.ROOT) ?: "toggle"
                val level = (args["level"] as? Number)?.toInt()

                when (target) {
                    "flashlight", "torch" -> {
                        val turnOn = when (state) {
                            "on", "enable" -> true
                            "off", "disable" -> false
                            else -> !deviceToggleManager.isFlashlightOn.value
                        }
                        val res = deviceToggleManager.setFlashlightEnabled(turnOn)
                        val msg = if (res is ToggleResult.Success) res.message else (res as ToggleResult.Error).message
                        FunctionExecutionResult(
                            isSuccess = res is ToggleResult.Success,
                            resultSummary = msg
                        )
                    }
                    "wifi", "wi-fi" -> {
                        val turnOn = when (state) {
                            "on", "enable" -> true
                            "off", "disable" -> false
                            else -> !deviceToggleManager.isWifiEnabled.value
                        }
                        val res = deviceToggleManager.setWifiEnabled(turnOn)
                        val msg = if (res is ToggleResult.Success) res.message else (res as ToggleResult.Error).message
                        FunctionExecutionResult(
                            isSuccess = res is ToggleResult.Success,
                            resultSummary = msg
                        )
                    }
                    "bluetooth" -> {
                        val voiceCmd = if (state in listOf("off", "disable")) "turn off bluetooth" else "turn on bluetooth"
                        val res = deviceToggleManager.processVoiceToggleCommand(voiceCmd)
                        FunctionExecutionResult(
                            isSuccess = res.isHandled,
                            resultSummary = res.feedbackMessage
                        )
                    }
                    "volume" -> {
                        val voiceCmd = if (level != null) "set volume to $level percent" else if (state in listOf("up", "increase")) "increase volume" else "decrease volume"
                        val res = deviceToggleManager.processVoiceToggleCommand(voiceCmd)
                        FunctionExecutionResult(
                            isSuccess = res.isHandled,
                            resultSummary = res.feedbackMessage
                        )
                    }
                    "brightness" -> {
                        val targetLevel = level ?: if (state in listOf("up", "increase")) 80 else 30
                        val res = deviceToggleManager.setScreenBrightness(targetLevel)
                        val msg = if (res is ToggleResult.Success) res.message else (res as ToggleResult.Error).message
                        FunctionExecutionResult(
                            isSuccess = res is ToggleResult.Success,
                            resultSummary = msg
                        )
                    }
                    "sound_mode", "dnd", "silent" -> {
                        val targetMode = when (state) {
                            "silent", "mute" -> SoundMode.SILENT
                            "vibrate" -> SoundMode.VIBRATE
                            else -> SoundMode.NORMAL
                        }
                        val res = deviceToggleManager.setSoundMode(targetMode)
                        val msg = if (res is ToggleResult.Success) res.message else (res as ToggleResult.Error).message
                        FunctionExecutionResult(
                            isSuccess = res is ToggleResult.Success,
                            resultSummary = msg
                        )
                    }
                    else -> FunctionExecutionResult(
                        isSuccess = false,
                        resultSummary = "Unknown hardware target: $target",
                        error = "Unsupported target"
                    )
                }
            }
        )

        // 2. Application Launch & Deep-linking
        register(
            DynamicFunction(
                name = "launch_application",
                description = "Launches an installed mobile application by name or opens relevant system screen (e.g., YouTube, WhatsApp, Settings, Maps, Camera, Calculator).",
                category = FunctionCategory.APPLICATION_LAUNCH,
                parameters = listOf(
                    FunctionParameter(
                        name = "appName",
                        type = "string",
                        description = "Name of the target app to launch (e.g., 'YouTube', 'WhatsApp', 'Camera', 'Settings', 'Calculator', 'Maps', 'Chrome')"
                    )
                )
            ) { args ->
                val appName = args["appName"]?.toString() ?: "Settings"
                val res = appLauncherManager.processVoiceAppLaunchCommand("open $appName")
                if (res.isHandled && res.launchIntent != null) {
                    appLauncherManager.launchIntentNow(res.launchIntent)
                    FunctionExecutionResult(
                        isSuccess = true,
                        resultSummary = "Opening $appName."
                    )
                } else {
                    FunctionExecutionResult(
                        isSuccess = false,
                        resultSummary = "Could not find or launch application: $appName",
                        error = "App not found"
                    )
                }
            }
        )

        // 3. Direct Phone Call Initiation
        register(
            DynamicFunction(
                name = "make_phone_call",
                description = "Initiates a direct phone call or dials a contact or phone number.",
                category = FunctionCategory.TELEPHONY_CALL,
                parameters = listOf(
                    FunctionParameter(
                        name = "recipient",
                        type = "string",
                        description = "Contact name or digits of the phone number to call (e.g. 'Mom', 'Dad', '9876543210')"
                    )
                )
            ) { args ->
                val recipient = args["recipient"]?.toString() ?: ""
                if (recipient.isBlank()) {
                    return@DynamicFunction FunctionExecutionResult(
                        isSuccess = false,
                        resultSummary = "No recipient provided for phone call.",
                        error = "Missing recipient"
                    )
                }
                val res = directCallManager.processVoiceCallCommand("call $recipient")
                if (res.isHandled && res.callIntent != null) {
                    try {
                        context.startActivity(res.callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        FunctionExecutionResult(
                            isSuccess = true,
                            resultSummary = res.feedbackMessage
                        )
                    } catch (e: Exception) {
                        FunctionExecutionResult(
                            isSuccess = false,
                            resultSummary = "Failed to launch phone dialer: ${e.message}",
                            error = e.message
                        )
                    }
                } else {
                    FunctionExecutionResult(
                        isSuccess = true,
                        resultSummary = res.feedbackMessage
                    )
                }
            }
        )

        // 4. SMS Message Dispatch
        register(
            DynamicFunction(
                name = "send_text_message",
                description = "Sends an SMS message to a specified contact number or name.",
                category = FunctionCategory.MESSAGING,
                parameters = listOf(
                    FunctionParameter(
                        name = "destinationNumber",
                        type = "string",
                        description = "Phone number of the recipient"
                    ),
                    FunctionParameter(
                        name = "messageText",
                        type = "string",
                        description = "Content text of the SMS message"
                    )
                )
            ) { args ->
                val destination = args["destinationNumber"]?.toString() ?: ""
                val text = args["messageText"]?.toString() ?: ""
                if (destination.isBlank() || text.isBlank()) {
                    return@DynamicFunction FunctionExecutionResult(
                        isSuccess = false,
                        resultSummary = "Destination number and message text cannot be empty.",
                        error = "Invalid arguments"
                    )
                }
                val result = smsSender.sendSms(
                    destinationNumber = destination,
                    messageText = text,
                    cooldownMinutes = 0,
                    bypassCooldown = true
                )
                if (result is com.example.telephony.SendSmsResult.Success) {
                    FunctionExecutionResult(
                        isSuccess = true,
                        resultSummary = "SMS message sent to $destination."
                    )
                } else {
                    FunctionExecutionResult(
                        isSuccess = false,
                        resultSummary = "Failed to dispatch SMS: ${(result as? com.example.telephony.SendSmsResult.Failure)?.reason}",
                        error = "SMS dispatch failure"
                    )
                }
            }
        )

        // 5. Emergency SOS & Location Broadcast
        register(
            DynamicFunction(
                name = "trigger_emergency_sos",
                description = "Triggers the emergency SOS protocol, fetches current GPS coordinates, and alerts designated emergency contacts.",
                category = FunctionCategory.HARDWARE_CONTROL,
                parameters = listOf(
                    FunctionParameter(
                        name = "sendGpsLocation",
                        type = "boolean",
                        description = "Whether to broadcast live GPS coordinates in the emergency message",
                        isRequired = false
                    )
                )
            ) { args ->
                val res = emergencySosManager.processVoiceSosCommand("send emergency sos help")
                FunctionExecutionResult(
                    isSuccess = res.isHandled,
                    resultSummary = res.feedbackMessage
                )
            }
        )

        // 6. Camera & Hands-Free Capture
        register(
            DynamicFunction(
                name = "control_camera",
                description = "Captures photos, selfies, or starts/stops video recording hands-free.",
                category = FunctionCategory.CAMERA_CAPTURE,
                parameters = listOf(
                    FunctionParameter(
                        name = "action",
                        type = "string",
                        description = "Camera action: 'take_photo', 'take_selfie', 'switch_camera', 'start_video', 'stop_video'",
                        allowedValues = listOf("take_photo", "take_selfie", "switch_camera", "start_video", "stop_video")
                    )
                )
            ) { args ->
                val action = args["action"]?.toString() ?: "take_photo"
                val voiceCommand = when (action) {
                    "take_selfie" -> "take a selfie"
                    "switch_camera" -> "switch camera"
                    "start_video" -> "start recording video"
                    "stop_video" -> "stop recording video"
                    else -> "take a photo"
                }
                val res = maxCameraManager.processVoiceCameraCommand(voiceCommand)
                FunctionExecutionResult(
                    isSuccess = res.isHandled,
                    resultSummary = res.feedbackMessage
                )
            }
        )

        // 7. Accessibility UI Navigation & Auto-Scroll
        register(
            DynamicFunction(
                name = "navigate_accessibility",
                description = "Performs global navigation or automated scrolling (scroll up, scroll down, go home, go back).",
                category = FunctionCategory.ACCESSIBILITY_NAV,
                parameters = listOf(
                    FunctionParameter(
                        name = "action",
                        type = "string",
                        description = "Navigation command: 'scroll_down', 'scroll_up', 'go_home', 'go_back'",
                        allowedValues = listOf("scroll_down", "scroll_up", "go_home", "go_back")
                    )
                )
            ) { args ->
                val action = args["action"]?.toString() ?: "scroll_down"
                val res = MaxAccessibilityService.processVoiceAccessibilityCommand(action.replace("_", " "))
                FunctionExecutionResult(
                    isSuccess = res.isHandled,
                    resultSummary = res.feedbackMessage
                )
            }
        )

        // 8. Device Status Query (Battery, Time, Date)
        register(
            DynamicFunction(
                name = "query_device_status",
                description = "Queries real-time device system info including battery level, charging status, time, and date.",
                category = FunctionCategory.DEVICE_UTILITY,
                parameters = listOf(
                    FunctionParameter(
                        name = "queryType",
                        type = "string",
                        description = "Type of query: 'battery', 'time', 'date', 'network'",
                        allowedValues = listOf("battery", "time", "date", "network")
                    )
                )
            ) { args ->
                val queryType = args["queryType"]?.toString() ?: "battery"
                when (queryType) {
                    "battery" -> {
                        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                        val isCharging = (bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) == BatteryManager.BATTERY_STATUS_CHARGING)
                        val status = if (isCharging) "charging" else "discharging"
                        FunctionExecutionResult(
                            isSuccess = true,
                            resultSummary = "Battery level is currently $level% ($status)."
                        )
                    }
                    "time" -> {
                        val formatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                        FunctionExecutionResult(
                            isSuccess = true,
                            resultSummary = "The current time is $formatted."
                        )
                    }
                    "date" -> {
                        val formatted = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
                        FunctionExecutionResult(
                            isSuccess = true,
                            resultSummary = "Today is $formatted."
                        )
                    }
                    else -> FunctionExecutionResult(
                        isSuccess = true,
                        resultSummary = "Device system operational."
                    )
                }
            }
        )
    }

    fun register(function: DynamicFunction) {
        functionsMap[function.name] = function
        Log.d(tag, "Registered dynamic function: ${function.name} (${function.category})")
    }

    fun getFunction(name: String): DynamicFunction? = functionsMap[name]

    fun getAllFunctions(): List<DynamicFunction> = functionsMap.values.toList()

    /**
     * Converts all registered functions into the JSON format expected by Gemini Tools / Function Declarations.
     */
    fun toGeminiToolsJson(): JSONArray {
        val toolsArray = JSONArray()
        val functionDeclarations = JSONArray()

        for (func in functionsMap.values) {
            val funcObj = JSONObject().apply {
                put("name", func.name)
                put("description", func.description)

                val propertiesObj = JSONObject()
                val requiredArray = JSONArray()

                for (param in func.parameters) {
                    val paramObj = JSONObject().apply {
                        put("type", param.type)
                        put("description", param.description)
                        if (param.allowedValues != null) {
                            val enumArray = JSONArray()
                            param.allowedValues.forEach { enumArray.put(it) }
                            put("enum", enumArray)
                        }
                    }
                    propertiesObj.put(param.name, paramObj)
                    if (param.isRequired) {
                        requiredArray.put(param.name)
                    }
                }

                val parametersSchema = JSONObject().apply {
                    put("type", "object")
                    put("properties", propertiesObj)
                    if (requiredArray.length() > 0) {
                        put("required", requiredArray)
                    }
                }
                put("parameters", parametersSchema)
            }
            functionDeclarations.put(funcObj)
        }

        val containerObj = JSONObject().apply {
            put("function_declarations", functionDeclarations)
        }
        toolsArray.put(containerObj)
        return toolsArray
    }
}
