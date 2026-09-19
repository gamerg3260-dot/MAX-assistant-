package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.AutoResponderApp
import com.example.MainActivity
import com.example.R
import com.example.data.db.AutoResponderEvent
import com.example.voice.CallAnnouncer
import com.example.voice.CallVoiceAudioManager
import com.example.voice.ContactResolver
import com.example.voice.VoiceCommand
import com.example.voice.VoiceCommandDetector
import com.example.voice.VoiceDetectorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Background Foreground Service that monitors telephony call state, announces caller ID via Text-To-Speech,
 * and activates real-time Speech Recognition to answer or reject calls by voice.
 */
class MaxAssistantForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var telephonyManager: TelephonyManager

    private var telephonyCallback: Any? = null
    private var legacyPhoneStateListener: PhoneStateListener? = null

    private lateinit var announcer: CallAnnouncer
    private lateinit var voiceDetector: VoiceCommandDetector
    private lateinit var audioManagerHelper: CallVoiceAudioManager
    private var openWakeWordDetector: com.example.voice.OpenWakeWordDetector? = null

    private var currentRingingNumber: String? = null
    private var isCallHandled = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MaxAssistantForegroundService onCreate()")
        currentServiceInstance = this
        _isServiceRunning.value = true

        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        announcer = CallAnnouncer(this)
        voiceDetector = VoiceCommandDetector(this)
        audioManagerHelper = CallVoiceAudioManager(this)
        openWakeWordDetector = (application as? AutoResponderApp)?.openWakeWordDetector

        createNotificationChannel()
        setupVoiceCommandCallbacks()
        setupWakeWordCallbacks()
        registerCallStateListener()
        startContinuousWakeWordListening()
    }

    private fun setupWakeWordCallbacks() {
        openWakeWordDetector?.onWakeWordDetected = { wakePhrase ->
            Log.i(TAG, "OpenWakeWord multi-phrase '$wakePhrase' triggered from background service!")
            _liveVoiceState.value = "Multi-Wake-Word: $wakePhrase"
            
            // Check microphone permission before showing overlay
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                // Trigger Siri-Style floating overlay & start listening
                com.example.overlay.MaxOverlayService.startListening(this)
            } else {
                Log.w(TAG, "Cannot launch voice prompt: RECORD_AUDIO permission missing")
            }
        }

        openWakeWordDetector?.onError = { err ->
            Log.w(TAG, "OpenWakeWord background detector warning: $err")
        }
    }

    private fun startContinuousWakeWordListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Starting continuous background OpenWakeWord engine monitoring (\"Okay Max\", \"Backup Max\", \"Hey Max\")...")
            openWakeWordDetector?.startListening()
        } else {
            Log.w(TAG, "RECORD_AUDIO not granted. Background wake-word detection postponed.")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action=$action")

        if (action == ACTION_STOP_SERVICE) {
            stopForegroundService()
            return START_NOT_STICKY
        }

        startForegroundWithNotification("MAX Assistant is active and monitoring incoming calls.")
        _isServiceRunning.value = true
        return START_STICKY
    }

    private fun startForegroundWithNotification(statusText: String) {
        val notification = buildNotification(statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            }
            startForeground(NOTIFICATION_ID, notification, foregroundServiceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MaxAssistantForegroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MAX Voice Assistant")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Turn Off", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MAX Voice Assistant Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors incoming calls for voice announcements and voice commands"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun setupVoiceCommandCallbacks() {
        voiceDetector.onCommandListener = { command, rawText ->
            Log.i(TAG, "Voice command received in service: $command ('$rawText')")
            handleVoiceCommandAction(command, rawText)
        }

        voiceDetector.onErrorListener = { errorMsg ->
            Log.w(TAG, "Voice detector error in service: $errorMsg")
            _liveVoiceState.value = "Voice Error: $errorMsg"
        }

        voiceDetector.onListeningStartedListener = {
            audioManagerHelper.playListeningPromptBeep()
            _liveVoiceState.value = "Listening: Say 'Accept' or 'Reject'..."
        }

        voiceDetector.onListeningEndedListener = {
            _liveVoiceState.value = "Idle"
        }
    }

    private fun handleVoiceCommandAction(command: VoiceCommand, rawText: String) {
        val app = application as? AutoResponderApp ?: return
        val settings = app.settingsRepository.settings.value
        val caller = currentRingingNumber ?: "Unknown"
        val callerName = ContactResolver.resolveCallerName(this, caller)

        when (command) {
            VoiceCommand.ACCEPT -> {
                audioManagerHelper.playCommandRecognizedBeep()
                val result = app.callController.acceptRingingCall()
                isCallHandled = true
                _liveVoiceState.value = "Call Accepted via voice ('$rawText')"

                if (settings.autoSpeakerphoneOnAccept) {
                    audioManagerHelper.setSpeakerphoneOn(true)
                }

                serviceScope.launch(Dispatchers.IO) {
                    app.database.autoResponderDao().insertEvent(
                        AutoResponderEvent(
                            eventType = "VOICE_CALL_ACCEPTED",
                            senderOrNumber = "$callerName ($caller)",
                            incomingContent = "Voice Command: '$rawText'",
                            generatedReply = "Accepted incoming call",
                            status = "SUCCESS"
                        )
                    )
                    app.database.callLogDao().insertCallLog(
                        com.example.data.db.CallHistoryLog(
                            phoneNumber = caller,
                            callerName = callerName,
                            actionTaken = "ACCEPTED",
                            voiceCommandUsed = rawText
                        )
                    )
                }
            }
            VoiceCommand.REJECT -> {
                audioManagerHelper.playCommandRecognizedBeep()
                val result = app.callController.endCall()
                isCallHandled = true
                _liveVoiceState.value = "Call Rejected via voice ('$rawText')"

                serviceScope.launch(Dispatchers.IO) {
                    app.database.autoResponderDao().insertEvent(
                        AutoResponderEvent(
                            eventType = "VOICE_CALL_REJECTED",
                            senderOrNumber = "$callerName ($caller)",
                            incomingContent = "Voice Command: '$rawText'",
                            generatedReply = "Declined incoming call",
                            status = "SUCCESS"
                        )
                    )
                    app.database.callLogDao().insertCallLog(
                        com.example.data.db.CallHistoryLog(
                            phoneNumber = caller,
                            callerName = callerName,
                            actionTaken = "REJECTED",
                            voiceCommandUsed = rawText
                        )
                    )
                }
            }
            VoiceCommand.SILENCE -> {
                app.callController.silenceRinger()
                _liveVoiceState.value = "Ringer Silenced via voice ('$rawText')"
            }
            VoiceCommand.UNKNOWN -> {
                Log.d(TAG, "Unrecognized command phrase: '$rawText'")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerCallStateListener() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_PHONE_STATE permission not granted. Cannot attach telephony listener.")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallStateChange(state, null)
                    }
                }
                telephonyManager.registerTelephonyCallback(mainExecutor, callback)
                telephonyCallback = callback
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, incomingNumber: String?) {
                        handleCallStateChange(state, incomingNumber)
                    }
                }
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                legacyPhoneStateListener = listener
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering Telephony listener: ${e.message}", e)
        }
    }

    fun onExternalIncomingCallReceived(incomingNumber: String?) {
        handleCallStateChange(TelephonyManager.CALL_STATE_RINGING, incomingNumber)
    }

    private fun handleCallStateChange(state: Int, incomingNumber: String?) {
        val app = application as? AutoResponderApp ?: return
        val settings = app.settingsRepository.settings.value

        if (!settings.isMaxAssistantEnabled) {
            Log.d(TAG, "MAX Assistant is disabled in settings. Skipping.")
            return
        }

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                currentRingingNumber = incomingNumber ?: currentRingingNumber ?: "Unknown"
                isCallHandled = false
                val callerName = ContactResolver.resolveCallerName(this, currentRingingNumber)

                // Blocklist Auto-Reject Check
                val numToMatch = currentRingingNumber?.replace(Regex("[^0-9+]"), "") ?: ""
                val isBlocked = settings.isBlocklistEnabled && settings.blockedNumbers.any { blocked ->
                    val cleanBlocked = blocked.replace(Regex("[^0-9+]"), "")
                    cleanBlocked.isNotEmpty() && (numToMatch.endsWith(cleanBlocked) || cleanBlocked.endsWith(numToMatch))
                }

                if (isBlocked) {
                    Log.i(TAG, "Call from $currentRingingNumber blocked by Blocklist rules. Auto-rejecting.")
                    _liveCallStatus.value = "Blocked Call Auto-Rejected: $callerName"
                    _liveVoiceState.value = "Blocked Spam Call Rejecting..."
                    app.callController.endCall()
                    isCallHandled = true

                    serviceScope.launch(Dispatchers.IO) {
                        app.database.autoResponderDao().insertEvent(
                            AutoResponderEvent(
                                eventType = "CALL_BLOCKED_AUTO_REJECTED",
                                senderOrNumber = "$callerName ($currentRingingNumber)",
                                incomingContent = "Auto-rejected via Blocklist & Spam filter",
                                generatedReply = "Disconnected call automatically",
                                status = "SUCCESS"
                            )
                        )
                        app.database.callLogDao().insertCallLog(
                            com.example.data.db.CallHistoryLog(
                                phoneNumber = currentRingingNumber ?: "Unknown",
                                callerName = callerName,
                                actionTaken = "BLOCKED_AUTO_REJECTED",
                                voiceCommandUsed = "Auto Blocklist Filter"
                            )
                        )
                    }
                    return
                }

                Log.i(TAG, "Incoming call ringing from: $callerName ($currentRingingNumber)")
                _liveCallStatus.value = "Ringing: $callerName"

                startForegroundWithNotification("Incoming call from $callerName")

                // Step 1: Caller Announcement via TTS
                if (settings.isCallAnnouncerEnabled) {
                    audioManagerHelper.requestVoiceAssistantAudioFocus()
                    _liveVoiceState.value = "Announcing Caller: $callerName"

                    announcer.announceCaller(
                        callerNameOrNumber = callerName,
                        template = settings.announcementTemplate,
                        speechRate = settings.ttsSpeechRate,
                        speechPitch = settings.ttsPitch,
                        repeatCount = settings.announcementRepeatCount,
                        onDone = {
                            // Step 2: Immediate transition to Speech Recognition
                            if (settings.isVoiceCallControlEnabled && !isCallHandled) {
                                startVoiceRecognitionForIncomingCall(
                                    acceptKeys = settings.acceptKeywords,
                                    rejectKeys = settings.rejectKeywords,
                                    timeout = settings.autoListenTimeoutSeconds,
                                    isAcceptEnabled = settings.isVoiceAcceptCommandsEnabled,
                                    isRejectEnabled = settings.isVoiceRejectCommandsEnabled
                                )
                            } else {
                                audioManagerHelper.releaseVoiceAssistantAudioFocus()
                            }
                        }
                    )
                } else if (settings.isVoiceCallControlEnabled) {
                    // Start voice recognition immediately without announcement
                    audioManagerHelper.requestVoiceAssistantAudioFocus()
                    startVoiceRecognitionForIncomingCall(
                        acceptKeys = settings.acceptKeywords,
                        rejectKeys = settings.rejectKeywords,
                        timeout = settings.autoListenTimeoutSeconds,
                        isAcceptEnabled = settings.isVoiceAcceptCommandsEnabled,
                        isRejectEnabled = settings.isVoiceRejectCommandsEnabled
                    )
                }
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.d(TAG, "Call state: OFFHOOK")
                _liveCallStatus.value = "Call Connected"
                stopVoicePipelines()
                startForegroundWithNotification("Call in progress.")
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d(TAG, "Call state: IDLE")
                _liveCallStatus.value = "Idle / Standby"
                stopVoicePipelines()
                currentRingingNumber = null
                isCallHandled = false
                startForegroundWithNotification("MAX Assistant is active and monitoring incoming calls.")
            }
        }
    }

    private fun startVoiceRecognitionForIncomingCall(
        acceptKeys: String,
        rejectKeys: String,
        timeout: Int,
        isAcceptEnabled: Boolean = true,
        isRejectEnabled: Boolean = true
    ) {
        _liveVoiceState.value = "Listening: Say 'Accept' or 'Reject'..."
        voiceDetector.startListening(
            timeoutSeconds = timeout,
            customAcceptKeywords = acceptKeys,
            customRejectKeywords = rejectKeys,
            isAcceptEnabled = isAcceptEnabled,
            isRejectEnabled = isRejectEnabled
        )
    }

    private fun stopVoicePipelines() {
        announcer.stop()
        voiceDetector.stopListening()
        openWakeWordDetector?.stopListening()
        audioManagerHelper.releaseVoiceAssistantAudioFocus()
        _liveVoiceState.value = "Idle"
    }

    private fun stopForegroundService() {
        Log.d(TAG, "Stopping MaxAssistantForegroundService...")
        stopVoicePipelines()
        announcer.shutdown()
        unregisterCallStateListener()
        _isServiceRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun unregisterCallStateListener() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let {
                    telephonyManager.unregisterTelephonyCallback(it as TelephonyCallback)
                }
            } else {
                legacyPhoneStateListener?.let {
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering telephony listener: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MaxAssistantForegroundService onDestroy()")
        currentServiceInstance = null
        stopForegroundService()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val TAG = "MaxAssistantService"
        const val CHANNEL_ID = "max_assistant_voice_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_SERVICE = "com.example.service.action.START_MAX"
        const val ACTION_STOP_SERVICE = "com.example.service.action.STOP_MAX"

        var currentServiceInstance: MaxAssistantForegroundService? = null
            private set

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _liveCallStatus = MutableStateFlow("Idle / Standby")
        val liveCallStatus: StateFlow<String> = _liveCallStatus.asStateFlow()

        private val _liveVoiceState = MutableStateFlow("Idle")
        val liveVoiceState: StateFlow<String> = _liveVoiceState.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, MaxAssistantForegroundService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MaxAssistantForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
