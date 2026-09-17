package com.example.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Helper utility to manage and check required permissions for MAX Assistant
 * (Caller ID Announcement, Voice Command Recognition, and Call Handling).
 */
object PermissionHelper {

    val REQUIRED_PERMISSIONS: Array<String>
        get() {
            val baseList = mutableListOf(
                Manifest.permission.ANSWER_PHONE_CALLS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MODIFY_AUDIO_SETTINGS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_SMS
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                baseList.add(Manifest.permission.MANAGE_OWN_CALLS)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                baseList.add(Manifest.permission.POST_NOTIFICATIONS)
            }

            return baseList.toTypedArray()
        }

    val VOICE_ASSISTANT_PERMISSIONS: Array<String>
        get() = arrayOf(
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECORD_AUDIO
        )

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasAllPermissions(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all { hasPermission(context, it) }
    }

    fun hasVoicePermissions(context: Context): Boolean {
        return VOICE_ASSISTANT_PERMISSIONS.all { hasPermission(context, it) }
    }

    fun getMissingPermissions(context: Context): List<String> {
        return REQUIRED_PERMISSIONS.filter { !hasPermission(context, it) }
    }

    fun getPermissionLabel(permission: String): String {
        return when (permission) {
            Manifest.permission.ANSWER_PHONE_CALLS -> "Answer Phone Calls (Voice Call Control)"
            Manifest.permission.RECORD_AUDIO -> "Record Audio (Voice Command Speech Recognition)"
            Manifest.permission.READ_PHONE_STATE -> "Read Phone State (Detect Incoming Calls)"
            Manifest.permission.READ_CONTACTS -> "Read Contacts (Caller ID Name Resolution)"
            Manifest.permission.READ_CALL_LOG -> "Read Call Log (Detect Caller & Missed Events)"
            Manifest.permission.MANAGE_OWN_CALLS -> "Manage Calls (Telecom Integration)"
            Manifest.permission.POST_NOTIFICATIONS -> "Post Notifications (Foreground Service & Alerts)"
            Manifest.permission.RECEIVE_SMS -> "Receive SMS (SMS Assistant)"
            Manifest.permission.SEND_SMS -> "Send SMS (SMS Auto-Reply)"
            Manifest.permission.READ_SMS -> "Read SMS (Process SMS Context)"
            Manifest.permission.MODIFY_AUDIO_SETTINGS -> "Modify Audio (Audio Focus Management)"
            else -> permission.substringAfterLast(".")
        }
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

