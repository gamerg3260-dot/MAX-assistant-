package com.example.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap

sealed class SendSmsResult {
    data class Success(val messageCount: Int) : SendSmsResult()
    data class Failure(val reason: String) : SendSmsResult()
}

/**
 * Automates transmitting SMS messages using Android's SmsManager.
 */
class SmsSender(private val context: Context) {
    private val tag = "SmsSender"

    // Cooldown tracker to prevent infinite loops (number -> last sent timestamp)
    private val lastSentTimestampMap = ConcurrentHashMap<String, Long>()

    /**
     * Sends an SMS message to the specified recipient phone number.
     * Handles single-part and multi-part text messages.
     */
    fun sendSms(
        destinationNumber: String,
        messageText: String,
        cooldownMinutes: Int = 3,
        bypassCooldown: Boolean = false
    ): SendSmsResult {
        val cleanNumber = destinationNumber.trim()
        val cleanMessage = messageText.trim()

        if (cleanNumber.isEmpty()) {
            return SendSmsResult.Failure("Destination phone number is empty.")
        }
        if (cleanMessage.isEmpty()) {
            return SendSmsResult.Failure("Message body is empty.")
        }

        // Check SEND_SMS runtime permission
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return SendSmsResult.Failure("SEND_SMS permission is not granted.")
        }

        // Anti-spam cooldown protection
        if (!bypassCooldown && cooldownMinutes > 0) {
            val lastSent = lastSentTimestampMap[cleanNumber] ?: 0L
            val elapsedMillis = System.currentTimeMillis() - lastSent
            val cooldownMillis = cooldownMinutes * 60 * 1000L

            if (elapsedMillis < cooldownMillis) {
                val remainingSec = ((cooldownMillis - elapsedMillis) / 1000).coerceAtLeast(1)
                return SendSmsResult.Failure("Anti-spam cooldown active for $cleanNumber ($remainingSec s remaining).")
            }
        }

        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(cleanMessage)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(
                    cleanNumber,
                    null,
                    parts,
                    null,
                    null
                )
            } else {
                smsManager.sendTextMessage(
                    cleanNumber,
                    null,
                    cleanMessage,
                    null,
                    null
                )
            }

            lastSentTimestampMap[cleanNumber] = System.currentTimeMillis()
            Log.d(tag, "Successfully dispatched SMS to $cleanNumber (${parts.size} part(s))")
            SendSmsResult.Success(parts.size)
        } catch (e: Exception) {
            Log.e(tag, "Failed to send SMS to $cleanNumber", e)
            SendSmsResult.Failure("SmsManager failed: ${e.localizedMessage ?: e.message}")
        }
    }
}
