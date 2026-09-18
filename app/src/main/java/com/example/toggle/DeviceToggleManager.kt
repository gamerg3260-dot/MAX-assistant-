package com.example.toggle

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class SoundMode {
    NORMAL,
    VIBRATE,
    SILENT
}

sealed class ToggleResult {
    data class Success(val message: String) : ToggleResult()
    data class Error(val message: String, val requiresPermissionIntent: Intent? = null) : ToggleResult()
}

data class VoiceToggleResult(
    val isHandled: Boolean,
    val feedbackMessage: String,
    val actionTaken: String? = null
)

/**
 * Manager for Mobile Quick Settings Controls (Wi-Fi, Sound Modes, Flashlight)
 * and System Screen Brightness level adjustment.
 */
class DeviceToggleManager(private val context: Context) {
    private val tag = "DeviceToggleManager"

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    // Reactive States
    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    private val _soundMode = MutableStateFlow(getCurrentSoundMode())
    val soundMode: StateFlow<SoundMode> = _soundMode.asStateFlow()

    private val _isWifiEnabled = MutableStateFlow(getInitialWifiState())
    val isWifiEnabled: StateFlow<Boolean> = _isWifiEnabled.asStateFlow()

    private val _brightnessPercent = MutableStateFlow(getInitialBrightnessPercent())
    val brightnessPercent: StateFlow<Int> = _brightnessPercent.asStateFlow()

    init {
        registerTorchCallback()
    }

    private fun registerTorchCallback() {
        try {
            cameraManager?.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    super.onTorchModeChanged(cameraId, enabled)
                    _isFlashlightOn.value = enabled
                }
            }, null)
        } catch (e: Exception) {
            Log.e(tag, "Failed to register torch callback: ${e.message}")
        }
    }

    private fun getInitialWifiState(): Boolean {
        return try {
            wifiManager?.isWifiEnabled ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun getCurrentSoundMode(): SoundMode {
        return when (audioManager?.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> SoundMode.SILENT
            AudioManager.RINGER_MODE_VIBRATE -> SoundMode.VIBRATE
            else -> SoundMode.NORMAL
        }
    }

    private fun getInitialBrightnessPercent(): Int {
        return try {
            val brightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            ((brightness / 255f) * 100).toInt().coerceIn(0, 100)
        } catch (e: Exception) {
            50
        }
    }

    /**
     * Toggles device Flashlight / Torch ON or OFF.
     */
    fun setFlashlightEnabled(enabled: Boolean): ToggleResult {
        return try {
            val cameraId = cameraManager?.cameraIdList?.firstOrNull()
            if (cameraId == null) {
                return ToggleResult.Error("Device camera or flashlight hardware is unavailable.")
            }
            cameraManager?.setTorchMode(cameraId, enabled)
            _isFlashlightOn.value = enabled
            val msg = if (enabled) "Flashlight turned ON" else "Flashlight turned OFF"
            Log.i(tag, msg)
            ToggleResult.Success(msg)
        } catch (e: Exception) {
            Log.e(tag, "Error setting flashlight: ${e.message}", e)
            ToggleResult.Error("Failed to set flashlight: ${e.localizedMessage}")
        }
    }

    /**
     * Toggles device Sound Mode: Normal, Vibrate, or Silent using AudioManager.
     * Ensures Silent mode sets ringerMode to SILENT without triggering Do Not Disturb or Night Mode.
     */
    fun setSoundMode(mode: SoundMode): ToggleResult {
        if (audioManager == null) return ToggleResult.Error("AudioManager is unavailable.")

        return try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            val isDndGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notificationManager?.isNotificationPolicyAccessGranted == true
            } else true

            if ((mode == SoundMode.SILENT || mode == SoundMode.VIBRATE) && !isDndGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                return ToggleResult.Error(
                    message = "Changing sound modes requires Do Not Disturb policy access in settings.",
                    requiresPermissionIntent = intent
                )
            }

            when (mode) {
                SoundMode.NORMAL -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                }
                SoundMode.VIBRATE -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                }
                SoundMode.SILENT -> {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                }
            }
            _soundMode.value = mode
            val msg = "Sound mode set to ${mode.name.lowercase().replaceFirstChar { it.uppercase() }}"
            Log.i(tag, msg)
            ToggleResult.Success(msg)
        } catch (e: Exception) {
            Log.e(tag, "Error setting sound mode: ${e.message}", e)
            ToggleResult.Error("Failed to change sound mode: ${e.localizedMessage}")
        }
    }

    /**
     * Toggles Wi-Fi state using WifiManager or launches Wi-Fi Control Panel Intent (Android 10+).
     */
    fun setWifiEnabled(enabled: Boolean): ToggleResult {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ restricts direct programmatic Wi-Fi toggling for apps. Launch Wi-Fi Control Panel Intent.
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(panelIntent)
                    _isWifiEnabled.value = enabled
                    ToggleResult.Success("Opening floating Wi-Fi panel to toggle Wi-Fi.")
                } catch (panelEx: Exception) {
                    val fallbackIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                    _isWifiEnabled.value = enabled
                    ToggleResult.Success("Opening Wi-Fi settings to toggle Wi-Fi.")
                }
            } else {
                @Suppress("DEPRECATION")
                val success = wifiManager?.setWifiEnabled(enabled) ?: false
                if (success) {
                    _isWifiEnabled.value = enabled
                    val msg = if (enabled) "Wi-Fi turned ON" else "Wi-Fi turned OFF"
                    ToggleResult.Success(msg)
                } else {
                    ToggleResult.Error("Unable to toggle Wi-Fi directly.")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error setting Wi-Fi: ${e.message}", e)
            ToggleResult.Error("Failed to set Wi-Fi: ${e.localizedMessage}")
        }
    }

    /**
     * Sets screen brightness percentage (0% to 100%).
     */
    fun setScreenBrightness(percent: Int): ToggleResult {
        val targetPercent = percent.coerceIn(5, 100)
        val canWrite = Settings.System.canWrite(context)

        if (!canWrite) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return ToggleResult.Error(
                message = "Changing screen brightness requires 'Write System Settings' permission.",
                requiresPermissionIntent = intent
            )
        }

        return try {
            val brightnessValue = ((targetPercent / 100f) * 255).toInt().coerceIn(10, 255)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightnessValue
            )
            _brightnessPercent.value = targetPercent
            val msg = "Screen brightness set to $targetPercent%"
            Log.i(tag, msg)
            ToggleResult.Success(msg)
        } catch (e: Exception) {
            Log.e(tag, "Error setting screen brightness: ${e.message}", e)
            ToggleResult.Error("Failed to change screen brightness: ${e.localizedMessage}")
        }
    }

    fun canWriteSystemSettings(): Boolean {
        return Settings.System.canWrite(context)
    }

    fun openWriteSettingsPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Evaluates natural language spoken commands for Quick Settings & Brightness controls.
     */
    fun processVoiceToggleCommand(query: String): VoiceToggleResult {
        val q = query.lowercase(Locale.ROOT).trim()

        // 1. Flashlight / Torch Commands
        if (q.contains("flashlight") || q.contains("torch") || q.contains("light") || q.contains("फ़्लैशलाइट") || q.contains("टॉर्च")) {
            if (q.contains("on") || q.contains("enable") || q.contains("turn on") || q.contains("start") || q.contains("ऑन") || q.contains("चालू")) {
                val res = setFlashlightEnabled(true)
                val msg = if (res is ToggleResult.Success) res.message else (res as ToggleResult.Error).message
                return VoiceToggleResult(true, msg, "FLASHLIGHT_ON")
            } else if (q.contains("off") || q.contains("disable") || q.contains("turn off") || q.contains("stop") || q.contains("बंद")) {
                val res = setFlashlightEnabled(false)
                val msg = if (res is ToggleResult.Success) res.message else (res as ToggleResult.Error).message
                return VoiceToggleResult(true, msg, "FLASHLIGHT_OFF")
            } else if (q.contains("toggle")) {
                val newState = !_isFlashlightOn.value
                val res = setFlashlightEnabled(newState)
                val msg = if (res is ToggleResult.Success) res.message else (res as ToggleResult.Error).message
                return VoiceToggleResult(true, msg, "FLASHLIGHT_TOGGLE")
            }
        }

        // 2. Wi-Fi Commands
        if (q.contains("wifi") || q.contains("wi-fi") || q.contains("वाईफाई")) {
            if (q.contains("on") || q.contains("enable") || q.contains("turn on") || q.contains("ऑन") || q.contains("चालू")) {
                val res = setWifiEnabled(true)
                val msg = if (res is ToggleResult.Success) res.message else (res as ToggleResult.Error).message
                return VoiceToggleResult(true, msg, "WIFI_ON")
            } else if (q.contains("off") || q.contains("disable") || q.contains("turn off") || q.contains("बंद")) {
                val res = setWifiEnabled(false)
                val msg = if (res is ToggleResult.Success) res.message else (res as ToggleResult.Error).message
                return VoiceToggleResult(true, msg, "WIFI_OFF")
            }
        }

        // 3. Sound Mode Commands (Silent, Vibrate, Ring/Normal)
        if (q.contains("silent") || q.contains("vibrate") || q.contains("ringer") || q.contains("sound") ||
            q.contains("mute") || q.contains("unmute") || q.contains("साइलेंट") || q.contains("वाइब्रेट") || q.contains("आवाज")) {

            if (q.contains("silent") || q.contains("mute") || q.contains("साइलेंट")) {
                val res = setSoundMode(SoundMode.SILENT)
                val msg = if (res is ToggleResult.Success) res.message else (res as ToggleResult.Error).message
                return VoiceToggleResult(true, msg, "SOUND_SILENT")
            } else if (q.contains("vibrate") || q.contains("वाइब्रेट")) {
                val res = setSoundMode(SoundMode.VIBRATE)
                val msg = if (res is ToggleResult.Success) res.message else (res as ToggleResult.Error).message
                return VoiceToggleResult(true, msg, "SOUND_VIBRATE")
            } else if (q.contains("ring") || q.contains("normal") || q.contains("unmute") || q.contains("sound on") || q.contains("चालू")) {
                val res = setSoundMode(SoundMode.NORMAL)
                val msg = if (res is ToggleResult.Success) res.message else (res as ToggleResult.Error).message
                return VoiceToggleResult(true, msg, "SOUND_NORMAL")
            }
        }

        // 4. Screen Brightness Commands
        if (q.contains("brightness") || q.contains("dim") || q.contains("bright") || q.contains("ब्राइटनेस")) {
            // Check for explicit percentages like "50%", "80", "100"
            val digits = Regex("\\d+").find(q)?.value?.toIntOrNull()
            if (digits != null && digits in 0..100) {
                val res = setScreenBrightness(digits)
                val msg = if (res is ToggleResult.Success) res.message else (res as ToggleResult.Error).message
                return VoiceToggleResult(true, msg, "BRIGHTNESS_SET")
            } else if (q.contains("max") || q.contains("full") || q.contains("high") || q.contains("100")) {
                val res = setScreenBrightness(100)
                val msg = if (res is ToggleResult.Success) res.message else (res as ToggleResult.Error).message
                return VoiceToggleResult(true, msg, "BRIGHTNESS_MAX")
            } else if (q.contains("dim") || q.contains("low") || q.contains("min")) {
                val res = setScreenBrightness(15)
                val msg = if (res is ToggleResult.Success) res.message else (res as ToggleResult.Error).message
                return VoiceToggleResult(true, msg, "BRIGHTNESS_LOW")
            } else if (q.contains("increase") || q.contains("more") || q.contains("up")) {
                val current = _brightnessPercent.value
                val res = setScreenBrightness((current + 25).coerceAtMost(100))
                val msg = if (res is ToggleResult.Success) res.message else (res as ToggleResult.Error).message
                return VoiceToggleResult(true, msg, "BRIGHTNESS_INCREASE")
            } else if (q.contains("decrease") || q.contains("less") || q.contains("down")) {
                val current = _brightnessPercent.value
                val res = setScreenBrightness((current - 25).coerceAtLeast(10))
                val msg = if (res is ToggleResult.Success) res.message else (res as ToggleResult.Error).message
                return VoiceToggleResult(true, msg, "BRIGHTNESS_DECREASE")
            }
        }

        return VoiceToggleResult(false, "Command not recognized as a mobile hardware toggle.")
    }
}
