package com.example.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AutoResponderApp
import com.example.ai.AiResult
import com.example.data.db.AutoResponderEvent
import com.example.data.repository.AppSettings
import com.example.permissions.PermissionHelper
import com.example.security.SecureKeyManager
import com.example.service.MaxAssistantForegroundService
import com.example.telephony.SendSmsResult
import com.example.voice.ContactResolver
import com.example.voice.MaxSttState
import com.example.voice.VoiceCommand
import com.example.voice.VoiceDetectorState
import com.example.whatsapp.WhatsAppControlManager
import com.example.whatsapp.WhatsAppMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SimulationState(
    val isRunning: Boolean = false,
    val simulatedOutput: String? = null,
    val error: String? = null,
    val currentStep: String? = null
)

data class IncomingCallSimState(
    val isRinging: Boolean = false,
    val callerName: String = "",
    val callerNumber: String = "",
    val phase: String = "IDLE", // "IDLE", "RINGING", "ANNOUNCING", "LISTENING", "ACCEPTED", "REJECTED"
    val recognizedCommand: String? = null,
    val statusText: String = ""
)

class AutoResponderViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "AutoResponderVM"
    private val app = application as AutoResponderApp
    private val dao = app.database.autoResponderDao()
    private val callLogDao = app.database.callLogDao()
    private val settingsRepo = app.settingsRepository
    private val geminiService = app.geminiService
    private val smsSender = app.smsSender
    val announcer = app.callAnnouncer
    val voiceDetector = app.voiceCommandDetector
    val audioManagerHelper = app.audioManagerHelper
    val callController = app.callController
    val maxNativeTTS = app.maxNativeTTS
    val swaraTtsService get() = maxNativeTTS
    val appLauncherManager = app.appLauncherManager
    val directCallManager = app.directCallManager
    val intruderSecurityManager = app.intruderSecurityManager
    val speakerVerificationManager = app.speakerVerificationManager
    val localVoiceCommandRouter = app.localVoiceCommandRouter
    val realtimeAudioPlayer = app.realtimeAudioPlayer
    val realtimeBargeInManager = app.realtimeBargeInManager
    val maxRealtimeWebSocketManager = app.maxRealtimeWebSocketManager

    // Real-Time Barge-In & WebSocket Reactive State Flows
    val isBargeInMonitoring: StateFlow<Boolean> = realtimeBargeInManager.isMonitoring
    val isBargeInInterrupted: StateFlow<Boolean> = realtimeBargeInManager.isInterrupted
    val bargeInCount: StateFlow<Int> = realtimeBargeInManager.bargeInCount
    val bargeInCurrentMicDb: StateFlow<Float> = realtimeBargeInManager.currentMicDb
    val lastBargeInReason: StateFlow<String?> = realtimeBargeInManager.lastInterruptionReason
    val isRealtimeWebSocketConnected: StateFlow<Boolean> = maxRealtimeWebSocketManager.isConnected
    val realtimeLatencyMs: StateFlow<Long> = maxRealtimeWebSocketManager.latencyMs
    val realtimeConnectionState = maxRealtimeWebSocketManager.connectionState
    val isStreamingWebSocketAudio = maxRealtimeWebSocketManager.isStreamingAudio

    val failedUnlockCount: StateFlow<Int> = intruderSecurityManager.failedUnlockCount
    val lastFailedTimestamp: StateFlow<Long?> = intruderSecurityManager.lastFailedTimestamp
    val isAlarmRinging: StateFlow<Boolean> = intruderSecurityManager.isAlarmRinging
    val isMotionArmed: StateFlow<Boolean> = intruderSecurityManager.isMotionArmed
    val lastSecurityStatus: StateFlow<String> = intruderSecurityManager.lastSecurityStatus
    val capturedIntruderImages: StateFlow<List<java.io.File>> = intruderSecurityManager.capturedIntruderImages

    val isVoiceEnrolled: StateFlow<Boolean> = speakerVerificationManager.isVoiceEnrolled
    val speakerTargetThreshold: StateFlow<Float> = speakerVerificationManager.targetThreshold
    val lastVerificationScore: StateFlow<Float?> = speakerVerificationManager.lastVerificationScore

    val settings: StateFlow<AppSettings> = settingsRepo.settings

    val events: StateFlow<List<AutoResponderEvent>> = dao.getAllEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callHistoryLogs: StateFlow<List<com.example.data.db.CallHistoryLog>> = callLogDao.getAllCallLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val acceptedCallsCount: StateFlow<Int> = callLogDao.getAcceptedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val rejectedCallsCount: StateFlow<Int> = callLogDao.getRejectedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _missingPermissions = MutableStateFlow(PermissionHelper.getMissingPermissions(application))
    val missingPermissions: StateFlow<List<String>> = _missingPermissions.asStateFlow()

    private val _simulationState = MutableStateFlow(SimulationState())
    val simulationState: StateFlow<SimulationState> = _simulationState.asStateFlow()

    private val _incomingCallSimState = MutableStateFlow(IncomingCallSimState())
    val incomingCallSimState: StateFlow<IncomingCallSimState> = _incomingCallSimState.asStateFlow()

    private val _apiKeyText = MutableStateFlow(SecureKeyManager.getApiKey(application))
    val apiKeyText: StateFlow<String> = _apiKeyText.asStateFlow()

    private val _isValidatingGeminiKey = MutableStateFlow(false)
    val isValidatingGeminiKey: StateFlow<Boolean> = _isValidatingGeminiKey.asStateFlow()

    private val _geminiValidationStatus = MutableStateFlow<String?>(null)
    val geminiValidationStatus: StateFlow<String?> = _geminiValidationStatus.asStateFlow()

    private val _elevenLabsApiKeyText = MutableStateFlow(app.elevenLabsKeyManager.getApiKey())
    val elevenLabsApiKeyText: StateFlow<String> = _elevenLabsApiKeyText.asStateFlow()

    private val _isValidatingElevenLabsKey = MutableStateFlow(false)
    val isValidatingElevenLabsKey: StateFlow<Boolean> = _isValidatingElevenLabsKey.asStateFlow()

    private val _elevenLabsValidationStatus = MutableStateFlow<String?>(null)
    val elevenLabsValidationStatus: StateFlow<String?> = _elevenLabsValidationStatus.asStateFlow()

    // MAX Voice Orb Reactive State Flows
    private val _isVoiceOrbActive = MutableStateFlow(false)
    val isVoiceOrbActive: StateFlow<Boolean> = _isVoiceOrbActive.asStateFlow()

    private val _isVoiceOrbListening = MutableStateFlow(false)
    val isVoiceOrbListening: StateFlow<Boolean> = _isVoiceOrbListening.asStateFlow()

    private val _isVoiceOrbSpeaking = MutableStateFlow(false)
    val isVoiceOrbSpeaking: StateFlow<Boolean> = _isVoiceOrbSpeaking.asStateFlow()

    private val _voiceOrbStatus = MutableStateFlow("Tap MAX Voice Orb to speak")
    val voiceOrbStatus: StateFlow<String> = _voiceOrbStatus.asStateFlow()

    private val _voiceOrbRmsDb = MutableStateFlow(0f)
    val voiceOrbRmsDb: StateFlow<Float> = _voiceOrbRmsDb.asStateFlow()

    val elevenLabsKeyManager = app.elevenLabsKeyManager
    val elevenLabsService = app.elevenLabsTtsService
    val maxSttManager = app.maxSttManager

    // STT State Flows
    val sttState: StateFlow<MaxSttState> = maxSttManager.sttState
    val sttRmsDbLevel: StateFlow<Float> = maxSttManager.rmsDbLevel
    val sttPartialText: StateFlow<String> = maxSttManager.partialText

    private val _sttConversationLog = MutableStateFlow<List<Pair<String, String>>>(emptyList()) // Pair(User, AI)
    val sttConversationLog: StateFlow<List<Pair<String, String>>> = _sttConversationLog.asStateFlow()

    private val _sttPipelineStatus = MutableStateFlow<String?>(null)
    val sttPipelineStatus: StateFlow<String?> = _sttPipelineStatus.asStateFlow()

    // Text & Search Query State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isGeminiProcessing = MutableStateFlow(false)
    val isGeminiProcessing: StateFlow<Boolean> = _isGeminiProcessing.asStateFlow()

    private val _latestAssistantResponse = MutableStateFlow<String?>(null)
    val latestAssistantResponse: StateFlow<String?> = _latestAssistantResponse.asStateFlow()

    // System Overlay Service State
    val isOverlayActive: StateFlow<Boolean> = com.example.overlay.MaxOverlayService.isOverlayActive

    // WhatsApp Control Manager
    val whatsAppManager = WhatsAppControlManager.instance
    val whatsAppMessages: StateFlow<List<WhatsAppMessage>> = whatsAppManager.messages
    val whatsAppStatus: StateFlow<String?> = whatsAppManager.lastInterceptedStatus

    // Device Quick Settings & Toggle Manager
    val deviceToggleManager = app.deviceToggleManager
    val isFlashlightOn: StateFlow<Boolean> = deviceToggleManager.isFlashlightOn
    val soundMode: StateFlow<com.example.toggle.SoundMode> = deviceToggleManager.soundMode
    val isWifiEnabled: StateFlow<Boolean> = deviceToggleManager.isWifiEnabled
    val brightnessPercent: StateFlow<Int> = deviceToggleManager.brightnessPercent

    // Accessibility Service State Flows
    val isAccessibilityConnected: StateFlow<Boolean> = com.example.accessibility.MaxAccessibilityService.isServiceConnected
    val isAutoScrolling: StateFlow<Boolean> = com.example.accessibility.MaxAccessibilityService.isAutoScrolling
    val autoScrollSpeedMs: StateFlow<Long> = com.example.accessibility.MaxAccessibilityService.autoScrollSpeedMs
    val accessibilityStatus: StateFlow<String?> = com.example.accessibility.MaxAccessibilityService.lastActionStatus

    // Emergency SOS & Live Location State Flows
    val emergencySosManager = app.emergencySosManager
    val currentLocation: StateFlow<com.example.sos.LocationData?> = emergencySosManager.currentLocation
    val sosContacts: StateFlow<List<String>> = emergencySosManager.sosContacts
    val isSosDispatching: StateFlow<Boolean> = emergencySosManager.isDispatching
    val sosStatus: StateFlow<String?> = emergencySosManager.lastSosStatus

    // Camera & Selfie Trigger State Flows
    val maxCameraManager = app.maxCameraManager
    val isFrontCamera: StateFlow<Boolean> = maxCameraManager.isFrontCamera
    val isRecordingVideo: StateFlow<Boolean> = maxCameraManager.isRecordingVideo
    val cameraZoomRatio: StateFlow<Float> = maxCameraManager.zoomRatio
    val lastCapturedPhotoUri: StateFlow<android.net.Uri?> = maxCameraManager.lastCapturedPhotoUri
    val lastCapturedVideoUri: StateFlow<android.net.Uri?> = maxCameraManager.lastCapturedVideoUri
    val cameraStatus: StateFlow<String?> = maxCameraManager.cameraStatus

    // OpenWakeWord Multi-Wake-Word ("Okay Max", "Backup Max", "Hey Max") State Flows
    val openWakeWordDetector = app.openWakeWordDetector
    val wakeWordState = openWakeWordDetector.state
    val wakeWordRmsDb = openWakeWordDetector.rmsDbLevel

    // Service & Voice reactive state flows
    val isServiceRunning: StateFlow<Boolean> = MaxAssistantForegroundService.isServiceRunning
    val liveCallStatus: StateFlow<String> = MaxAssistantForegroundService.liveCallStatus
    val liveVoiceState: StateFlow<String> = MaxAssistantForegroundService.liveVoiceState
    val voiceDetectorState: StateFlow<VoiceDetectorState> = voiceDetector.detectorState
    val rmsDbLevel: StateFlow<Float> = voiceDetector.rmsDbLevel
    val micAudioAmplitude: StateFlow<Float> = combine(
        _voiceOrbRmsDb,
        maxSttManager.rmsDbLevel,
        voiceDetector.rmsDbLevel
    ) { orbDb, sttDb, detectorDb ->
        maxOf(orbDb, sttDb, detectorDb).coerceAtLeast(0f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)
    val isTtsSpeaking: StateFlow<Boolean> = combine(
        announcer.isSpeaking,
        maxNativeTTS.isSpeaking,
        _isVoiceOrbSpeaking
    ) { speakingTts, speakingNative, speakingOrb ->
        speakingTts || speakingNative || speakingOrb
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // State tracking for Accept / Reject toggles and Caller Announcer state changes
    private val _toggleFeedbackEvent = MutableStateFlow<ToggleFeedback?>(null)
    val toggleFeedbackEvent: StateFlow<ToggleFeedback?> = _toggleFeedbackEvent.asStateFlow()

    fun notifyToggleState(title: String, isEnabled: Boolean, speakOutLoud: Boolean = true) {
        val stateText = if (isEnabled) "enabled" else "disabled"
        val confirmationText = "$title is now ${if (isEnabled) "ENABLED" else "DISABLED"}"
        _toggleFeedbackEvent.value = ToggleFeedback(
            title = title,
            isEnabled = isEnabled,
            message = confirmationText
        )
        if (speakOutLoud) {
            val speech = "$title $stateText"
            audioManagerHelper.requestVoiceAssistantAudioFocus()
            announcer.speak(
                text = speech,
                speechRate = 1.05f,
                speechPitch = 1.0f,
                onDone = {
                    audioManagerHelper.releaseVoiceAssistantAudioFocus()
                }
            )
        }
    }

    fun clearToggleFeedback() {
        _toggleFeedbackEvent.value = null
    }

    init {
        // Sync barge-in settings
        viewModelScope.launch {
            settingsRepo.settings.collect { currentSettings ->
                realtimeBargeInManager.setBargeInEnabled(currentSettings.isBargeInEnabled)
                realtimeBargeInManager.setSensitivity(currentSettings.bargeInSensitivity)
            }
        }

        // Setup in-app voice detector listeners for test workbench
        voiceDetector.onCommandListener = { cmd, raw ->
            viewModelScope.launch {
                handleRecognizedVoiceCommand(cmd, raw)
            }
        }

        // Setup native STT manager callback pipeline: User Speech -> Gemini AI -> ElevenLabs/System TTS
        maxSttManager.onSpeechRecognizedListener = { spokenText ->
            _isVoiceOrbListening.value = false
            _voiceOrbStatus.value = "Recognized: \"$spokenText\" • Asking Gemini AI..."
            processSttUserQuery(spokenText)
        }
        maxSttManager.onErrorListener = { errorMsg ->
            _isVoiceOrbListening.value = false
            _voiceOrbStatus.value = "Speech recognition: $errorMsg. Tap Orb to retry."
            _sttPipelineStatus.value = "STT Error: $errorMsg"
        }

        // Forward rmsDbLevel for voice orb dynamic pulsating
        viewModelScope.launch {
            maxSttManager.rmsDbLevel.collect { db ->
                if (_isVoiceOrbListening.value) {
                    _voiceOrbRmsDb.value = db
                }
            }
        }

        // Forward partial live transcription to orb status
        viewModelScope.launch {
            maxSttManager.partialText.collect { partial ->
                if (partial.isNotBlank() && _isVoiceOrbListening.value) {
                    _voiceOrbStatus.value = "Hearing: \"$partial\"..."
                }
            }
        }
    }

    fun refreshPermissions() {
        _missingPermissions.value = PermissionHelper.getMissingPermissions(getApplication())
    }

    fun toggleMaxAssistant(enabled: Boolean) {
        val context = getApplication<Application>()
        settingsRepo.setMaxAssistantEnabled(enabled)
        if (enabled) {
            MaxAssistantForegroundService.startService(context)
            if (PermissionHelper.canDrawOverlays(context)) {
                com.example.overlay.MaxOverlayService.showOverlay(context)
            }
        } else {
            MaxAssistantForegroundService.stopService(context)
            com.example.overlay.MaxOverlayService.hideOverlay(context)
            announcer.stop()
            app.callAnnouncer.stop()
            voiceDetector.stopListening()
            audioManagerHelper.releaseVoiceAssistantAudioFocus()
        }
    }

    fun toggleCallAnnouncer(enabled: Boolean) {
        settingsRepo.setCallAnnouncerEnabled(enabled)
        if (!enabled) {
            announcer.stop()
            app.callAnnouncer.stop()
            if (_incomingCallSimState.value.phase == "ANNOUNCING") {
                _incomingCallSimState.value = _incomingCallSimState.value.copy(
                    phase = "RINGING",
                    statusText = "Caller Announcer disabled. Phone is ringing..."
                )
            }
        }
        notifyToggleState("Caller Voice Announcer", enabled)
    }

    fun toggleVoiceCallControl(enabled: Boolean) {
        settingsRepo.setVoiceCallControlEnabled(enabled)
        if (!enabled) {
            voiceDetector.stopListening()
            if (_incomingCallSimState.value.phase == "LISTENING") {
                _incomingCallSimState.value = _incomingCallSimState.value.copy(
                    phase = "RINGING",
                    statusText = "Voice Call Control disabled. Phone is ringing..."
                )
                audioManagerHelper.releaseVoiceAssistantAudioFocus()
            }
        }
        notifyToggleState("Voice Call Control", enabled)
    }

    fun toggleVoiceAcceptCommands(enabled: Boolean) {
        settingsRepo.setVoiceAcceptCommandsEnabled(enabled)
        notifyToggleState("Voice Accept Command", enabled)
    }

    fun toggleVoiceRejectCommands(enabled: Boolean) {
        settingsRepo.setVoiceRejectCommandsEnabled(enabled)
        notifyToggleState("Voice Reject Command", enabled)
    }

    fun setAnnouncementTemplate(template: String) {
        settingsRepo.setAnnouncementTemplate(template)
    }

    fun setTtsSpeechRate(rate: Float) {
        settingsRepo.setTtsSpeechRate(rate)
    }

    fun setTtsPitch(pitch: Float) {
        settingsRepo.setTtsPitch(pitch)
    }

    fun setAnnouncementRepeatCount(count: Int) {
        settingsRepo.setAnnouncementRepeatCount(count)
    }

    fun setAcceptKeywords(keywords: String) {
        settingsRepo.setAcceptKeywords(keywords)
    }

    fun setRejectKeywords(keywords: String) {
        settingsRepo.setRejectKeywords(keywords)
    }

    fun setAutoSpeakerphoneOnAccept(enabled: Boolean) {
        settingsRepo.setAutoSpeakerphoneOnAccept(enabled)
        notifyToggleState("Auto-Speakerphone on Accept", enabled)
    }

    // Blocklist & Spam Management
    fun toggleBlocklist(enabled: Boolean) {
        settingsRepo.setBlocklistEnabled(enabled)
        notifyToggleState("Call Blocklist", enabled)
    }

    fun toggleAutoRejectSpam(enabled: Boolean) {
        settingsRepo.setAutoRejectSpam(enabled)
        notifyToggleState("Auto-Reject Spam Calls", enabled)
    }

    fun toggleBlockUnknownNumbers(enabled: Boolean) {
        settingsRepo.setBlockUnknownNumbers(enabled)
    }

    fun addBlockedNumber(number: String) {
        settingsRepo.addBlockedNumber(number)
    }

    fun removeBlockedNumber(number: String) {
        settingsRepo.removeBlockedNumber(number)
    }

    // Hardware & System Control
    fun toggleBluetoothVoiceControl(enabled: Boolean) {
        settingsRepo.setBluetoothVoiceControl(enabled)
    }

    fun toggleFlashlightAlerts(enabled: Boolean) {
        settingsRepo.setFlashlightAlerts(enabled)
    }

    fun toggleHapticFeedback(enabled: Boolean) {
        settingsRepo.setHapticFeedbackEnabled(enabled)
    }

    fun toggleProximitySensorSilence(enabled: Boolean) {
        settingsRepo.setProximitySensorSilence(enabled)
    }

    // App & Media Control
    fun toggleWhatsAppAutoRead(enabled: Boolean) {
        settingsRepo.setWhatsAppAutoRead(enabled)
    }

    fun toggleWhatsAppAutoReply(enabled: Boolean) {
        settingsRepo.setWhatsAppAutoReplyEnabled(enabled)
    }

    fun toggleYouTubeMediaAutoPause(enabled: Boolean) {
        settingsRepo.setYouTubeMediaAutoPause(enabled)
    }

    fun toggleSpotifyAutoDucking(enabled: Boolean) {
        settingsRepo.setSpotifyAutoDucking(enabled)
    }

    // Theme Customization
    fun setThemePreset(preset: String) {
        settingsRepo.setThemePreset(preset)
    }

    // Voice AI Settings
    fun setDefaultVoiceAssistant(enabled: Boolean) {
        settingsRepo.setDefaultVoiceAssistant(enabled)
    }

    fun setVoiceLanguage(language: String) {
        settingsRepo.setVoiceLanguage(language)
    }

    fun setVoiceResponseStyle(style: String) {
        settingsRepo.setVoiceResponseStyle(style)
    }

    // Hardware Settings
    fun setMicrophoneSource(source: String) {
        settingsRepo.setMicrophoneSource(source)
    }

    fun setSpeakerOutput(output: String) {
        settingsRepo.setSpeakerOutput(output)
    }

    fun setInputSensitivity(sensitivity: Float) {
        settingsRepo.setInputSensitivity(sensitivity)
    }

    fun setEqualizerPreset(preset: String) {
        settingsRepo.setEqualizerPreset(preset)
    }

    fun setBassBoost(bass: Float) {
        settingsRepo.setBassBoost(bass)
    }

    fun setTrebleBoost(treble: Float) {
        settingsRepo.setTrebleBoost(treble)
    }

    // Security & SOS Settings
    fun setVoiceIdEnabled(enabled: Boolean) {
        settingsRepo.setVoiceIdEnabled(enabled)
    }

    fun setPinRequiredForActions(required: Boolean) {
        settingsRepo.setPinRequiredForActions(required)
    }

    fun setSecurityPin(pin: String) {
        settingsRepo.setSecurityPin(pin)
    }

    fun setSosLocationBroadcast(enabled: Boolean) {
        settingsRepo.setSosLocationBroadcast(enabled)
    }

    fun setSosSmsBroadcast(enabled: Boolean) {
        settingsRepo.setSosSmsBroadcast(enabled)
    }

    fun setSosSirensBroadcast(enabled: Boolean) {
        settingsRepo.setSosSirensBroadcast(enabled)
    }

    fun setSosAlertMessage(message: String) {
        settingsRepo.setSosAlertMessage(message)
    }

    fun toggleAutoResponder(enabled: Boolean) {
        settingsRepo.setAutoResponderEnabled(enabled)
    }

    fun toggleSmsAutoReply(enabled: Boolean) {
        settingsRepo.setSmsAutoReplyEnabled(enabled)
    }

    fun toggleCallAutoReply(enabled: Boolean) {
        settingsRepo.setCallAutoReplyEnabled(enabled)
    }

    fun setPersona(persona: String) {
        settingsRepo.setPersona(persona)
    }

    fun setCustomInstructions(instructions: String) {
        settingsRepo.setCustomInstructions(instructions)
    }

    fun setResponseTone(tone: String) {
        settingsRepo.setResponseTone(tone)
    }

    fun setCooldownMinutes(minutes: Int) {
        settingsRepo.updateSettings(settings.value.copy(antiSpamCooldownMinutes = minutes))
    }

    fun setAiProvider(providerId: String) {
        val provider = com.example.ai.ApiProvider.fromId(providerId)
        SecureKeyManager.setActiveProvider(getApplication(), provider)
        settingsRepo.setAiProvider(providerId, provider.defaultModel)
        _apiKeyText.value = SecureKeyManager.getApiKey(getApplication())
    }

    fun setModelName(modelName: String) {
        settingsRepo.setModelName(modelName)
    }

    fun detectApiProvider(candidateKey: String): com.example.ai.ApiProvider {
        return com.example.ai.ApiProvider.detectProvider(candidateKey)
    }

    fun saveApiKey(newKey: String) {
        val provider = com.example.ai.ApiProvider.detectProvider(newKey)
        SecureKeyManager.saveProviderApiKey(getApplication(), provider, newKey)
        SecureKeyManager.setActiveProvider(getApplication(), provider)
        settingsRepo.setAiProvider(provider.id, provider.defaultModel)
        _apiKeyText.value = SecureKeyManager.getApiKey(getApplication())
        _geminiValidationStatus.value = "${provider.displayName} key saved to SharedPreferences."
    }

    /**
     * Auto-detects the provider and triggers a validation check automatically upon entry.
     * On successful validation, securely saves the key in SharedPreferences, activates the service,
     * and dynamically updates the version matching.
     */
    fun validateAndSaveApiKey(candidateKey: String, onResult: ((Boolean, String) -> Unit)? = null) {
        val trimmed = candidateKey.trim()
        if (trimmed.isBlank()) {
            _geminiValidationStatus.value = "Please enter an API key."
            onResult?.invoke(false, "Please enter an API key.")
            return
        }

        val detectedProvider = com.example.ai.ApiProvider.detectProvider(trimmed)

        viewModelScope.launch {
            _isValidatingGeminiKey.value = true
            _geminiValidationStatus.value = "Auto-detected ${detectedProvider.displayName}. Validating API key..."

            val result = geminiService.validateAndSaveApiKey(trimmed)
            _isValidatingGeminiKey.value = false

            when (result) {
                is com.example.ai.GeminiKeyValidationResult.Success -> {
                    _apiKeyText.value = SecureKeyManager.getApiKey(getApplication())
                    _geminiValidationStatus.value = result.message
                    onResult?.invoke(true, result.message)
                }
                is com.example.ai.GeminiKeyValidationResult.Error -> {
                    _geminiValidationStatus.value = result.message
                    onResult?.invoke(false, result.message)
                }
            }
        }
    }

    /**
     * Backward-compatible alias for validateAndSaveApiKey
     */
    fun validateAndSaveGeminiApiKey(candidateKey: String, onResult: ((Boolean, String) -> Unit)? = null) {
        validateAndSaveApiKey(candidateKey, onResult)
    }

    fun validateAndSaveElevenLabsApiKey(candidateKey: String, onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            _isValidatingElevenLabsKey.value = true
            _elevenLabsValidationStatus.value = "Validating API Key with ElevenLabs server..."
            
            val result = elevenLabsKeyManager.validateAndSaveApiKey(candidateKey)
            _isValidatingElevenLabsKey.value = false

            when (result) {
                is com.example.voice.ElevenLabsKeyValidationResult.Success -> {
                    _elevenLabsApiKeyText.value = elevenLabsKeyManager.getApiKey()
                    _elevenLabsValidationStatus.value = result.message
                    onResult?.invoke(true, result.message)
                }
                is com.example.voice.ElevenLabsKeyValidationResult.Error -> {
                    _elevenLabsValidationStatus.value = result.message
                    onResult?.invoke(false, result.message)
                }
            }
        }
    }

    fun clearElevenLabsApiKey() {
        elevenLabsKeyManager.clearApiKey()
        _elevenLabsApiKeyText.value = ""
        _elevenLabsValidationStatus.value = "API key cleared."
    }

    fun clearApiKey() {
        SecureKeyManager.clearCustomApiKey(getApplication())
        _apiKeyText.value = ""
        _geminiValidationStatus.value = "Gemini key cleared."
    }

    /**
     * Toggles the MAX Voice Orb:
     * When toggled ON, listens for user voice with OpenWakeWord multi-engine, sends text to Gemini AI,
     * and speaks back responses via ElevenLabs or System TTS.
     * When toggled OFF or tapped while active, cancels speech/listening.
     */
    fun toggleVoiceOrb() {
        if (_isVoiceOrbSpeaking.value || announcer.isSpeaking.value || elevenLabsService.isPlayingAudio.value) {
            stopTtsVoice()
            elevenLabsService.stopAudio()
            _isVoiceOrbSpeaking.value = false
            _isVoiceOrbListening.value = false
            _isVoiceOrbActive.value = false
            _voiceOrbStatus.value = "Speech stopped. Tap MAX Orb to speak."
        } else if (_isVoiceOrbListening.value) {
            stopSttAssistantListening()
            _isVoiceOrbListening.value = false
            _isVoiceOrbActive.value = false
            _voiceOrbStatus.value = "Listening cancelled. Tap MAX Orb to speak."
        } else {
            startVoiceOrbListening()
        }
    }

    fun startVoiceOrbListening() {
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _isVoiceOrbListening.value = false
            _isVoiceOrbActive.value = false
            _voiceOrbStatus.value = "Microphone access needed. Tap to grant permission."
            _sttPipelineStatus.value = "RECORD_AUDIO permission missing. Please grant microphone access."
            return
        }
        openWakeWordDetector.pauseListening()
        _isVoiceOrbActive.value = true
        _isVoiceOrbListening.value = true
        _isVoiceOrbSpeaking.value = false
        _voiceOrbStatus.value = "Listening with OpenWakeWord multi-engine & Gemini AI..."
        audioManagerHelper.playListeningPromptBeep()
        maxSttManager.startListening(preferredLanguage = "hi-IN")
    }

    fun stopVoiceOrb() {
        _isVoiceOrbListening.value = false
        _isVoiceOrbSpeaking.value = false
        _isVoiceOrbActive.value = false
        maxSttManager.stopListening()
        swaraTtsService.stop()
        elevenLabsService.stopAudio()
        announcer.stop()
        openWakeWordDetector.resumeListening()
        _voiceOrbStatus.value = "Tap MAX Voice Orb to speak"
    }

    fun testMaxNativeTts(
        text: String = "नमस्ते! मैं मैक्स हूँ। मैं आपकी क्या मदद कर सकता हूँ?",
        onResult: (String) -> Unit
    ) {
        onResult("Speaking via MAX Native TTS (Hindi India)...")
        maxNativeTTS.speak(text) {
            onResult("MAX Native TTS voice playback completed!")
        }
    }

    fun testSwaraVoice(
        text: String = "नमस्ते! मैं मैक्स हूँ। मैं आपकी क्या मदद कर सकता हूँ?",
        onResult: (String) -> Unit
    ) {
        testMaxNativeTts(text, onResult)
    }

    fun testElevenLabsVoice(
        text: String = "नमस्ते! मैं मैक्स हूँ। मैं आपकी क्या मदद कर सकता हूँ?",
        voiceId: String = "hindi_voice",
        onResult: (String) -> Unit
    ) {
        testMaxNativeTts(text, onResult)
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearAll()
            callLogDao.clearAllCallLogs()
        }
    }

    fun clearCallHistoryLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            callLogDao.clearAllCallLogs()
        }
    }

    /**
     * Starts native STT recognition for Hindi and English speech input.
     */
    fun startSttAssistantListening(preferredLang: String = "hi-IN") {
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _sttPipelineStatus.value = "RECORD_AUDIO permission missing. Please grant microphone access."
            return
        }
        audioManagerHelper.playListeningPromptBeep()
        _sttPipelineStatus.value = "Listening for Hindi / English speech..."
        maxSttManager.startListening(preferredLanguage = preferredLang)
    }

    /**
     * Stops native STT recognition.
     */
    fun stopSttAssistantListening() {
        maxSttManager.stopListening()
        _sttPipelineStatus.value = "STT Stopped."
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun clearAssistantResponse() {
        _latestAssistantResponse.value = null
    }

    fun toggleSystemOverlay(context: Context) {
        if (!PermissionHelper.canDrawOverlays(context)) {
            PermissionHelper.openOverlaySettings(context)
        } else {
            if (isOverlayActive.value) {
                com.example.overlay.MaxOverlayService.hideOverlay(context)
            } else {
                com.example.overlay.MaxOverlayService.showOverlay(context)
            }
        }
    }

    /**
     * Sends typed text query directly to Gemini AI assistant, displays response,
     * and speaks response back via TTS while animating wave visualizer.
     */
    fun sendTextMessage(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        _searchQuery.value = ""
        processSttUserQuery(trimmed)
    }

    private var activeVoiceResponseJob: kotlinx.coroutines.Job? = null

    /**
     * Processes recognized user speech or typed text query:
     * First checks hardware toggles -> fallback to Gemini AI -> TTS playback.
     */
    fun processSttUserQuery(spokenText: String) {
        if (spokenText.isBlank()) return

        activeVoiceResponseJob?.cancel()
        activeVoiceResponseJob = viewModelScope.launch {
            _isGeminiProcessing.value = true

            // Register generation cancellation hook for real-time barge-in interruption
            realtimeBargeInManager.activeGenerationCancellationCallback = {
                activeVoiceResponseJob?.cancel()
                maxRealtimeWebSocketManager.sendInterruptSignal()
            }

            // 1. Evaluate via Local Command Router (Direct Device Actions & Fast Local Intent Execution)
            when (val routeResult = localVoiceCommandRouter.routeCommand(spokenText)) {
                is com.example.voice.CommandRouteResult.LocalAction -> {
                    val feedback = routeResult.feedbackMessage
                    _isGeminiProcessing.value = false
                    _latestAssistantResponse.value = feedback
                    _sttPipelineStatus.value = "⚡ Local Action (${routeResult.actionType}): $feedback"

                    com.example.ai.ConversationContextManager.getInstance().addTurn("user", spokenText, routeResult.actionType)
                    com.example.ai.ConversationContextManager.getInstance().addTurn("assistant", feedback)

                    val currentList = _sttConversationLog.value.toMutableList()
                    currentList.add(Pair(spokenText, feedback))
                    _sttConversationLog.value = currentList

                    val intentToLaunch = routeResult.launchIntent
                    val postAction = routeResult.postSpeechAction

                    if (intentToLaunch != null || postAction != null) {
                        swaraTtsService.speak(feedback, onDone = {
                            postAction?.invoke()
                            if (intentToLaunch != null) {
                                appLauncherManager.launchIntentNow(intentToLaunch)
                            }
                        })
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(1200)
                            postAction?.invoke()
                            if (intentToLaunch != null) {
                                appLauncherManager.launchIntentNow(intentToLaunch)
                            }
                        }
                    } else {
                        swaraTtsService.speak(feedback)
                    }
                    return@launch
                }

                is com.example.voice.CommandRouteResult.ComplexAiQuery -> {
                    // 2. Route only complex queries to Gemini API for natural language reasoning
                    val targetQuery = routeResult.cleanedQuery
                    _sttPipelineStatus.value = "User: \"$targetQuery\" -> Streaming Gemini AI..."
                    _isGeminiProcessing.value = true

                    // Activate real-time barge-in Voice Activity Detection
                    realtimeBargeInManager.startMonitoring { reason ->
                        _isGeminiProcessing.value = false
                        _isVoiceOrbSpeaking.value = false
                        _voiceOrbStatus.value = "⚡ Interrupted ($reason). Listening to your new input..."
                        _sttPipelineStatus.value = "⚡ Barge-In triggered: listening for new speech..."
                        startVoiceOrbListening()
                    }

                    var fullText = ""
                    var isFirstChunk = true

                    try {
                        if (settings.value.isRealtimeWebSocketEnabled && maxRealtimeWebSocketManager.isConnected.value) {
                            _voiceOrbStatus.value = "Live WebSocket: $targetQuery"
                            maxRealtimeWebSocketManager.sendTextMessage(targetQuery)
                        }

                        geminiService.streamMaxVoiceResponse(
                            userQuery = targetQuery,
                            settings = settings.value
                        ).collect { chunk ->
                            _isGeminiProcessing.value = false
                            _isVoiceOrbSpeaking.value = true
                            fullText += chunk
                            _latestAssistantResponse.value = fullText
                            _voiceOrbStatus.value = "MAX: $fullText"
                            _sttPipelineStatus.value = "Speaking via MAX Native TTS (Barge-in active)..."

                            maxNativeTTS.speakChunk(chunk, isFirstChunk)
                            isFirstChunk = false
                        }

                        if (fullText.isNotBlank()) {
                            com.example.ai.ConversationContextManager.getInstance().addTurn("user", targetQuery)
                            com.example.ai.ConversationContextManager.getInstance().addTurn("assistant", fullText)
                            val currentList = _sttConversationLog.value.toMutableList()
                            currentList.add(Pair(targetQuery, fullText))
                            _sttConversationLog.value = currentList
                        } else {
                            val fallbackMsg = if (!SecureKeyManager.hasValidApiKey(getApplication())) {
                                "Gemini API key is not configured. Please save your API key in Settings."
                            } else {
                                "Sorry, I could not complete the request right now."
                            }
                            _latestAssistantResponse.value = fallbackMsg
                            _voiceOrbStatus.value = fallbackMsg
                            swaraTtsService.speak(fallbackMsg)
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) {
                            Log.i(tag, "Streaming response cancelled cleanly by user barge-in.")
                        } else {
                            Log.e(tag, "Error during Gemini streaming: ${e.message}", e)
                            val err = "Error: ${e.localizedMessage ?: "Network error"}"
                            _sttPipelineStatus.value = err
                            _latestAssistantResponse.value = err
                            swaraTtsService.speak(err)
                        }
                    } finally {
                        _isGeminiProcessing.value = false
                        realtimeBargeInManager.stopMonitoring()
                        openWakeWordDetector.resumeListening()
                    }
                }
            }
        }
    }

    private fun fallbackAndroidTts(text: String, onDone: (() -> Unit)? = null) {
        _isVoiceOrbSpeaking.value = true
        audioManagerHelper.requestVoiceAssistantAudioFocus()
        announcer.announceCaller(
            callerNameOrNumber = text,
            template = "{name}",
            speechRate = settings.value.ttsSpeechRate,
            speechPitch = settings.value.ttsPitch,
            repeatCount = 1,
            onDone = {
                audioManagerHelper.releaseVoiceAssistantAudioFocus()
                _isVoiceOrbSpeaking.value = false
                _isVoiceOrbActive.value = false
                _voiceOrbStatus.value = "Tap MAX Voice Orb to speak"
                openWakeWordDetector.resumeListening()
                onDone?.invoke()
            }
        )
    }

    // WhatsApp Mobile Control Methods
    fun isNotificationListenerGranted(): Boolean {
        return WhatsAppControlManager.isNotificationListenerGranted(getApplication())
    }

    fun openNotificationListenerSettings(context: android.content.Context) {
        val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun readAloudWhatsAppMessage(msg: WhatsAppMessage) {
        whatsAppManager.readAloudMessage(
            message = msg,
            elevenLabsService = elevenLabsService,
            elevenLabsKeyManager = elevenLabsKeyManager,
            announcer = announcer,
            scope = viewModelScope
        ) { status ->
            _sttPipelineStatus.value = status
        }
    }

    fun sendWhatsAppReply(messageId: String, replyText: String) {
        val success = whatsAppManager.sendRemoteInputReply(
            context = getApplication(),
            messageId = messageId,
            replyText = replyText
        )
        if (success) {
            val status = "Reply sent to WhatsApp: \"$replyText\""
            _sttPipelineStatus.value = status
            if (elevenLabsKeyManager.hasValidApiKey()) {
                testElevenLabsVoice(text = "WhatsApp reply sent successfully.") {}
            } else {
                fallbackAndroidTts("WhatsApp reply sent successfully.")
            }
        }
    }

    fun startDictatingWhatsAppReply(messageId: String, preferredLang: String = "hi-IN") {
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _sttPipelineStatus.value = "RECORD_AUDIO permission missing. Please grant microphone access."
            return
        }
        val targetMsg = whatsAppMessages.value.find { it.id == messageId } ?: return
        audioManagerHelper.playListeningPromptBeep()
        _sttPipelineStatus.value = "Dictating WhatsApp reply to ${targetMsg.sender}..."

        maxSttManager.onSpeechRecognizedListener = { spokenText ->
            sendWhatsAppReply(messageId, spokenText)
            // Restore default STT handler
            maxSttManager.onSpeechRecognizedListener = { text -> processSttUserQuery(text) }
        }
        maxSttManager.startListening(preferredLanguage = preferredLang)
    }

    fun generateAiWhatsAppReplyAndSend(messageId: String) {
        val targetMsg = whatsAppMessages.value.find { it.id == messageId } ?: return
        viewModelScope.launch {
            _sttPipelineStatus.value = "Generating AI response for ${targetMsg.sender}..."
            val aiResult = geminiService.generateSmsReply(
                senderNumber = targetMsg.sender,
                incomingMessage = targetMsg.text,
                settings = settings.value
            )
            when (aiResult) {
                is AiResult.Success -> {
                    sendWhatsAppReply(messageId, aiResult.text)
                }
                is AiResult.Error -> {
                    _sttPipelineStatus.value = "AI WhatsApp Reply Error: ${aiResult.message}"
                }
            }
        }
    }

    // Hardware Toggle Control Methods
    fun toggleFlashlight(enable: Boolean) {
        val result = deviceToggleManager.setFlashlightEnabled(enable)
        val msg = if (result is com.example.toggle.ToggleResult.Success) result.message else (result as com.example.toggle.ToggleResult.Error).message
        _sttPipelineStatus.value = msg
    }

    fun setSoundMode(mode: com.example.toggle.SoundMode) {
        val result = deviceToggleManager.setSoundMode(mode)
        val msg = if (result is com.example.toggle.ToggleResult.Success) result.message else (result as com.example.toggle.ToggleResult.Error).message
        _sttPipelineStatus.value = msg
    }

    fun toggleWifi(enable: Boolean) {
        val result = deviceToggleManager.setWifiEnabled(enable)
        val msg = if (result is com.example.toggle.ToggleResult.Success) result.message else (result as com.example.toggle.ToggleResult.Error).message
        _sttPipelineStatus.value = msg
    }

    fun setScreenBrightness(percent: Int) {
        val result = deviceToggleManager.setScreenBrightness(percent)
        val msg = if (result is com.example.toggle.ToggleResult.Success) result.message else (result as com.example.toggle.ToggleResult.Error).message
        _sttPipelineStatus.value = msg
    }

    fun canWriteSystemSettings(): Boolean {
        return deviceToggleManager.canWriteSystemSettings()
    }

    fun openWriteSettingsPermission(context: android.content.Context) {
        deviceToggleManager.openWriteSettingsPermission(context)
    }

    // Accessibility Control Methods
    fun performScrollDown() {
        val service = com.example.accessibility.MaxAccessibilityService.instance
        if (service != null) {
            service.performScrollDown()
        } else {
            _sttPipelineStatus.value = "Accessibility service is not enabled."
        }
    }

    fun performScrollUp() {
        val service = com.example.accessibility.MaxAccessibilityService.instance
        if (service != null) {
            service.performScrollUp()
        } else {
            _sttPipelineStatus.value = "Accessibility service is not enabled."
        }
    }

    fun startAutoScroll(intervalMs: Long = 2000L) {
        val service = com.example.accessibility.MaxAccessibilityService.instance
        if (service != null) {
            service.startAutoScroll(intervalMs)
        } else {
            _sttPipelineStatus.value = "Accessibility service is not enabled."
        }
    }

    fun stopAutoScroll() {
        val service = com.example.accessibility.MaxAccessibilityService.instance
        if (service != null) {
            service.stopAutoScroll()
        } else {
            _sttPipelineStatus.value = "Accessibility service is not enabled."
        }
    }

    fun autoTypeInFocusedField(textToType: String) {
        val service = com.example.accessibility.MaxAccessibilityService.instance
        if (service != null) {
            service.autoTypeInFocusedField(textToType)
        } else {
            _sttPipelineStatus.value = "Accessibility service is not enabled."
        }
    }

    fun openAccessibilitySettings(context: android.content.Context) {
        val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    fun testTtsVoice(sampleName: String = "John Doe") {
        val s = settings.value
        audioManagerHelper.requestVoiceAssistantAudioFocus()
        announcer.announceCaller(
            callerNameOrNumber = sampleName,
            template = s.announcementTemplate,
            speechRate = s.ttsSpeechRate,
            speechPitch = s.ttsPitch,
            repeatCount = s.announcementRepeatCount,
            onDone = {
                audioManagerHelper.releaseVoiceAssistantAudioFocus()
            }
        )
    }

    fun stopTtsVoice() {
        announcer.stop()
        audioManagerHelper.releaseVoiceAssistantAudioFocus()
    }

    /**
     * Triggers real mic voice recognition directly to test "Accept" / "Reject" detection.
     */
    fun startLiveVoiceRecognitionTest() {
        val s = settings.value
        audioManagerHelper.playListeningPromptBeep()
        voiceDetector.startListening(
            timeoutSeconds = 10,
            customAcceptKeywords = s.acceptKeywords,
            customRejectKeywords = s.rejectKeywords
        )
    }

    fun stopLiveVoiceRecognitionTest() {
        voiceDetector.stopListening()
    }

    private fun handleRecognizedVoiceCommand(command: VoiceCommand, rawText: String) {
        val sim = _incomingCallSimState.value
        if (sim.isRinging) {
            when (command) {
                VoiceCommand.ACCEPT -> {
                    audioManagerHelper.playCommandRecognizedBeep()
                    _incomingCallSimState.value = sim.copy(
                        phase = "ACCEPTED",
                        recognizedCommand = rawText,
                        statusText = "Call ACCEPTED via voice command ('$rawText')"
                    )
                    viewModelScope.launch(Dispatchers.IO) {
                        dao.insertEvent(
                            AutoResponderEvent(
                                eventType = "VOICE_CALL_ACCEPTED",
                                senderOrNumber = "${sim.callerName} (${sim.callerNumber})",
                                incomingContent = "Voice Command: '$rawText'",
                                generatedReply = "Call answered",
                                status = "SUCCESS"
                            )
                        )
                        callLogDao.insertCallLog(
                            com.example.data.db.CallHistoryLog(
                                phoneNumber = sim.callerNumber,
                                callerName = sim.callerName,
                                actionTaken = "ACCEPTED",
                                voiceCommandUsed = rawText
                            )
                        )
                    }
                }
                VoiceCommand.REJECT -> {
                    audioManagerHelper.playCommandRecognizedBeep()
                    _incomingCallSimState.value = sim.copy(
                        phase = "REJECTED",
                        recognizedCommand = rawText,
                        statusText = "Call REJECTED via voice command ('$rawText')"
                    )
                    viewModelScope.launch(Dispatchers.IO) {
                        dao.insertEvent(
                            AutoResponderEvent(
                                eventType = "VOICE_CALL_REJECTED",
                                senderOrNumber = "${sim.callerName} (${sim.callerNumber})",
                                incomingContent = "Voice Command: '$rawText'",
                                generatedReply = "Call rejected",
                                status = "SUCCESS"
                            )
                        )
                        callLogDao.insertCallLog(
                            com.example.data.db.CallHistoryLog(
                                phoneNumber = sim.callerNumber,
                                callerName = sim.callerName,
                                actionTaken = "REJECTED",
                                voiceCommandUsed = rawText
                            )
                        )
                    }
                }
                VoiceCommand.SILENCE -> {
                    _incomingCallSimState.value = sim.copy(
                        statusText = "Ringer SILENCED via voice command ('$rawText')"
                    )
                }
                VoiceCommand.UNKNOWN -> {
                    _incomingCallSimState.value = sim.copy(
                        statusText = "Unrecognized voice command: '$rawText'. Try 'Accept' or 'Reject'."
                    )
                }
            }
        }
    }

    /**
     * Executes an end-to-end interactive simulation of an incoming call with voice announcement & speech recognition.
     */
    fun simulateIncomingCallWithVoiceAssistant(
        callerName: String = "Sarah Connor",
        callerNumber: String = "+1 555-0199"
    ) {
        viewModelScope.launch {
            val s = settings.value

            _incomingCallSimState.value = IncomingCallSimState(
                isRinging = true,
                callerName = callerName,
                callerNumber = callerNumber,
                phase = "RINGING",
                statusText = "Incoming phone call is ringing..."
            )

            delay(1000)

            // Step 1: TTS Announcement
            if (s.isCallAnnouncerEnabled) {
                _incomingCallSimState.value = _incomingCallSimState.value.copy(
                    phase = "ANNOUNCING",
                    statusText = "Announcing Caller: $callerName..."
                )
                audioManagerHelper.requestVoiceAssistantAudioFocus()

                announcer.announceCaller(
                    callerNameOrNumber = callerName,
                    template = s.announcementTemplate,
                    speechRate = s.ttsSpeechRate,
                    speechPitch = s.ttsPitch,
                    repeatCount = 1,
                    onDone = {
                        // Step 2: Speech Recognition
                        if (s.isVoiceCallControlEnabled && _incomingCallSimState.value.isRinging) {
                            _incomingCallSimState.value = _incomingCallSimState.value.copy(
                                phase = "LISTENING",
                                statusText = "Listening for your voice command (Say 'Accept' or 'Reject')..."
                            )
                            audioManagerHelper.playListeningPromptBeep()
                            voiceDetector.startListening(
                                timeoutSeconds = s.autoListenTimeoutSeconds,
                                customAcceptKeywords = s.acceptKeywords,
                                customRejectKeywords = s.rejectKeywords
                            )
                        } else {
                            audioManagerHelper.releaseVoiceAssistantAudioFocus()
                        }
                    }
                )
            } else if (s.isVoiceCallControlEnabled) {
                _incomingCallSimState.value = _incomingCallSimState.value.copy(
                    phase = "LISTENING",
                    statusText = "Listening for your voice command (Say 'Accept' or 'Reject')..."
                )
                audioManagerHelper.playListeningPromptBeep()
                voiceDetector.startListening(
                    timeoutSeconds = s.autoListenTimeoutSeconds,
                    customAcceptKeywords = s.acceptKeywords,
                    customRejectKeywords = s.rejectKeywords
                )
            }
        }
    }

    fun endIncomingCallSimulation() {
        announcer.stop()
        voiceDetector.stopListening()
        audioManagerHelper.releaseVoiceAssistantAudioFocus()
        _incomingCallSimState.value = IncomingCallSimState()
    }

    /**
     * Executes an end-to-end simulated test of an incoming SMS with Gemini.
     */
    fun simulateSmsReceived(
        senderNumber: String,
        messageBody: String,
        dispatchRealSms: Boolean = false
    ) {
        viewModelScope.launch {
            _simulationState.value = SimulationState(isRunning = true, currentStep = "Processing with Gemini...")
            val currentSettings = settings.value

            val eventId = dao.insertEvent(
                AutoResponderEvent(
                    eventType = "SMS_RECEIVED",
                    senderOrNumber = senderNumber,
                    incomingContent = messageBody,
                    status = "PENDING"
                )
            )

            val aiResult = geminiService.generateSmsReply(
                senderNumber = senderNumber,
                incomingMessage = messageBody,
                settings = currentSettings
            )

            when (aiResult) {
                is AiResult.Success -> {
                    val reply = aiResult.text
                    var status = "SUCCESS"
                    var errorMsg: String? = null

                    if (dispatchRealSms) {
                        val sendResult = smsSender.sendSms(
                            destinationNumber = senderNumber,
                            messageText = reply,
                            cooldownMinutes = 0,
                            bypassCooldown = true
                        )
                        if (sendResult is SendSmsResult.Failure) {
                            status = "FAILED"
                            errorMsg = "SMS Dispatch: ${sendResult.reason}"
                        }
                    }

                    dao.updateEvent(
                        AutoResponderEvent(
                            id = eventId,
                            eventType = "SMS_RECEIVED",
                            senderOrNumber = senderNumber,
                            incomingContent = messageBody,
                            generatedReply = reply,
                            status = status,
                            errorMessage = errorMsg
                        )
                    )

                    _simulationState.value = SimulationState(
                        isRunning = false,
                        simulatedOutput = reply,
                        error = errorMsg
                    )
                }
                is AiResult.Error -> {
                    dao.updateEvent(
                        AutoResponderEvent(
                            id = eventId,
                            eventType = "SMS_RECEIVED",
                            senderOrNumber = senderNumber,
                            incomingContent = messageBody,
                            generatedReply = null,
                            status = "FAILED",
                            errorMessage = aiResult.message
                        )
                    )

                    _simulationState.value = SimulationState(
                        isRunning = false,
                        simulatedOutput = null,
                        error = aiResult.message
                    )
                }
            }
        }
    }

    /**
     * Executes an end-to-end simulated test of a missed phone call.
     */
    fun simulateMissedCall(
        callerNumber: String,
        dispatchRealSms: Boolean = false
    ) {
        viewModelScope.launch {
            _simulationState.value = SimulationState(isRunning = true, currentStep = "Generating missed call reply...")
            val currentSettings = settings.value

            val eventId = dao.insertEvent(
                AutoResponderEvent(
                    eventType = "MISSED_CALL",
                    senderOrNumber = callerNumber,
                    incomingContent = "Simulated missed call from $callerNumber",
                    status = "PENDING"
                )
            )

            val aiResult = geminiService.generateMissedCallReply(
                callerNumber = callerNumber,
                settings = currentSettings
            )

            when (aiResult) {
                is AiResult.Success -> {
                    val reply = aiResult.text
                    var status = "SUCCESS"
                    var errorMsg: String? = null

                    if (dispatchRealSms) {
                        val sendResult = smsSender.sendSms(
                            destinationNumber = callerNumber,
                            messageText = reply,
                            cooldownMinutes = 0,
                            bypassCooldown = true
                        )
                        if (sendResult is SendSmsResult.Failure) {
                            status = "FAILED"
                            errorMsg = "SMS Dispatch: ${sendResult.reason}"
                        }
                    }

                    dao.updateEvent(
                        AutoResponderEvent(
                            id = eventId,
                            eventType = "MISSED_CALL",
                            senderOrNumber = callerNumber,
                            incomingContent = "Simulated missed call from $callerNumber",
                            generatedReply = reply,
                            status = status,
                            errorMessage = errorMsg
                        )
                    )

                    _simulationState.value = SimulationState(
                        isRunning = false,
                        simulatedOutput = reply,
                        error = errorMsg
                    )
                }
                is AiResult.Error -> {
                    dao.updateEvent(
                        AutoResponderEvent(
                            id = eventId,
                            eventType = "MISSED_CALL",
                            senderOrNumber = callerNumber,
                            incomingContent = "Simulated missed call from $callerNumber",
                            generatedReply = null,
                            status = "FAILED",
                            errorMessage = aiResult.message
                        )
                    )

                    _simulationState.value = SimulationState(
                        isRunning = false,
                        simulatedOutput = null,
                        error = aiResult.message
                    )
                }
            }
        }
    }

    // Emergency SOS & Location Helpers
    fun fetchCurrentLocation() {
        emergencySosManager.fetchCurrentLocation()
    }

    fun dispatchSosAlert(customNote: String = "") {
        emergencySosManager.dispatchSosAlert(customNote)
    }

    fun addSosContact(number: String) {
        emergencySosManager.addSosContact(number)
    }

    fun removeSosContact(number: String) {
        emergencySosManager.removeSosContact(number)
    }

    // Camera Helpers
    fun switchCamera() {
        maxCameraManager.switchCamera()
    }

    fun takeCameraPhoto(onComplete: ((android.net.Uri?) -> Unit)? = null) {
        maxCameraManager.takePhoto(onComplete)
    }

    fun setCameraZoom(ratio: Float) {
        maxCameraManager.setZoomRatio(ratio)
    }

    fun zoomInCamera() {
        maxCameraManager.zoomIn()
    }

    fun zoomOutCamera() {
        maxCameraManager.zoomOut()
    }

    fun startVideoRecording() {
        maxCameraManager.startVideoRecording()
    }

    fun stopVideoRecording() {
        maxCameraManager.stopVideoRecording()
    }

    // Device Admin Security Helpers
    fun isDeviceAdminActive(): Boolean {
        val dpm = getApplication<AutoResponderApp>().getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val adminComponent = android.content.ComponentName(getApplication(), com.example.security.MaxDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(adminComponent)
    }

    fun getDeviceAdminEnableIntent(): android.content.Intent {
        val adminComponent = android.content.ComponentName(getApplication(), com.example.security.MaxDeviceAdminReceiver::class.java)
        return android.content.Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Activate MAX Assistant Device Admin protection to detect failed unlock attempts, play intruder alarms, and capture background intruder photos.")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun triggerTestSecurityAlarm(reason: String = "Manual User Test") {
        intruderSecurityManager.triggerLoudAlarm(reason)
    }

    fun stopSecurityAlarm() {
        intruderSecurityManager.stopLoudAlarm()
    }

    fun triggerTestSilentCapture(reason: String = "Manual User Test") {
        intruderSecurityManager.captureSilentFrontCameraSnapshot(reason)
    }

    fun setAntiTheftSirenEnabled(enabled: Boolean) {
        settingsRepo.setAntiTheftSirenEnabled(enabled)
    }

    fun setIntruderSelfieCaptureEnabled(enabled: Boolean) {
        settingsRepo.setIntruderSelfieCaptureEnabled(enabled)
    }

    fun setMotionDetectionAlarmEnabled(enabled: Boolean) {
        settingsRepo.setMotionDetectionAlarmEnabled(enabled)
        intruderSecurityManager.setMotionDetectionArmed(enabled)
    }

    fun setFailedUnlockThreshold(threshold: Int) {
        settingsRepo.setFailedUnlockThreshold(threshold)
    }

    fun deleteIntruderImage(file: java.io.File) {
        intruderSecurityManager.deleteIntruderImage(file)
    }

    fun clearIntruderLogs() {
        intruderSecurityManager.clearIntruderLogs()
    }

    // Speaker Verification Helpers
    fun setSpeakerThreshold(threshold: Float) {
        speakerVerificationManager.setTargetThreshold(threshold)
    }

    fun enrollVoiceProfile(pcmAudio: FloatArray): Boolean {
        return speakerVerificationManager.enrollVoiceProfile(pcmAudio)
    }

    fun deleteVoiceProfile() {
        speakerVerificationManager.deleteVoiceProfile()
    }

    // Real-Time Barge-In & WebSocket Methods
    fun setBargeInEnabled(enabled: Boolean) {
        settingsRepo.setBargeInEnabled(enabled)
        realtimeBargeInManager.setBargeInEnabled(enabled)
    }

    fun setBargeInSensitivity(sensitivity: Float) {
        settingsRepo.setBargeInSensitivity(sensitivity)
        realtimeBargeInManager.setSensitivity(sensitivity)
    }

    fun setBargeInMode(mode: String) {
        settingsRepo.setBargeInMode(mode)
    }

    fun setRealtimeWebSocketEnabled(enabled: Boolean) {
        settingsRepo.setRealtimeWebSocketEnabled(enabled)
        if (enabled) {
            maxRealtimeWebSocketManager.connect()
        } else {
            maxRealtimeWebSocketManager.disconnect()
        }
    }

    fun setBargeInSoundFeedback(enabled: Boolean) {
        settingsRepo.setBargeInSoundFeedback(enabled)
    }

    fun simulateBargeInInterrupt() {
        realtimeBargeInManager.simulateBargeIn()
        _isVoiceOrbSpeaking.value = false
        _isVoiceOrbListening.value = true
        _voiceOrbStatus.value = "⚡ Barge-in Test: Interrupted! Listening to new speech..."
        _sttPipelineStatus.value = "⚡ Barge-in simulated: Speech interrupted."
        startVoiceOrbListening()
    }

    /**
     * Previews telephone caller announcement with natural digit grouping and TtsSpan telephone styling.
     */
    fun testPhoneNumberAnnouncement(sampleNumber: String = "+1 (800) 555-0199") {
        val s = settings.value
        audioManagerHelper.requestVoiceAssistantAudioFocus()
        announcer.announceCaller(
            callerNameOrNumber = sampleNumber,
            template = s.announcementTemplate,
            speechRate = s.ttsSpeechRate,
            speechPitch = s.ttsPitch,
            repeatCount = s.announcementRepeatCount,
            onDone = {
                audioManagerHelper.releaseVoiceAssistantAudioFocus()
            }
        )
    }
}

data class ToggleFeedback(
    val title: String,
    val isEnabled: Boolean,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

