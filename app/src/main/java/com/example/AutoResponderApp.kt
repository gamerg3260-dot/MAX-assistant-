package com.example

import android.app.Application
import com.example.ai.GeminiAutoResponderService
import com.example.data.db.AppDatabase
import com.example.data.repository.AppSettingsRepository
import com.example.telephony.CallController
import com.example.telephony.SmsSender
import com.example.voice.CallAnnouncer
import com.example.voice.CallVoiceAudioManager
import com.example.voice.VoiceCommandDetector

class AutoResponderApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var settingsRepository: AppSettingsRepository
        private set

    lateinit var geminiService: GeminiAutoResponderService
        private set

    lateinit var smsSender: SmsSender
        private set

    lateinit var callController: CallController
        private set

    lateinit var callAnnouncer: CallAnnouncer
        private set

    lateinit var voiceCommandDetector: VoiceCommandDetector
        private set

    lateinit var audioManagerHelper: CallVoiceAudioManager
        private set

    lateinit var elevenLabsTtsService: com.example.voice.ElevenLabsTtsService
        private set

    lateinit var elevenLabsKeyManager: com.example.voice.ElevenLabsKeyManager
        private set

    lateinit var maxNativeTTS: com.example.voice.MAXNativeTTS
        private set

    val swaraTtsService: com.example.voice.MAXNativeTTS
        get() = maxNativeTTS

    lateinit var maxSttManager: com.example.voice.MaxSttManager
        private set

    lateinit var deviceToggleManager: com.example.toggle.DeviceToggleManager
        private set

    lateinit var emergencySosManager: com.example.sos.EmergencySosManager
        private set

    lateinit var maxCameraManager: com.example.camera.MaxCameraManager
        private set

    lateinit var appLauncherManager: com.example.launcher.AppLauncherManager
        private set

    lateinit var openWakeWordDetector: com.example.voice.OpenWakeWordDetector
        private set

    lateinit var intruderSecurityManager: com.example.security.IntruderSecurityManager
        private set

    lateinit var speakerVerificationManager: com.example.biometrics.SpeakerVerificationManager
        private set

    lateinit var conversationContextManager: com.example.ai.ConversationContextManager
        private set

    lateinit var directCallManager: com.example.telephony.DirectCallManager
        private set

    lateinit var realtimeAudioPlayer: com.example.voice.RealtimeAudioPlayer
        private set

    lateinit var realtimeBargeInManager: com.example.voice.RealtimeBargeInManager
        private set

    lateinit var maxRealtimeWebSocketManager: com.example.ai.MaxRealtimeWebSocketManager
        private set

    lateinit var localVoiceCommandRouter: com.example.voice.LocalVoiceCommandRouter
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        settingsRepository = AppSettingsRepository.getInstance(this)
        conversationContextManager = com.example.ai.ConversationContextManager.getInstance()
        directCallManager = com.example.telephony.DirectCallManager(this)
        geminiService = GeminiAutoResponderService(this)
        smsSender = SmsSender(this)
        callController = CallController(this)
        callAnnouncer = CallAnnouncer(this)
        voiceCommandDetector = VoiceCommandDetector(this)
        audioManagerHelper = CallVoiceAudioManager(this)
        elevenLabsKeyManager = com.example.voice.ElevenLabsKeyManager(this)
        elevenLabsTtsService = com.example.voice.ElevenLabsTtsService(this)
        maxNativeTTS = com.example.voice.MAXNativeTTS(this)
        maxSttManager = com.example.voice.MaxSttManager(this)
        realtimeAudioPlayer = com.example.voice.RealtimeAudioPlayer()
        realtimeBargeInManager = com.example.voice.RealtimeBargeInManager(
            context = this,
            realtimeAudioPlayer = realtimeAudioPlayer,
            maxNativeTTS = maxNativeTTS,
            elevenLabsTtsService = elevenLabsTtsService,
            callAnnouncer = callAnnouncer
        )
        maxRealtimeWebSocketManager = com.example.ai.MaxRealtimeWebSocketManager(
            context = this,
            realtimeAudioPlayer = realtimeAudioPlayer
        )
        deviceToggleManager = com.example.toggle.DeviceToggleManager(this)
        emergencySosManager = com.example.sos.EmergencySosManager.getInstance(this)
        maxCameraManager = com.example.camera.MaxCameraManager.getInstance(this)
        appLauncherManager = com.example.launcher.AppLauncherManager(this)
        openWakeWordDetector = com.example.voice.OpenWakeWordDetector(this)
        intruderSecurityManager = com.example.security.IntruderSecurityManager.getInstance(this)
        speakerVerificationManager = com.example.biometrics.SpeakerVerificationManager.getInstance(this)
        localVoiceCommandRouter = com.example.voice.LocalVoiceCommandRouter(
            context = this,
            directCallManager = directCallManager,
            appLauncherManager = appLauncherManager,
            deviceToggleManager = deviceToggleManager,
            emergencySosManager = emergencySosManager,
            maxCameraManager = maxCameraManager
        )
    }

    companion object {
        lateinit var instance: AutoResponderApp
            private set
    }
}

