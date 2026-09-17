package com.example.whatsapp

import android.app.Notification
import android.app.RemoteInput
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Service extending NotificationListenerService to intercept incoming WhatsApp messages and
 * capture RemoteInput actions for voice/auto replies.
 */
class WhatsAppNotificationListenerService : NotificationListenerService() {
    private val tag = "WhatsAppNotifService"

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
            WhatsAppControlManager.instance.onMessageReceived(
                sender = title,
                text = text,
                packageName = pkg,
                notificationKey = sbn.key,
                replyAction = replyAction
            )
        } catch (e: Exception) {
            Log.e(tag, "Error parsing WhatsApp notification: ${e.message}", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
