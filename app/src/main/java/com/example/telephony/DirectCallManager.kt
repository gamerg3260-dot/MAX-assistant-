package com.example.telephony

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.voice.ContactResolver
import java.util.Locale

data class DirectCallResult(
    val isHandled: Boolean,
    val feedbackMessage: String,
    val targetNumber: String? = null,
    val targetName: String? = null,
    val callIntent: Intent? = null,
    val isPermissionMissing: Boolean = false
)

/**
 * Direct Calling voice command resolution and immediate execution engine.
 * Recognizes spoken phone numbers and contact names, and immediately launches
 * Intent(Intent.ACTION_CALL) with a 'tel:' URI to place the phone call directly,
 * bypassing any dialer UI or intermediate confirmation delay.
 */
class DirectCallManager(private val context: Context) {
    private val tag = "DirectCallManager"

    fun processVoiceCallCommand(rawQuery: String): DirectCallResult {
        val q = rawQuery.trim()
        val lower = q.lowercase(Locale.ROOT)

        // Filter out non-call commands
        if (lower.contains("call log") || lower.contains("call history") ||
            lower.contains("call setting") || lower.contains("call announcer") ||
            lower.contains("call volume") || lower.contains("call recording") ||
            lower.contains("missed call") || lower.contains("cut call") ||
            lower.contains("disconnect call") || lower.contains("reject call") ||
            lower.contains("end call") || lower.contains("accept call") ||
            lower.contains("receive call") || lower.contains("answer call")
        ) {
            return DirectCallResult(isHandled = false, feedbackMessage = "Not a direct call command.")
        }

        var candidateTarget: String? = null

        // Pattern 1: Hindi: "<Target> ko/par call/phone karo/lagao/milao/kijiye/kar do/laga do"
        val hindiSuffixRegex = Regex("""^(.+?)\s*(?:ko|par)\s*(?:call|phone)\s*(?:karo|lagao|milao|kijiye|kar do|laga do)\b""", RegexOption.IGNORE_CASE)
        val matchHindiSuffix = hindiSuffixRegex.find(lower)
        if (matchHindiSuffix != null) {
            candidateTarget = matchHindiSuffix.groupValues[1].trim()
        }

        // Pattern 2: Hindi prefix: "call/phone karo/lagao/milao <Target>"
        if (candidateTarget == null) {
            val hindiPrefixRegex = Regex("""^(?:call|phone)\s*(?:karo|lagao|milao|kijiye)\s*(?:ko|par|to)?\s*(.+)""", RegexOption.IGNORE_CASE)
            val matchHindiPrefix = hindiPrefixRegex.find(lower)
            if (matchHindiPrefix != null) {
                candidateTarget = matchHindiPrefix.groupValues[1].trim()
            }
        }

        // Pattern 3: English: "call / phone / dial / ring / make a call to / place a call to <Target>"
        if (candidateTarget == null) {
            val englishRegex = Regex("""^(?:please\s+)?(?:call|phone|dial|ring|make\s+a\s+call\s+to|place\s+a\s+call\s+to)\s+(?:to\s+)?(.+)""", RegexOption.IGNORE_CASE)
            val matchEnglish = englishRegex.find(lower)
            if (matchEnglish != null) {
                candidateTarget = matchEnglish.groupValues[1].trim()
            }
        }

        // Pattern 4: Fallback for queries containing "call <Target>" or "phone <Target>"
        if (candidateTarget == null) {
            val wordBoundaryRegex = Regex("""\b(?:call|phone|dial)\s+(?:to\s+)?(.+)""", RegexOption.IGNORE_CASE)
            val matchWord = wordBoundaryRegex.find(lower)
            if (matchWord != null) {
                val candidate = matchWord.groupValues[1].trim()
                if (!candidate.startsWith("me ") && !candidate.startsWith("it ") && candidate.length in 2..50) {
                    candidateTarget = candidate
                }
            }
        }

        if (candidateTarget.isNullOrBlank()) {
            return DirectCallResult(isHandled = false, feedbackMessage = "No calling intent recognized.")
        }

        // Clean candidate: remove trailing punctuation or courtesy words
        val cleanCandidate = candidateTarget
            .replace(Regex("""(?i)\b(please|now|immediately|fast|jaldi)\b"""), "")
            .trim(' ', '.', '?', '!', ',')

        if (cleanCandidate.isBlank()) {
            return DirectCallResult(isHandled = false, feedbackMessage = "No phone number or contact specified.")
        }

        // Check if candidate is a numeric phone number
        val digitsOnly = cleanCandidate.filter { it.isDigit() }
        val hasPlus = cleanCandidate.startsWith("+")
        val isMostlyDigits = digitsOnly.length >= 3 && (digitsOnly.length.toFloat() / cleanCandidate.replace(" ", "").length) > 0.6f

        var targetNumber: String? = null
        var displayName: String = cleanCandidate

        if (isMostlyDigits) {
            targetNumber = if (hasPlus) "+$digitsOnly" else digitsOnly
            displayName = targetNumber
        } else {
            // Contact Name query: Look up in phone contacts
            val contactMatch = ContactResolver.findPhoneNumberByName(context, cleanCandidate)
            if (contactMatch != null) {
                displayName = contactMatch.first
                targetNumber = contactMatch.second
            } else {
                // Fallback: If query contains some digits (e.g. "9876543210"), extract them
                if (digitsOnly.length >= 7) {
                    targetNumber = if (hasPlus) "+$digitsOnly" else digitsOnly
                    displayName = targetNumber
                }
            }
        }

        if (targetNumber.isNullOrBlank()) {
            val errMsg = "Could not find contact \"$cleanCandidate\" in your contacts."
            Log.w(tag, errMsg)
            return DirectCallResult(isHandled = true, feedbackMessage = errMsg)
        }

        // Check CALL_PHONE runtime permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            val permMsg = "CALL_PHONE permission is required to make direct calls."
            Log.e(tag, permMsg)
            return DirectCallResult(
                isHandled = true,
                feedbackMessage = permMsg,
                targetNumber = targetNumber,
                targetName = displayName,
                isPermissionMissing = true
            )
        }

        // Execute Intent.ACTION_CALL directly without opening dialer UI
        return try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${Uri.encode(targetNumber)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(callIntent)
            val successMsg = "Calling $displayName directly..."
            Log.i(tag, "Successfully executed direct Intent.ACTION_CALL to $displayName ($targetNumber)")
            DirectCallResult(
                isHandled = true,
                feedbackMessage = successMsg,
                targetNumber = targetNumber,
                targetName = displayName,
                callIntent = callIntent
            )
        } catch (e: Exception) {
            val err = "Failed to initiate direct call: ${e.localizedMessage}"
            Log.e(tag, err, e)
            DirectCallResult(isHandled = true, feedbackMessage = err)
        }
    }

    fun launchDirectCall(phoneNumber: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(tag, "CALL_PHONE permission not granted")
            return false
        }
        return try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${Uri.encode(phoneNumber.trim())}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(callIntent)
            true
        } catch (e: Exception) {
            Log.e(tag, "Error starting ACTION_CALL: ${e.message}", e)
            false
        }
    }
}
