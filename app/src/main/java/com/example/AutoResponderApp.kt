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
    }

    companion object {
        lateinit var instance: AutoResponderApp
            private set
    }
}

