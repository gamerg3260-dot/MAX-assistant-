package com.example.security

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.util.Log
import android.widget.Toast

/**
 * DeviceAdminReceiver monitoring device security and password attempt events.
 * Listens for ACTION_PASSWORD_FAILED and triggers immediate security actions on failed unlock attempts.
 */
class MaxDeviceAdminReceiver : DeviceAdminReceiver() {
    private val tag = "MaxDeviceAdminReceiver"

    override fun onPasswordFailed(context: Context, intent: Intent, user: UserHandle) {
        super.onPasswordFailed(context, intent, user)
        Log.w(tag, ">>> ACTION_PASSWORD_FAILED received! Password/PIN/Pattern attempt failed on device. <<<")
        triggerSecurityActions(context)
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.w(tag, ">>> ACTION_PASSWORD_FAILED received! (Legacy call) <<<")
        triggerSecurityActions(context)
    }

    private fun triggerSecurityActions(context: Context) {
        try {
            val securityManager = IntruderSecurityManager.getInstance(context)
            securityManager.handleFailedUnlockAttempt()
        } catch (e: Exception) {
            Log.e(tag, "Error triggering security actions from DeviceAdminReceiver: ${e.message}", e)
        }
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent, user: UserHandle) {
        super.onPasswordSucceeded(context, intent, user)
        Log.i(tag, "Password succeeded. Device unlocked successfully.")
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(tag, "MAX Assistant Device Admin protection enabled.")
        Toast.makeText(context, "MAX Device Admin Protection Activated", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(tag, "MAX Assistant Device Admin protection disabled.")
        Toast.makeText(context, "MAX Device Admin Protection Deactivated", Toast.LENGTH_SHORT).show()
    }
}
