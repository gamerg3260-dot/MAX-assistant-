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

    lateinit var swaraTtsService: com.example.voice.SwaraTtsService
        private set

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

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        settingsRepository = AppSettingsRepository.getInstance(this)
        geminiService = GeminiAutoResponderService(this)
        smsSender = SmsSender(this)
        callController = CallController(this)
        callAnnouncer = CallAnnouncer(this)
        voiceCommandDetector = VoiceCommandDetector(this)
        audioManagerHelper = CallVoiceAudioManager(this)
        elevenLabsKeyManager = com.example.voice.ElevenLabsKeyManager(this)
        elevenLabsTtsService = com.example.voice.ElevenLabsTtsService(this)
        swaraTtsService = com.example.voice.SwaraTtsService(this)
        maxSttManager = com.example.voice.MaxSttManager(this)
        deviceToggleManager = com.example.toggle.DeviceToggleManager(this)
        emergencySosManager = com.example.sos.EmergencySosManager.getInstance(this)
        maxCameraManager = com.example.camera.MaxCameraManager.getInstance(this)
        appLauncherManager = com.example.launcher.AppLauncherManager(this)
        openWakeWordDetector = com.example.voice.OpenWakeWordDetector(this)
        intruderSecurityManager = com.example.security.IntruderSecurityManager.getInstance(this)
        speakerVerificationManager = com.example.biometrics.SpeakerVerificationManager.getInstance(this)
    }

    companion object {
        lateinit var instance: AutoResponderApp
            private set
    }
}

