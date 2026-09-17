package com.example.whatsapp

import android.app.Notification
import android.app.RemoteInput

data class WhatsAppMessage(
    val id: String,
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val notificationKey: String,
    val replyAction: Notification.Action? = null,
    val remoteInput: RemoteInput? = null
)
