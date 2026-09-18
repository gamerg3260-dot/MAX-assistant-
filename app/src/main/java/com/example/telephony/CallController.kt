package com.example.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

sealed class CallActionResult {
    data class Success(val message: String) : CallActionResult()
    data class Failure(val reason: String) : CallActionResult()
}

/**
 * Executes call answering, rejection, and ringer silencing operations
 * via TelecomManager on Android 8.0+ (API 26+) and Android 9.0+ (API 28+).
 */
class CallController(private val context: Context) {
    private val tag = "CallController"
    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /**
     * Accepts the currently ringing incoming phone call.
     * Uses InCallService and TelecomManager.acceptRingingCall() on Android 8.0+ (API 26+).
     */
    fun acceptRingingCall(): CallActionResult {
        // 1. Attempt programmatic answer via active InCallService Call instance
        if (MaxInCallService.answerCurrentCall()) {
            Log.i(tag, "Call accepted via InCallService Telecom API.")
            return CallActionResult.Success("Call accepted via Telecom InCallService.")
        }

        // 2. Fallback to TelecomManager.acceptRingingCall()
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ANSWER_PHONE_CALLS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(tag, "ANSWER_PHONE_CALLS permission not granted.")
            return CallActionResult.Failure("Missing ANSWER_PHONE_CALLS permission.")
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (telecomManager != null) {
                    @Suppress("DEPRECATION")
                    telecomManager.acceptRingingCall()
                    Log.i(tag, "telecomManager.acceptRingingCall() executed successfully.")
                    CallActionResult.Success("Call accepted via TelecomManager.")
                } else {
                    CallActionResult.Failure("TelecomManager service not available.")
                }
            } else {
                CallActionResult.Failure("Answering calls programmatically requires Android 8.0+.")
            }
        } catch (e: SecurityException) {
            Log.e(tag, "SecurityException accepting call: ${e.message}", e)
            CallActionResult.Failure("SecurityException: ${e.message}")
        } catch (e: Exception) {
            Log.e(tag, "Exception accepting call: ${e.message}", e)
            CallActionResult.Failure("Failed to accept call: ${e.message}")
        }
    }

    /**
     * Ends or rejects the currently ringing or active phone call.
     * Uses InCallService and TelecomManager.endCall() on Android 9.0+ (API 28+).
     */
    fun endCall(): CallActionResult {
        // 1. Attempt programmatic rejection/end via active InCallService Call instance
        if (MaxInCallService.rejectCurrentCall()) {
            Log.i(tag, "Call rejected/ended via InCallService Telecom API.")
            return CallActionResult.Success("Call rejected via Telecom InCallService.")
        }

        // 2. Fallback to TelecomManager.endCall()
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ANSWER_PHONE_CALLS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(tag, "ANSWER_PHONE_CALLS permission not granted for endCall.")
            return CallActionResult.Failure("Missing ANSWER_PHONE_CALLS permission.")
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (telecomManager != null) {
                    val ended = telecomManager.endCall()
                    Log.i(tag, "telecomManager.endCall() executed, result: $ended")
                    if (ended) {
                        CallActionResult.Success("Call ended via TelecomManager.")
                    } else {
                        CallActionResult.Failure("TelecomManager could not terminate call.")
                    }
                } else {
                    CallActionResult.Failure("TelecomManager service not available.")
                }
            } else {
                // Fallback attempt via TelephonyManager reflection for legacy devices
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                if (telephonyManager != null) {
                    val telephonyInterfaceMethod = telephonyManager.javaClass.getDeclaredMethod("getITelephony")
                    telephonyInterfaceMethod.isAccessible = true
                    val iTelephony = telephonyInterfaceMethod.invoke(telephonyManager)
                    val endCallMethod = iTelephony.javaClass.getDeclaredMethod("endCall")
                    endCallMethod.invoke(iTelephony)
                    CallActionResult.Success("Call ended via legacy telephony interface.")
                } else {
                    CallActionResult.Failure("Ending call requires Android 9.0+.")
                }
            }
        } catch (e: SecurityException) {
            Log.e(tag, "SecurityException ending call: ${e.message}", e)
            CallActionResult.Failure("SecurityException: ${e.message}")
        } catch (e: Exception) {
            Log.e(tag, "Exception ending call: ${e.message}", e)
            CallActionResult.Failure("Failed to end call: ${e.message}")
        }
    }

    /**
     * Silences the ringer for the current incoming call without disconnecting.
     */
    fun silenceRinger(): CallActionResult {
        return try {
            if (telecomManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                telecomManager.silenceRinger()
                Log.i(tag, "telecomManager.silenceRinger() executed.")
                CallActionResult.Success("Ringer silenced.")
            } else if (audioManager != null) {
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                CallActionResult.Success("Ringer set to silent.")
            } else {
                CallActionResult.Failure("Audio services unavailable.")
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception silencing ringer: ${e.message}", e)
            CallActionResult.Failure("Failed to silence ringer: ${e.message}")
        }
    }
}
