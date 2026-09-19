package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    // MAX Assistant Core Switches
    val isMaxAssistantEnabled: Boolean = true,
    val isCallAnnouncerEnabled: Boolean = true,
    val isVoiceCallControlEnabled: Boolean = true,

    // Caller Announcement Voice Settings
    val announcementTemplate: String = "Incoming call from {name}",
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val announcementRepeatCount: Int = 2,
    val announceUnknownNumbers: Boolean = true,

    // Voice Command Controls
    val acceptKeywords: String = "accept, receive, answer, yes, pickup",
    val rejectKeywords: String = "reject, decline, disconnect, no, ignore, hang up",
    val silenceKeywords: String = "silence, mute, quiet, stop",
    val autoSpeakerphoneOnAccept: Boolean = true,
    val autoListenTimeoutSeconds: Int = 15,

    // Blocklist & Spam Management Settings
    val isBlocklistEnabled: Boolean = true,
    val autoRejectSpam: Boolean = true,
    val blockUnknownNumbers: Boolean = false,
    val blockedNumbers: Set<String> = setOf("+1 800-555-0199", "+1 888-555-4321"),

    // Hardware & System Control Settings
    val isBluetoothVoiceControl: Boolean = true,
    val isFlashlightAlerts: Boolean = false,
    val isHapticFeedbackEnabled: Boolean = true,
    val isProximitySensorSilence: Boolean = true,

    // App & Media Control Settings (YouTube, WhatsApp)
    val isWhatsAppAutoRead: Boolean = true,
    val isWhatsAppAutoReplyEnabled: Boolean = true,
    val isYouTubeMediaAutoPause: Boolean = true,
    val isSpotifyAutoDucking: Boolean = true,

    // Theme Customization Option
    val themePreset: String = "Siri Spectrum",

    // Voice AI Dedicated Settings
    val isDefaultVoiceAssistant: Boolean = true,
    val voiceLanguage: String = "Hindi (India)",
    val voiceResponseStyle: String = "Conversational",
    val isBargeInEnabled: Boolean = true,
    val bargeInSensitivity: Float = 0.75f,
    val bargeInMode: String = "HYBRID_VAD_STT",
    val isRealtimeWebSocketEnabled: Boolean = true,
    val bargeInSoundFeedback: Boolean = true,

    // Hardware & Audio I/O Settings
    val microphoneSource: String = "Built-in Mic (Auto)",
    val speakerOutput: String = "Auto (Speakerphone)",
    val inputSensitivity: Float = 0.75f,
    val equalizerPreset: String = "Voice Clarity",
    val bassBoost: Float = 0.4f,
    val trebleBoost: Float = 0.6f,

    // Security, Access Control & Theft Defense Settings
    val isVoiceIdEnabled: Boolean = true,
    val isPinRequiredForActions: Boolean = false,
    val securityPin: String = "1234",
    val isAntiTheftSirenEnabled: Boolean = true,
    val isIntruderSelfieCaptureEnabled: Boolean = true,
    val isMotionDetectionAlarmEnabled: Boolean = false,
    val failedUnlockThreshold: Int = 2,
    val isSosLocationBroadcast: Boolean = true,
    val isSosSmsBroadcast: Boolean = true,
    val isSosSirensBroadcast: Boolean = true,
    val sosAlertMessage: String = "EMERGENCY: I need immediate help. My current GPS location is being broadcast via MAX Assistant.",

    // Auto-Responder & AI Settings (retained for comprehensive call management)
    val isAutoResponderEnabled: Boolean = true,
    val isSmsAutoReplyEnabled: Boolean = true,
    val isCallAutoReplyEnabled: Boolean = true,
    val selectedPersona: String = "In a Meeting",
    val customInstructions: String = "I am currently tied up in an important meeting and unable to answer calls or reply immediately. I will get back to you as soon as I am free.",
    val responseTone: String = "Professional",
    val activeAiProvider: String = "gemini",
    val modelName: String = "gemini-3.5-flash",
    val antiSpamCooldownMinutes: Int = 3
)

class AppSettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val defaultBlocked = setOf("+1 800-555-0199", "+1 888-555-4321")
        val loadedBlocked = prefs.getStringSet("blocked_numbers", defaultBlocked) ?: defaultBlocked

        return AppSettings(
            isMaxAssistantEnabled = prefs.getBoolean("is_max_assistant_enabled", true),
            isCallAnnouncerEnabled = prefs.getBoolean("is_call_announcer_enabled", true),
            isVoiceCallControlEnabled = prefs.getBoolean("is_voice_call_control_enabled", true),
            announcementTemplate = prefs.getString("announcement_template", "Incoming call from {name}") ?: "Incoming call from {name}",
            ttsSpeechRate = prefs.getFloat("tts_speech_rate", 1.0f),
            ttsPitch = prefs.getFloat("tts_pitch", 1.0f),
            announcementRepeatCount = prefs.getInt("announcement_repeat_count", 2),
            announceUnknownNumbers = prefs.getBoolean("announce_unknown_numbers", true),
            acceptKeywords = prefs.getString("accept_keywords", "accept, receive, answer, yes, pickup") ?: "accept, receive, answer, yes, pickup",
            rejectKeywords = prefs.getString("reject_keywords", "reject, decline, disconnect, no, ignore, hang up") ?: "reject, decline, disconnect, no, ignore, hang up",
            silenceKeywords = prefs.getString("silence_keywords", "silence, mute, quiet, stop") ?: "silence, mute, quiet, stop",
            autoSpeakerphoneOnAccept = prefs.getBoolean("auto_speakerphone_on_accept", true),
            autoListenTimeoutSeconds = prefs.getInt("auto_listen_timeout_seconds", 15),

            isBlocklistEnabled = prefs.getBoolean("is_blocklist_enabled", true),
            autoRejectSpam = prefs.getBoolean("auto_reject_spam", true),
            blockUnknownNumbers = prefs.getBoolean("block_unknown_numbers", false),
            blockedNumbers = loadedBlocked,

            isBluetoothVoiceControl = prefs.getBoolean("is_bluetooth_voice_control", true),
            isFlashlightAlerts = prefs.getBoolean("is_flashlight_alerts", false),
            isHapticFeedbackEnabled = prefs.getBoolean("is_haptic_feedback_enabled", true),
            isProximitySensorSilence = prefs.getBoolean("is_proximity_sensor_silence", true),

            isWhatsAppAutoRead = prefs.getBoolean("is_whatsapp_auto_read", true),
            isWhatsAppAutoReplyEnabled = prefs.getBoolean("whatsapp_auto_reply_enabled", true),
            isYouTubeMediaAutoPause = prefs.getBoolean("is_youtube_media_auto_pause", true),
            isSpotifyAutoDucking = prefs.getBoolean("is_spotify_auto_ducking", true),

            themePreset = prefs.getString("theme_preset", "Siri Spectrum") ?: "Siri Spectrum",

            isDefaultVoiceAssistant = prefs.getBoolean("is_default_voice_assistant", true),
            voiceLanguage = prefs.getString("voice_language", "Hindi (India)") ?: "Hindi (India)",
            voiceResponseStyle = prefs.getString("voice_response_style", "Conversational") ?: "Conversational",
            isBargeInEnabled = prefs.getBoolean("is_barge_in_enabled", true),
            bargeInSensitivity = prefs.getFloat("barge_in_sensitivity", 0.75f),
            bargeInMode = prefs.getString("barge_in_mode", "HYBRID_VAD_STT") ?: "HYBRID_VAD_STT",
            isRealtimeWebSocketEnabled = prefs.getBoolean("is_realtime_websocket_enabled", true),
            bargeInSoundFeedback = prefs.getBoolean("barge_in_sound_feedback", true),

            microphoneSource = prefs.getString("microphone_source", "Built-in Mic (Auto)") ?: "Built-in Mic (Auto)",
            speakerOutput = prefs.getString("speaker_output", "Auto (Speakerphone)") ?: "Auto (Speakerphone)",
            inputSensitivity = prefs.getFloat("input_sensitivity", 0.75f),
            equalizerPreset = prefs.getString("equalizer_preset", "Voice Clarity") ?: "Voice Clarity",
            bassBoost = prefs.getFloat("bass_boost", 0.4f),
            trebleBoost = prefs.getFloat("treble_boost", 0.6f),

            isVoiceIdEnabled = prefs.getBoolean("is_voice_id_enabled", true),
            isPinRequiredForActions = prefs.getBoolean("is_pin_required_for_actions", false),
            securityPin = prefs.getString("security_pin", "1234") ?: "1234",
            isAntiTheftSirenEnabled = prefs.getBoolean("is_anti_theft_siren_enabled", true),
            isIntruderSelfieCaptureEnabled = prefs.getBoolean("is_intruder_selfie_capture_enabled", true),
            isMotionDetectionAlarmEnabled = prefs.getBoolean("is_motion_detection_alarm_enabled", false),
            failedUnlockThreshold = prefs.getInt("failed_unlock_threshold", 2),
            isSosLocationBroadcast = prefs.getBoolean("is_sos_location_broadcast", true),
            isSosSmsBroadcast = prefs.getBoolean("is_sos_sms_broadcast", true),
            isSosSirensBroadcast = prefs.getBoolean("is_sos_sirens_broadcast", true),
            sosAlertMessage = prefs.getString(
                "sos_alert_message",
                "EMERGENCY: I need immediate help. My current GPS location is being broadcast via MAX Assistant."
            ) ?: "EMERGENCY: I need immediate help. My current GPS location is being broadcast via MAX Assistant.",

            isAutoResponderEnabled = prefs.getBoolean("is_auto_responder_enabled", true),
            isSmsAutoReplyEnabled = prefs.getBoolean("is_sms_auto_reply_enabled", true),
            isCallAutoReplyEnabled = prefs.getBoolean("is_call_auto_reply_enabled", true),
            selectedPersona = prefs.getString("selected_persona", "In a Meeting") ?: "In a Meeting",
            customInstructions = prefs.getString(
                "custom_instructions",
                "I am currently in an important meeting and unable to take calls or reply directly right now. I will review your message and get back to you promptly."
            ) ?: "",
            responseTone = prefs.getString("response_tone", "Professional") ?: "Professional",
            activeAiProvider = prefs.getString("active_ai_provider", "gemini") ?: "gemini",
            modelName = prefs.getString("model_name", "gemini-3.5-flash") ?: "gemini-3.5-flash",
            antiSpamCooldownMinutes = prefs.getInt("anti_spam_cooldown_minutes", 3)
        )
    }

    fun updateSettings(newSettings: AppSettings) {
        prefs.edit().apply {
            putBoolean("is_max_assistant_enabled", newSettings.isMaxAssistantEnabled)
            putBoolean("is_call_announcer_enabled", newSettings.isCallAnnouncerEnabled)
            putBoolean("is_voice_call_control_enabled", newSettings.isVoiceCallControlEnabled)
            putString("announcement_template", newSettings.announcementTemplate)
            putFloat("tts_speech_rate", newSettings.ttsSpeechRate)
            putFloat("tts_pitch", newSettings.ttsPitch)
            putInt("announcement_repeat_count", newSettings.announcementRepeatCount)
            putBoolean("announce_unknown_numbers", newSettings.announceUnknownNumbers)
            putString("accept_keywords", newSettings.acceptKeywords)
            putString("reject_keywords", newSettings.rejectKeywords)
            putString("silence_keywords", newSettings.silenceKeywords)
            putBoolean("auto_speakerphone_on_accept", newSettings.autoSpeakerphoneOnAccept)
            putInt("auto_listen_timeout_seconds", newSettings.autoListenTimeoutSeconds)

            putBoolean("is_blocklist_enabled", newSettings.isBlocklistEnabled)
            putBoolean("auto_reject_spam", newSettings.autoRejectSpam)
            putBoolean("block_unknown_numbers", newSettings.blockUnknownNumbers)
            putStringSet("blocked_numbers", newSettings.blockedNumbers)

            putBoolean("is_bluetooth_voice_control", newSettings.isBluetoothVoiceControl)
            putBoolean("is_flashlight_alerts", newSettings.isFlashlightAlerts)
            putBoolean("is_haptic_feedback_enabled", newSettings.isHapticFeedbackEnabled)
            putBoolean("is_proximity_sensor_silence", newSettings.isProximitySensorSilence)

            putBoolean("is_whatsapp_auto_read", newSettings.isWhatsAppAutoRead)
            putBoolean("whatsapp_auto_reply_enabled", newSettings.isWhatsAppAutoReplyEnabled)
            putBoolean("is_youtube_media_auto_pause", newSettings.isYouTubeMediaAutoPause)
            putBoolean("is_spotify_auto_ducking", newSettings.isSpotifyAutoDucking)

            putString("theme_preset", newSettings.themePreset)

            putBoolean("is_default_voice_assistant", newSettings.isDefaultVoiceAssistant)
            putString("voice_language", newSettings.voiceLanguage)
            putString("voice_response_style", newSettings.voiceResponseStyle)
            putBoolean("is_barge_in_enabled", newSettings.isBargeInEnabled)
            putFloat("barge_in_sensitivity", newSettings.bargeInSensitivity)
            putString("barge_in_mode", newSettings.bargeInMode)
            putBoolean("is_realtime_websocket_enabled", newSettings.isRealtimeWebSocketEnabled)
            putBoolean("barge_in_sound_feedback", newSettings.bargeInSoundFeedback)

            putString("microphone_source", newSettings.microphoneSource)
            putString("speaker_output", newSettings.speakerOutput)
            putFloat("input_sensitivity", newSettings.inputSensitivity)
            putString("equalizer_preset", newSettings.equalizerPreset)
            putFloat("bass_boost", newSettings.bassBoost)
            putFloat("treble_boost", newSettings.trebleBoost)

            putBoolean("is_voice_id_enabled", newSettings.isVoiceIdEnabled)
            putBoolean("is_pin_required_for_actions", newSettings.isPinRequiredForActions)
            putString("security_pin", newSettings.securityPin)
            putBoolean("is_anti_theft_siren_enabled", newSettings.isAntiTheftSirenEnabled)
            putBoolean("is_intruder_selfie_capture_enabled", newSettings.isIntruderSelfieCaptureEnabled)
            putBoolean("is_motion_detection_alarm_enabled", newSettings.isMotionDetectionAlarmEnabled)
            putInt("failed_unlock_threshold", newSettings.failedUnlockThreshold)
            putBoolean("is_sos_location_broadcast", newSettings.isSosLocationBroadcast)
            putBoolean("is_sos_sms_broadcast", newSettings.isSosSmsBroadcast)
            putBoolean("is_sos_sirens_broadcast", newSettings.isSosSirensBroadcast)
            putString("sos_alert_message", newSettings.sosAlertMessage)

            putBoolean("is_auto_responder_enabled", newSettings.isAutoResponderEnabled)
            putBoolean("is_sms_auto_reply_enabled", newSettings.isSmsAutoReplyEnabled)
            putBoolean("is_call_auto_reply_enabled", newSettings.isCallAutoReplyEnabled)
            putString("selected_persona", newSettings.selectedPersona)
            putString("custom_instructions", newSettings.customInstructions)
            putString("response_tone", newSettings.responseTone)
            putString("active_ai_provider", newSettings.activeAiProvider)
            putString("model_name", newSettings.modelName)
            putInt("anti_spam_cooldown_minutes", newSettings.antiSpamCooldownMinutes)
            apply()
        }
        _settings.value = newSettings
    }

    fun setMaxAssistantEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(isMaxAssistantEnabled = enabled))
    }

    fun setCallAnnouncerEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(isCallAnnouncerEnabled = enabled))
    }

    fun setVoiceCallControlEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(isVoiceCallControlEnabled = enabled))
    }

    fun setAnnouncementTemplate(template: String) {
        updateSettings(_settings.value.copy(announcementTemplate = template))
    }

    fun setTtsSpeechRate(rate: Float) {
        updateSettings(_settings.value.copy(ttsSpeechRate = rate))
    }

    fun setTtsPitch(pitch: Float) {
        updateSettings(_settings.value.copy(ttsPitch = pitch))
    }

    fun setAnnouncementRepeatCount(count: Int) {
        updateSettings(_settings.value.copy(announcementRepeatCount = count))
    }

    fun setAutoSpeakerphoneOnAccept(enabled: Boolean) {
        updateSettings(_settings.value.copy(autoSpeakerphoneOnAccept = enabled))
    }

    fun setAcceptKeywords(keywords: String) {
        updateSettings(_settings.value.copy(acceptKeywords = keywords))
    }

    fun setRejectKeywords(keywords: String) {
        updateSettings(_settings.value.copy(rejectKeywords = keywords))
    }

    fun setBlocklistEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(isBlocklistEnabled = enabled))
    }

    fun setAutoRejectSpam(enabled: Boolean) {
        updateSettings(_settings.value.copy(autoRejectSpam = enabled))
    }

    fun setBlockUnknownNumbers(enabled: Boolean) {
        updateSettings(_settings.value.copy(blockUnknownNumbers = enabled))
    }

    fun addBlockedNumber(number: String) {
        val trimmed = number.trim()
        if (trimmed.isNotEmpty()) {
            val updated = _settings.value.blockedNumbers.toMutableSet().apply { add(trimmed) }
            updateSettings(_settings.value.copy(blockedNumbers = updated))
        }
    }

    fun removeBlockedNumber(number: String) {
        val updated = _settings.value.blockedNumbers.toMutableSet().apply { remove(number) }
        updateSettings(_settings.value.copy(blockedNumbers = updated))
    }

    fun setBluetoothVoiceControl(enabled: Boolean) {
        updateSettings(_settings.value.copy(isBluetoothVoiceControl = enabled))
    }

    fun setFlashlightAlerts(enabled: Boolean) {
        updateSettings(_settings.value.copy(isFlashlightAlerts = enabled))
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(isHapticFeedbackEnabled = enabled))
    }

    fun setProximitySensorSilence(enabled: Boolean) {
        updateSettings(_settings.value.copy(isProximitySensorSilence = enabled))
    }

    fun setWhatsAppAutoRead(enabled: Boolean) {
        updateSettings(_settings.value.copy(isWhatsAppAutoRead = enabled))
    }

    fun setWhatsAppAutoReplyEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(isWhatsAppAutoReplyEnabled = enabled))
    }

    fun setYouTubeMediaAutoPause(enabled: Boolean) {
        updateSettings(_settings.value.copy(isYouTubeMediaAutoPause = enabled))
    }

    fun setSpotifyAutoDucking(enabled: Boolean) {
        updateSettings(_settings.value.copy(isSpotifyAutoDucking = enabled))
    }

    fun setThemePreset(preset: String) {
        updateSettings(_settings.value.copy(themePreset = preset))
    }

    fun setAutoResponderEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(isAutoResponderEnabled = enabled))
    }

    fun setSmsAutoReplyEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(isSmsAutoReplyEnabled = enabled))
    }

    fun setCallAutoReplyEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(isCallAutoReplyEnabled = enabled))
    }

    fun setPersona(persona: String, defaultInstructions: String? = null) {
        val current = _settings.value
        val instructions = defaultInstructions ?: when (persona) {
            "In a Meeting" -> "I am currently in a meeting and unable to take calls or reply right now. I will review your message shortly."
            "Driving" -> "I am currently driving and cannot respond manually. I will contact you once safely stopped."
            "Vacation / Out of Office" -> "I am out of office on leave with limited connectivity. For urgent matters, please reach out to my team."
            "Do Not Disturb" -> "I am currently in focus mode / Do Not Disturb. I will respond to non-urgent messages later."
            else -> current.customInstructions
        }
        updateSettings(current.copy(selectedPersona = persona, customInstructions = instructions))
    }

    fun setCustomInstructions(instructions: String) {
        updateSettings(_settings.value.copy(customInstructions = instructions))
    }

    fun setResponseTone(tone: String) {
        updateSettings(_settings.value.copy(responseTone = tone))
    }

    fun setAiProvider(provider: String, defaultModel: String? = null) {
        val current = _settings.value
        val model = defaultModel ?: current.modelName
        updateSettings(current.copy(activeAiProvider = provider, modelName = model))
    }

    fun setModelName(model: String) {
        updateSettings(_settings.value.copy(modelName = model))
    }

    // Voice AI Setters
    fun setDefaultVoiceAssistant(enabled: Boolean) {
        updateSettings(_settings.value.copy(isDefaultVoiceAssistant = enabled))
    }

    fun setVoiceLanguage(language: String) {
        updateSettings(_settings.value.copy(voiceLanguage = language))
    }

    fun setVoiceResponseStyle(style: String) {
        updateSettings(_settings.value.copy(voiceResponseStyle = style))
    }

    fun setBargeInEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(isBargeInEnabled = enabled))
    }

    fun setBargeInSensitivity(sensitivity: Float) {
        updateSettings(_settings.value.copy(bargeInSensitivity = sensitivity))
    }

    fun setBargeInMode(mode: String) {
        updateSettings(_settings.value.copy(bargeInMode = mode))
    }

    fun setRealtimeWebSocketEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(isRealtimeWebSocketEnabled = enabled))
    }

    fun setBargeInSoundFeedback(enabled: Boolean) {
        updateSettings(_settings.value.copy(bargeInSoundFeedback = enabled))
    }

    // Hardware Setters
    fun setMicrophoneSource(source: String) {
        updateSettings(_settings.value.copy(microphoneSource = source))
    }

    fun setSpeakerOutput(output: String) {
        updateSettings(_settings.value.copy(speakerOutput = output))
    }

    fun setInputSensitivity(sensitivity: Float) {
        updateSettings(_settings.value.copy(inputSensitivity = sensitivity))
    }

    fun setEqualizerPreset(preset: String) {
        val (bass, treble) = when (preset) {
            "Voice Clarity" -> 0.3f to 0.7f
            "Bass Boost" -> 0.8f to 0.4f
            "Treble Crisp" -> 0.2f to 0.9f
            "Studio Flat" -> 0.5f to 0.5f
            else -> 0.4f to 0.6f
        }
        updateSettings(_settings.value.copy(equalizerPreset = preset, bassBoost = bass, trebleBoost = treble))
    }

    fun setBassBoost(bass: Float) {
        updateSettings(_settings.value.copy(bassBoost = bass))
    }

    fun setTrebleBoost(treble: Float) {
        updateSettings(_settings.value.copy(trebleBoost = treble))
    }

    // Security & SOS Setters
    fun setVoiceIdEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(isVoiceIdEnabled = enabled))
    }

    fun setPinRequiredForActions(required: Boolean) {
        updateSettings(_settings.value.copy(isPinRequiredForActions = required))
    }

    fun setSecurityPin(pin: String) {
        updateSettings(_settings.value.copy(securityPin = pin))
    }

    fun setAntiTheftSirenEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(isAntiTheftSirenEnabled = enabled))
    }

    fun setIntruderSelfieCaptureEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(isIntruderSelfieCaptureEnabled = enabled))
    }

    fun setMotionDetectionAlarmEnabled(enabled: Boolean) {
        updateSettings(_settings.value.copy(isMotionDetectionAlarmEnabled = enabled))
    }

    fun setFailedUnlockThreshold(threshold: Int) {
        updateSettings(_settings.value.copy(failedUnlockThreshold = threshold))
    }

    fun setSosLocationBroadcast(enabled: Boolean) {
        updateSettings(_settings.value.copy(isSosLocationBroadcast = enabled))
    }

    fun setSosSmsBroadcast(enabled: Boolean) {
        updateSettings(_settings.value.copy(isSosSmsBroadcast = enabled))
    }

    fun setSosSirensBroadcast(enabled: Boolean) {
        updateSettings(_settings.value.copy(isSosSirensBroadcast = enabled))
    }

    fun setSosAlertMessage(message: String) {
        updateSettings(_settings.value.copy(sosAlertMessage = message))
    }

    companion object {
        @Volatile
        private var INSTANCE: AppSettingsRepository? = null

        fun getInstance(context: Context): AppSettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

