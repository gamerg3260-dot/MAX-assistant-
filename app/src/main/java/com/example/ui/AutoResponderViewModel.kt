package com.example.ui

import android.app.Application
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
import com.example.voice.VoiceCommand
import com.example.voice.VoiceDetectorState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // Service & Voice reactive state flows
    val isServiceRunning: StateFlow<Boolean> = MaxAssistantForegroundService.isServiceRunning
    val liveCallStatus: StateFlow<String> = MaxAssistantForegroundService.liveCallStatus
    val liveVoiceState: StateFlow<String> = MaxAssistantForegroundService.liveVoiceState
    val voiceDetectorState: StateFlow<VoiceDetectorState> = voiceDetector.detectorState
    val rmsDbLevel: StateFlow<Float> = voiceDetector.rmsDbLevel
    val isTtsSpeaking: StateFlow<Boolean> = announcer.isSpeaking

    init {
        // Setup in-app voice detector listeners for test workbench
        voiceDetector.onCommandListener = { cmd, raw ->
            viewModelScope.launch {
                handleRecognizedVoiceCommand(cmd, raw)
            }
        }
    }

    fun refreshPermissions() {
        _missingPermissions.value = PermissionHelper.getMissingPermissions(getApplication())
    }

    fun toggleMaxAssistant(enabled: Boolean) {
        settingsRepo.setMaxAssistantEnabled(enabled)
        if (enabled) {
            MaxAssistantForegroundService.startService(getApplication())
        } else {
            MaxAssistantForegroundService.stopService(getApplication())
        }
    }

    fun toggleCallAnnouncer(enabled: Boolean) {
        settingsRepo.setCallAnnouncerEnabled(enabled)
    }

    fun toggleVoiceCallControl(enabled: Boolean) {
        settingsRepo.setVoiceCallControlEnabled(enabled)
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
    }

    // Blocklist & Spam Management
    fun toggleBlocklist(enabled: Boolean) {
        settingsRepo.setBlocklistEnabled(enabled)
    }

    fun toggleAutoRejectSpam(enabled: Boolean) {
        settingsRepo.setAutoRejectSpam(enabled)
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

    fun saveApiKey(newKey: String) {
        SecureKeyManager.saveApiKey(getApplication(), newKey)
        _apiKeyText.value = SecureKeyManager.getApiKey(getApplication())
    }

    fun clearApiKey() {
        SecureKeyManager.clearCustomApiKey(getApplication())
        _apiKeyText.value = SecureKeyManager.getApiKey(getApplication())
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
     * Previews Text-To-Speech announcement for testing voice pitch, rate, and template.
     */
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
}
