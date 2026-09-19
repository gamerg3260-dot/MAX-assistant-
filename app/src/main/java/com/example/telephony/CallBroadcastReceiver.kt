package com.example.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.example.AutoResponderApp
import com.example.ai.AiResult
import com.example.data.db.AutoResponderEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver listening for READ_PHONE_STATE and ACTION_PHONE_STATE_CHANGED.
 * Accurately detects phone state transitions (RINGING, OFFHOOK, IDLE) and caller ID,
 * identifying Missed Calls to generate automated AI SMS notifications.
 */
class CallBroadcastReceiver : BroadcastReceiver() {
    private val tag = "CallBroadcastReceiver"

    companion object {
        // State tracking across broadcast receiver invocations
        private var lastState = TelephonyManager.EXTRA_STATE_IDLE
        private var isIncomingRinging = false
        private var isCallAnswered = false
        private var savedIncomingNumber: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            return
        }

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        @Suppress("DEPRECATION")
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        if (!incomingNumber.isNullOrEmpty()) {
            savedIncomingNumber = incomingNumber
        }

        Log.d(tag, "Phone state changed: $stateStr | Incoming number: $incomingNumber")

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                isIncomingRinging = true
                isCallAnswered = false
                lastState = TelephonyManager.EXTRA_STATE_RINGING

                val number = savedIncomingNumber ?: incomingNumber
                Log.d(tag, "Incoming call ringing detected: $number")

                val app = context.applicationContext as? AutoResponderApp ?: AutoResponderApp.instance
                val settings = app.settingsRepository.settings.value

                // 1. If foreground service is running, notify it with the incoming number
                val service = com.example.service.MaxAssistantForegroundService.currentServiceInstance
                if (service != null) {
                    service.onExternalIncomingCallReceived(number)
                } else if (settings.isCallAnnouncerEnabled) {
                    // 2. Announce incoming caller using CallAnnouncer (resolving contact or reading number aloud)
                    val callerNameOrFormattedNum = com.example.voice.ContactResolver.resolveCallerName(context, number)
                    Log.i(tag, "Announcing incoming caller via CallAnnouncer: $callerNameOrFormattedNum")
                    app.callAnnouncer.announceCaller(
                        callerNameOrNumber = callerNameOrFormattedNum,
                        template = settings.announcementTemplate,
                        speechRate = settings.ttsSpeechRate,
                        speechPitch = settings.ttsPitch,
                        repeatCount = settings.announcementRepeatCount
                    )
                }
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                val app = context.applicationContext as? AutoResponderApp ?: AutoResponderApp.instance
                app.callAnnouncer.stop()
                if (isIncomingRinging) {
                    isCallAnswered = true
                    Log.d(tag, "Incoming call answered.")
                }
                lastState = TelephonyManager.EXTRA_STATE_OFFHOOK
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                val app = context.applicationContext as? AutoResponderApp ?: AutoResponderApp.instance
                app.callAnnouncer.stop()
                val caller = savedIncomingNumber ?: incomingNumber ?: "Unknown Caller"

                if (isIncomingRinging && !isCallAnswered) {
                    // This was a Missed Call!
                    Log.i(tag, "Missed call detected from: $caller")
                    handleMissedCall(context, caller)
                }

                // Reset state machine
                isIncomingRinging = false
                isCallAnswered = false
                savedIncomingNumber = null
                lastState = TelephonyManager.EXTRA_STATE_IDLE
            }
        }
    }

    private fun handleMissedCall(context: Context, callerNumber: String) {
        val pendingResult = goAsync()
        val app = context.applicationContext as? AutoResponderApp ?: AutoResponderApp.instance

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = app.settingsRepository.settings.value

                // Check if auto-responder and Call handling are enabled
                if (!settings.isAutoResponderEnabled || !settings.isCallAutoReplyEnabled) {
                    app.database.autoResponderDao().insertEvent(
                        AutoResponderEvent(
                            eventType = "MISSED_CALL",
                            senderOrNumber = callerNumber,
                            incomingContent = "Missed incoming call",
                            generatedReply = null,
                            status = "SKIPPED",
                            errorMessage = if (!settings.isAutoResponderEnabled) "Auto-responder disabled" else "Call auto-reply disabled"
                        )
                    )
                    return@launch
                }

                val eventId = app.database.autoResponderDao().insertEvent(
                    AutoResponderEvent(
                        eventType = "MISSED_CALL",
                        senderOrNumber = callerNumber,
                        incomingContent = "Missed incoming call from $callerNumber",
                        generatedReply = null,
                        status = "PENDING",
                        errorMessage = null
                    )
                )

                // Skip sending SMS if the caller number is unknown/private
                if (callerNumber.isBlank() || callerNumber.equals("Unknown Caller", ignoreCase = true)) {
                    app.database.autoResponderDao().updateEvent(
                        AutoResponderEvent(
                            id = eventId,
                            eventType = "MISSED_CALL",
                            senderOrNumber = callerNumber,
                            incomingContent = "Missed incoming call",
                            generatedReply = null,
                            status = "SKIPPED",
                            errorMessage = "Caller number is unknown/private"
                        )
                    )
                    return@launch
                }

                // 1. Generate Context-Aware Missed Call Reply via Gemini API
                val aiResult = app.geminiService.generateMissedCallReply(
                    callerNumber = callerNumber,
                    settings = settings
                )

                when (aiResult) {
                    is AiResult.Success -> {
                        val replyText = aiResult.text

                        // 2. Transmit generated SMS response to caller
                        val sendResult = app.smsSender.sendSms(
                            destinationNumber = callerNumber,
                            messageText = replyText,
                            cooldownMinutes = settings.antiSpamCooldownMinutes
                        )

                        when (sendResult) {
                            is SendSmsResult.Success -> {
                                app.database.autoResponderDao().updateEvent(
                                    AutoResponderEvent(
                                        id = eventId,
                                        eventType = "MISSED_CALL",
                                        senderOrNumber = callerNumber,
                                        incomingContent = "Missed incoming call",
                                        generatedReply = replyText,
                                        status = "SUCCESS",
                                        errorMessage = null
                                    )
                                )
                                Log.i(tag, "Successfully auto-replied to missed call from $callerNumber")
                            }
                            is SendSmsResult.Failure -> {
                                app.database.autoResponderDao().updateEvent(
                                    AutoResponderEvent(
                                        id = eventId,
                                        eventType = "MISSED_CALL",
                                        senderOrNumber = callerNumber,
                                        incomingContent = "Missed incoming call",
                                        generatedReply = replyText,
                                        status = "FAILED",
                                        errorMessage = "SMS Send Error: ${sendResult.reason}"
                                    )
                                )
                                Log.w(tag, "Failed to send missed call auto-reply: ${sendResult.reason}")
                            }
                        }
                    }
                    is AiResult.Error -> {
                        app.database.autoResponderDao().updateEvent(
                            AutoResponderEvent(
                                id = eventId,
                                eventType = "MISSED_CALL",
                                senderOrNumber = callerNumber,
                                incomingContent = "Missed incoming call",
                                generatedReply = null,
                                status = "FAILED",
                                errorMessage = aiResult.message
                            )
                        )
                        Log.e(tag, "Gemini failed to generate missed call reply: ${aiResult.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error handling missed call", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
