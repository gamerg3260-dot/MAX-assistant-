package com.example.whatsapp

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import com.example.voice.CallAnnouncer
import com.example.voice.ElevenLabsKeyManager
import com.example.voice.ElevenLabsResult
import com.example.voice.ElevenLabsTtsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Central manager for WhatsApp Mobile Control: message interception, RemoteInput replies,
 * and voice-assisted Read Aloud.
 */
class WhatsAppControlManager private constructor() {
    private val tag = "WhatsAppControlManager"

    private val _messages = MutableStateFlow<List<WhatsAppMessage>>(emptyList())
    val messages: StateFlow<List<WhatsAppMessage>> = _messages.asStateFlow()

    private val _lastInterceptedStatus = MutableStateFlow<String?>("Listening for WhatsApp notifications...")
    val lastInterceptedStatus: StateFlow<String?> = _lastInterceptedStatus.asStateFlow()

    private val activeReplyActions = mutableMapOf<String, Notification.Action>()

    fun onMessageReceived(
        sender: String,
        text: String,
        packageName: String,
        notificationKey: String,
        replyAction: Notification.Action?
    ): WhatsAppMessage {
        val msgId = "${packageName}_${System.currentTimeMillis()}"
        val msg = WhatsAppMessage(
            id = msgId,
            sender = sender,
            text = text,
            packageName = packageName,
            notificationKey = notificationKey,
            replyAction = replyAction,
            remoteInput = replyAction?.remoteInputs?.firstOrNull()
        )

        replyAction?.let {
            activeReplyActions[msgId] = it
        }

        val currentList = _messages.value.toMutableList()
        currentList.add(0, msg) // top of list
        _messages.value = currentList.take(30) // keep last 30 messages
        _lastInterceptedStatus.value = "New WhatsApp msg from $sender: \"$text\""
        Log.i(tag, "Intercepted WhatsApp message from $sender: $text")
        return msg
    }

    /**
     * Sends a direct reply back to the WhatsApp sender using Android's RemoteInput API.
     */
    fun sendRemoteInputReply(context: Context, messageId: String, replyText: String): Boolean {
        val action = activeReplyActions[messageId] ?: _messages.value.find { it.id == messageId }?.replyAction
        if (action == null) {
            Log.e(tag, "No reply action found for message $messageId")
            _lastInterceptedStatus.value = "Failed to reply: RemoteInput action expired or missing."
            return false
        }

        val remoteInputs = action.remoteInputs
        if (remoteInputs.isNullOrEmpty()) {
            Log.e(tag, "RemoteInput list is empty for action")
            _lastInterceptedStatus.value = "Failed to reply: Notification has no text input action."
            return false
        }

        return try {
            val intent = Intent()
            val bundle = Bundle()
            for (remoteInput in remoteInputs) {
                bundle.putCharSequence(remoteInput.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
            action.actionIntent.send(context, 0, intent)
            _lastInterceptedStatus.value = "Reply sent to WhatsApp: \"$replyText\""
            Log.i(tag, "Successfully sent WhatsApp reply via RemoteInput: $replyText")
            true
        } catch (e: Exception) {
            Log.e(tag, "Error sending RemoteInput reply: ${e.message}", e)
            _lastInterceptedStatus.value = "Error sending WhatsApp reply: ${e.localizedMessage}"
            false
        }
    }

    /**
     * Reads aloud the WhatsApp message using ElevenLabs TTS or fallback Android TTS.
     */
    fun readAloudMessage(
        message: WhatsAppMessage,
        elevenLabsService: ElevenLabsTtsService,
        elevenLabsKeyManager: ElevenLabsKeyManager,
        announcer: CallAnnouncer,
        scope: CoroutineScope,
        onStatus: (String) -> Unit
    ) {
        val readText = "New WhatsApp message from ${message.sender}. ${message.text}"
        onStatus("Reading aloud: \"$readText\"")

        scope.launch(Dispatchers.IO) {
            if (elevenLabsKeyManager.hasValidApiKey()) {
                val result = elevenLabsService.generateSpeech(
                    text = readText,
                    voiceId = "21m00Tcm4TlvDq8ikWAM"
                )
                when (result) {
                    is ElevenLabsResult.Success -> {
                        elevenLabsService.playAudio(result.audioFile) {
                            onStatus("Read aloud completed.")
                        }
                    }
                    is ElevenLabsResult.Error -> {
                        onStatus("ElevenLabs Error: ${result.message}. Fallback to standard TTS...")
                        announcer.announceCaller(readText, template = "{name}", speechRate = 1.0f, speechPitch = 1.0f)
                    }
                }
            } else {
                announcer.announceCaller(readText, template = "{name}", speechRate = 1.0f, speechPitch = 1.0f)
            }
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
        activeReplyActions.clear()
        _lastInterceptedStatus.value = "Messages cleared."
    }

    companion object {
        val instance by lazy { WhatsAppControlManager() }

        fun isNotificationListenerGranted(context: Context): Boolean {
            val packageName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            return flat != null && flat.contains(packageName)
        }
    }
}
