package com.example.whatsapp

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.AutoResponderApp
import com.example.ai.AiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Service extending NotificationListenerService to intercept incoming WhatsApp messages and
 * capture RemoteInput actions for voice/auto replies.
 */
class WhatsAppNotificationListenerService : NotificationListenerService() {
    private val tag = "WhatsAppNotifService"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(tag, "WhatsApp NotificationListenerService connected successfully")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return

        // Intercept WhatsApp, WhatsApp Business, and compatible messaging notifications
        val isWhatsApp = pkg.contains("whatsapp", ignoreCase = true)
        if (!isWhatsApp) return

        // 1. Check SharedPreferences key "whatsapp_auto_reply_enabled"
        val prefs = applicationContext.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)
        val isAutoReplyEnabled = prefs.getBoolean("whatsapp_auto_reply_enabled", true)

        if (!isAutoReplyEnabled) {
            Log.i(tag, "WhatsApp Auto-Reply switch is OFF ('whatsapp_auto_reply_enabled' = false). Ignoring notification completely.")
            return
        }

        try {
            val notification = sbn.notification ?: return
            val extras = notification.extras ?: return

            // Extract title / sender
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
                ?: "WhatsApp Contact"

            // Ignore system notifications like "WhatsApp Web is active" or "Backup in progress"
            if (title.contains("WhatsApp", ignoreCase = true) && title.contains("active", ignoreCase = true)) {
                return
            }

            // Extract message content
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: ""

            if (text.isBlank()) return

            // Locate Reply action containing RemoteInput
            var replyAction: Notification.Action? = null
            notification.actions?.let { actions ->
                for (action in actions) {
                    val remoteInputs = action.remoteInputs
                    if (!remoteInputs.isNullOrEmpty()) {
                        replyAction = action
                        break
                    }
                }
            }

            // Pass intercepted message to WhatsAppControlManager
            val interceptedMsg = WhatsAppControlManager.instance.onMessageReceived(
                sender = title,
                text = text,
                packageName = pkg,
                notificationKey = sbn.key,
                replyAction = replyAction
            )

            // 2. Since Auto-Reply is ON, pass to MAX Assistant AI (Gemini API) to generate response and reply via RemoteInput
            if (replyAction != null) {
                val app = applicationContext as? AutoResponderApp
                val geminiService = app?.geminiService
                val settingsRepo = app?.settingsRepository

                if (geminiService != null && settingsRepo != null) {
                    scope.launch {
                        try {
                            val settings = settingsRepo.settings.value
                            Log.i(tag, "Generating Gemini AI response for WhatsApp notification from $title: \"$text\"")
                            val aiResult = geminiService.generateWhatsAppReply(
                                senderName = title,
                                incomingMessage = text,
                                settings = settings
                            )

                            when (aiResult) {
                                is AiResult.Success -> {
                                    val aiReply = aiResult.text
                                    Log.i(tag, "Gemini AI reply generated: \"$aiReply\". Sending back via RemoteInput...")
                                    val success = WhatsAppControlManager.instance.sendRemoteInputReply(
                                        context = applicationContext,
                                        messageId = interceptedMsg.id,
                                        replyText = aiReply
                                    )
                                    if (success) {
                                        Log.i(tag, "Successfully sent WhatsApp auto-reply via RemoteInput!")
                                    }
                                }
                                is AiResult.Error -> {
                                    Log.e(tag, "Gemini AI WhatsApp Auto-Reply Error: ${aiResult.message}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(tag, "Error during WhatsApp AI auto-reply execution: ${e.message}", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing WhatsApp notification: ${e.message}", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
