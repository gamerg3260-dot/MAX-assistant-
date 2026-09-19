package com.example.function

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
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
 * 
 * Features strict permission validation before execution with graceful fallbacks
 * and descriptive error metadata.
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

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isNotificationListenerEnabled(): Boolean {
        return WhatsAppControlManager.isNotificationListenerGranted(context)
    }

    private fun registerCoreFunctions() {
        // 1. Hardware Toggles & Settings
        register(
            DynamicFunction(
                name = "toggle_hardware_setting",
                description = "Controls mobile hardware toggles and system audio/screen settings (Flashlight, Wi-Fi, Bluetooth, Volume, Brightness, Sound Mode/DND).",
                category = FunctionCategory.HARDWARE_CONTROL,
                parameters = listOf(
                    FunctionParameter(
                        name = "target",
                        type = "string",
                        description = "Hardware component: 'flashlight', 'wifi', 'bluetooth', 'volume', 'brightness', 'sound_mode'",
                        allowedValues = listOf("flashlight", "wifi", "bluetooth", "volume", "brightness", "sound_mode")
                    ),
                    FunctionParameter(
                        name = "state",
                        type = "string",
                        description = "State or operation: 'on', 'off', 'toggle', 'up', 'down', 'silent', 'vibrate', 'normal'",
                        allowedValues = listOf("on", "off", "toggle", "up", "down", "silent", "vibrate", "normal"),
                        isRequired = false
                    ),
                    FunctionParameter(
                        name = "level",
                        type = "integer",
                        description = "Percentage level for volume or brightness (0-100)",
                        isRequired = false
                    )
                )
            ) { args ->
                val target = args["target"]?.toString()?.lowercase(Locale.ROOT) ?: "flashlight"
                val state = args["state"]?.toString()?.lowercase(Locale.ROOT) ?: "toggle"
                val level = (args["level"] as? Number)?.toInt()

                try {
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
                                resultSummary = msg,
                                error = if (res is ToggleResult.Error) res.message else null
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
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                                    return@DynamicFunction FunctionExecutionResult(
                                        isSuccess = false,
                                        resultSummary = "Bluetooth control requires Nearby Devices permission.",
                                        requiresPermission = true,
                                        missingPermission = Manifest.permission.BLUETOOTH_CONNECT,
                                        error = "Missing BLUETOOTH_CONNECT permission"
                                    )
                                }
                            }
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
                            if (!Settings.System.canWrite(context)) {
                                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                return@DynamicFunction FunctionExecutionResult(
                                    isSuccess = false,
                                    resultSummary = "Changing screen brightness requires 'Write System Settings' permission.",
                                    requiresPermission = true,
                                    missingPermission = "android.permission.WRITE_SETTINGS",
                                    resolutionIntent = intent,
                                    error = "Missing WRITE_SETTINGS permission"
                                )
                            }
                            val targetLevel = level ?: if (state in listOf("up", "increase")) 80 else 30
                            val res = deviceToggleManager.setScreenBrightness(targetLevel)
                            val msg = if (res is ToggleResult.Success) res.message else (res as ToggleResult.Error).message
                            FunctionExecutionResult(
                                isSuccess = res is ToggleResult.Success,
                                resultSummary = msg,
                                error = if (res is ToggleResult.Error) res.message else null
                            )
                        }
                        "sound_mode", "dnd", "silent" -> {
                            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                            val isDndGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                notificationManager?.isNotificationPolicyAccessGranted == true
                            } else true

                            val targetMode = when (state) {
                                "silent", "mute" -> SoundMode.SILENT
                                "vibrate" -> SoundMode.VIBRATE
                                else -> SoundMode.NORMAL
                            }
                            val res = deviceToggleManager.setSoundMode(targetMode)
                            val msg = if (res is ToggleResult.Success) res.message else (res as ToggleResult.Error).message
                            
                            val permRequired = !isDndGranted && targetMode == SoundMode.SILENT
                            val resolutionIntent = if (permRequired && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            } else null

                            FunctionExecutionResult(
                                isSuccess = res is ToggleResult.Success,
                                resultSummary = if (permRequired) "$msg (Do Not Disturb access recommended for complete silence)." else msg,
                                requiresPermission = permRequired,
                                missingPermission = if (permRequired) "android.permission.ACCESS_NOTIFICATION_POLICY" else null,
                                resolutionIntent = resolutionIntent
                            )
                        }
                        else -> FunctionExecutionResult(
                            isSuccess = false,
                            resultSummary = "Unknown hardware target: $target",
                            error = "Unsupported target"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error in toggle_hardware_setting: ${e.message}", e)
                    FunctionExecutionResult(
                        isSuccess = false,
                        resultSummary = "Error controlling $target: ${e.localizedMessage}",
                        error = e.message
                    )
                }
            }
        )

        // 2. WhatsApp & Notification Reading
        register(
            DynamicFunction(
                name = "check_whatsapp_messages",
                description = "Checks and reads recent incoming WhatsApp notifications and messages.",
                category = FunctionCategory.NOTIFICATIONS,
                parameters = listOf(
                    FunctionParameter(
                        name = "limit",
                        type = "integer",
                        description = "Number of recent WhatsApp messages to retrieve (default 3)",
                        isRequired = false
                    )
                )
            ) { args ->
                try {
                    if (!isNotificationListenerEnabled()) {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        return@DynamicFunction FunctionExecutionResult(
                            isSuccess = false,
                            resultSummary = "Checking WhatsApp messages requires Notification Access permission. Please grant permission in Settings.",
                            requiresPermission = true,
                            missingPermission = "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
                            resolutionIntent = intent,
                            error = "Missing Notification Access permission"
                        )
                    }

                    val limit = (args["limit"] as? Number)?.toInt() ?: 3
                    val msgs = whatsAppManager.messages.value.take(limit)

                    if (msgs.isEmpty()) {
                        FunctionExecutionResult(
                            isSuccess = true,
                            resultSummary = "No new WhatsApp messages found.",
                            data = mapOf("messageCount" to 0)
                        )
                    } else {
                        val summaryText = msgs.joinToString("; ") { "From ${it.sender}: ${it.text}" }
                        FunctionExecutionResult(
                            isSuccess = true,
                            resultSummary = "Found ${msgs.size} recent WhatsApp message${if (msgs.size > 1) "s" else ""}: $summaryText",
                            data = mapOf("messages" to msgs.map { mapOf("sender" to it.sender, "text" to it.text) })
                        )
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error checking WhatsApp messages: ${e.message}", e)
                    FunctionExecutionResult(
                        isSuccess = false,
                        resultSummary = "Failed to retrieve WhatsApp messages: ${e.localizedMessage}",
                        error = e.message
                    )
                }
            }
        )

        // 3. Application Launch & Deep-linking
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
                try {
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
                } catch (e: Exception) {
                    Log.e(tag, "Error launching application: ${e.message}", e)
                    FunctionExecutionResult(
                        isSuccess = false,
                        resultSummary = "Failed to launch app: ${e.localizedMessage}",
                        error = e.message
                    )
                }
            }
        )

        // 4. Direct Phone Call Initiation
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
                try {
                    val recipient = args["recipient"]?.toString() ?: ""
                    if (recipient.isBlank()) {
                        return@DynamicFunction FunctionExecutionResult(
                            isSuccess = false,
                            resultSummary = "No recipient provided for phone call.",
                            error = "Missing recipient"
                        )
                    }

                    if (!hasPermission(Manifest.permission.CALL_PHONE)) {
                        return@DynamicFunction FunctionExecutionResult(
                            isSuccess = false,
                            resultSummary = "Placing direct phone calls requires Phone Call permission.",
                            requiresPermission = true,
                            missingPermission = Manifest.permission.CALL_PHONE,
                            error = "Missing CALL_PHONE permission"
                        )
                    }

                    val res = directCallManager.processVoiceCallCommand("call $recipient")
                    if (res.isHandled && res.callIntent != null) {
                        context.startActivity(res.callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        FunctionExecutionResult(
                            isSuccess = true,
                            resultSummary = res.feedbackMessage
                        )
                    } else {
                        FunctionExecutionResult(
                            isSuccess = true,
                            resultSummary = res.feedbackMessage
                        )
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error placing phone call: ${e.message}", e)
                    FunctionExecutionResult(
                        isSuccess = false,
                        resultSummary = "Failed to place call: ${e.localizedMessage}",
                        error = e.message
                    )
                }
            }
        )

        // 5. SMS Message Dispatch
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
                try {
                    val destination = args["destinationNumber"]?.toString() ?: ""
                    val text = args["messageText"]?.toString() ?: ""
                    if (destination.isBlank() || text.isBlank()) {
                        return@DynamicFunction FunctionExecutionResult(
                            isSuccess = false,
                            resultSummary = "Destination number and message text cannot be empty.",
                            error = "Invalid arguments"
                        )
                    }

                    if (!hasPermission(Manifest.permission.SEND_SMS)) {
                        return@DynamicFunction FunctionExecutionResult(
                            isSuccess = false,
                            resultSummary = "Sending SMS messages requires SMS permission.",
                            requiresPermission = true,
                            missingPermission = Manifest.permission.SEND_SMS,
                            error = "Missing SEND_SMS permission"
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
                } catch (e: Exception) {
                    Log.e(tag, "Error sending SMS: ${e.message}", e)
                    FunctionExecutionResult(
                        isSuccess = false,
                        resultSummary = "Failed to send SMS: ${e.localizedMessage}",
                        error = e.message
                    )
                }
            }
        )

        // 6. Emergency SOS & Location Broadcast
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
                try {
                    val res = emergencySosManager.processVoiceSosCommand("send emergency sos help")
                    FunctionExecutionResult(
                        isSuccess = res.isHandled,
                        resultSummary = res.feedbackMessage
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Error triggering SOS: ${e.message}", e)
                    FunctionExecutionResult(
                        isSuccess = false,
                        resultSummary = "Failed to trigger SOS: ${e.localizedMessage}",
                        error = e.message
                    )
                }
            }
        )

        // 7. Camera & Hands-Free Capture
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
                try {
                    if (!hasPermission(Manifest.permission.CAMERA)) {
                        return@DynamicFunction FunctionExecutionResult(
                            isSuccess = false,
                            resultSummary = "Camera control requires Camera permission.",
                            requiresPermission = true,
                            missingPermission = Manifest.permission.CAMERA,
                            error = "Missing CAMERA permission"
                        )
                    }

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
                } catch (e: Exception) {
                    Log.e(tag, "Error controlling camera: ${e.message}", e)
                    FunctionExecutionResult(
                        isSuccess = false,
                        resultSummary = "Camera error: ${e.localizedMessage}",
                        error = e.message
                    )
                }
            }
        )

        // 8. Accessibility UI Navigation & Auto-Scroll
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
                try {
                    val action = args["action"]?.toString() ?: "scroll_down"
                    val res = MaxAccessibilityService.processVoiceAccessibilityCommand(action.replace("_", " "))
                    FunctionExecutionResult(
                        isSuccess = res.isHandled,
                        resultSummary = res.feedbackMessage
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Error executing accessibility navigation: ${e.message}", e)
                    FunctionExecutionResult(
                        isSuccess = false,
                        resultSummary = "Accessibility navigation error: ${e.localizedMessage}",
                        error = e.message
                    )
                }
            }
        )

        // 9. Device Status Query (Battery, Time, Date)
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
                try {
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
                } catch (e: Exception) {
                    Log.e(tag, "Error querying device status: ${e.message}", e)
                    FunctionExecutionResult(
                        isSuccess = false,
                        resultSummary = "Error reading device status: ${e.localizedMessage}",
                        error = e.message
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
