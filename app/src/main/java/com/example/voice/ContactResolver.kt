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

    /**
     * Resolves a spoken contact name or partial name to a matching phone contact and number.
     * Returns Pair(ResolvedDisplayName, PhoneNumber) or null if no matching contact found.
     */
    fun findPhoneNumberByName(context: Context, query: String): Pair<String, String>? {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return null

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "READ_CONTACTS permission not granted. Cannot search contacts.")
            return null
        }

        return try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$cleanQuery%")

            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (nameIndex != -1 && numberIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        val number = cursor.getString(numberIndex)
                        if (!number.isNullOrBlank()) {
                            return Pair(name ?: cleanQuery, number)
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error searching contact by name '$cleanQuery': ${e.message}", e)
            null
        }
    }

    /**
     * Formats phone numbers so Android Text-To-Speech spells out individual digits or cadence groups
     * rather than misreading phone numbers as billions or integers.
     */
    fun formatPhoneNumberForSpeech(number: String): String {
        val digitsOnly = number.filter { it.isDigit() }
        return if (digitsOnly.length >= 7) {
            // Group digits into chunks of 3 or 4 separated by commas for natural TTS cadence
            digitsOnly.chunked(3).joinToString(", ") { chunk ->
                chunk.toCharArray().joinToString(" ")
            }
        } else if (digitsOnly.isNotEmpty()) {
            digitsOnly.toCharArray().joinToString(" ")
        } else if (number.isNotBlank()) {
            number
        } else {
            "Unknown Caller"
        }
    }
}
