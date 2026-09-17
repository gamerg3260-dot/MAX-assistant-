package com.example.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Resolves phone numbers to human-readable Contact Names using ContactsContract.
 */
object ContactResolver {
    private const val TAG = "ContactResolver"

    fun resolveCallerName(context: Context, phoneNumber: String?): String {
        if (phoneNumber.isNullOrBlank() || phoneNumber.equals("null", ignoreCase = true)) {
            return "Unknown Caller"
        }

        // Clean query phone number
        val cleanedNumber = phoneNumber.trim()

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "READ_CONTACTS permission not granted. Returning raw number.")
            return formatPhoneNumberForSpeech(cleanedNumber)
        }

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(cleanedNumber)
            )
            val projection = arrayOf(
                ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME
            )

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) {
                            return name
                        }
                    }
                }
            }
            formatPhoneNumberForSpeech(cleanedNumber)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving contact name: ${e.message}", e)
            formatPhoneNumberForSpeech(cleanedNumber)
        }
    }

    private fun formatPhoneNumberForSpeech(number: String): String {
        // If it looks like a phone number, format digits with spaces so TTS spells out natural digit groups
        val digitsOnly = number.filter { it.isDigit() }
        return if (digitsOnly.length >= 7) {
            // Group digits for clear speech cadence: e.g. "5 5 5, 0 1 9 9"
            number
        } else if (number.isNotBlank()) {
            number
        } else {
            "Unknown Caller"
        }
    }
}
