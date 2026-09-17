package com.example.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.AutoResponderApp
import com.example.ai.AiResult
import com.example.data.db.AutoResponderEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver listening for Telephony.Sms.Intents.SMS_RECEIVED_ACTION.
 * Reads incoming SMS messages, invokes Gemini API to generate context-aware replies,
 * and automatically dispatches the response back via SmsManager.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {
    private val tag = "SmsBroadcastReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            Log.w(tag, "SMS_RECEIVED intent received with no messages.")
            return
        }

        // Group message parts by sender address
        val sender = messages[0].originatingAddress ?: "Unknown"
        val fullMessageBody = messages.joinToString(separator = "") { it.messageBody ?: "" }

        Log.d(tag, "Incoming SMS received from $sender: $fullMessageBody")

        // Use goAsync() for background AI processing and SMS transmission
        val pendingResult = goAsync()
        val app = context.applicationContext as? AutoResponderApp ?: AutoResponderApp.instance

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = app.settingsRepository.settings.value

                // Check if auto-responder and SMS handling are enabled
                if (!settings.isAutoResponderEnabled || !settings.isSmsAutoReplyEnabled) {
                    app.database.autoResponderDao().insertEvent(
                        AutoResponderEvent(
                            eventType = "SMS_RECEIVED",
                            senderOrNumber = sender,
                            incomingContent = fullMessageBody,
                            generatedReply = null,
                            status = "SKIPPED",
                            errorMessage = if (!settings.isAutoResponderEnabled) "Auto-responder disabled" else "SMS auto-reply disabled"
                        )
                    )
                    return@launch
                }

                // Initial pending log entry
                val eventId = app.database.autoResponderDao().insertEvent(
                    AutoResponderEvent(
                        eventType = "SMS_RECEIVED",
                        senderOrNumber = sender,
                        incomingContent = fullMessageBody,
                        generatedReply = null,
                        status = "PENDING",
                        errorMessage = null
                    )
                )

                // 1. Generate Context-Aware Reply via Gemini API
                val aiResult = app.geminiService.generateSmsReply(
                    senderNumber = sender,
                    incomingMessage = fullMessageBody,
                    settings = settings
                )

                when (aiResult) {
                    is AiResult.Success -> {
                        val replyText = aiResult.text

                        // 2. Transmit generated response back to sender
                        val sendResult = app.smsSender.sendSms(
                            destinationNumber = sender,
                            messageText = replyText,
                            cooldownMinutes = settings.antiSpamCooldownMinutes
                        )

                        when (sendResult) {
                            is SendSmsResult.Success -> {
                                app.database.autoResponderDao().updateEvent(
                                    AutoResponderEvent(
                                        id = eventId,
                                        eventType = "SMS_RECEIVED",
                                        senderOrNumber = sender,
                                        incomingContent = fullMessageBody,
                                        generatedReply = replyText,
                                        status = "SUCCESS",
                                        errorMessage = null
                                    )
                                )
                                Log.i(tag, "Successfully auto-replied to SMS from $sender")
                            }
                            is SendSmsResult.Failure -> {
                                app.database.autoResponderDao().updateEvent(
                                    AutoResponderEvent(
                                        id = eventId,
                                        eventType = "SMS_RECEIVED",
                                        senderOrNumber = sender,
                                        incomingContent = fullMessageBody,
                                        generatedReply = replyText,
                                        status = "FAILED",
                                        errorMessage = "SMS Send Error: ${sendResult.reason}"
                                    )
                                )
                                Log.w(tag, "Failed to send SMS auto-reply: ${sendResult.reason}")
                            }
                        }
                    }
                    is AiResult.Error -> {
                        app.database.autoResponderDao().updateEvent(
                            AutoResponderEvent(
                                id = eventId,
                                eventType = "SMS_RECEIVED",
                                senderOrNumber = sender,
                                incomingContent = fullMessageBody,
                                generatedReply = null,
                                status = "FAILED",
                                errorMessage = aiResult.message
                            )
                        )
                        Log.e(tag, "Gemini failed to generate reply: ${aiResult.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error processing incoming SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
