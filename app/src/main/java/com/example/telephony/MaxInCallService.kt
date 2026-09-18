package com.example.telephony

import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log

/**
 * Android Telecom InCallService integration for MAX Assistant.
 * Receives active and incoming Call instances directly from Android TelecomManager
 * to answer or reject incoming calls programmatically based on voice commands.
 */
class MaxInCallService : InCallService() {

    override fun onCallAdded(call: Call?) {
        super.onCallAdded(call)
        if (call == null) return
        Log.i(TAG, "InCallService onCallAdded: state=${call.state}")
        activeCall = call
        call.registerCallback(callCallback)
    }

    override fun onCallRemoved(call: Call?) {
        super.onCallRemoved(call)
        Log.i(TAG, "InCallService onCallRemoved")
        if (activeCall == call) {
            activeCall?.unregisterCallback(callCallback)
            activeCall = null
        }
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call?, state: Int) {
            super.onStateChanged(call, state)
            Log.d(TAG, "InCallService Call state changed: $state")
            if (state == Call.STATE_DISCONNECTED) {
                if (activeCall == call) {
                    activeCall?.unregisterCallback(this)
                    activeCall = null
                }
            }
        }
    }

    companion object {
        private const val TAG = "MaxInCallService"

        @Volatile
        var activeCall: Call? = null
            private set

        /**
         * Answers the currently ringing call programmatically via InCallService.
         */
        fun answerCurrentCall(): Boolean {
            val call = activeCall
            if (call != null && (call.state == Call.STATE_RINGING || call.state == Call.STATE_SELECT_PHONE_ACCOUNT)) {
                return try {
                    call.answer(VideoProfile.STATE_AUDIO_ONLY)
                    Log.i(TAG, "Successfully answered incoming call via InCallService.Call.answer()")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Exception answering call via InCallService: ${e.message}", e)
                    false
                }
            }
            return false
        }

        /**
         * Rejects or ends the currently ringing/active call programmatically via InCallService.
         */
        fun rejectCurrentCall(): Boolean {
            val call = activeCall
            if (call != null) {
                return try {
                    if (call.state == Call.STATE_RINGING) {
                        call.reject(false, null)
                        Log.i(TAG, "Successfully rejected incoming call via InCallService.Call.reject()")
                    } else {
                        call.disconnect()
                        Log.i(TAG, "Successfully disconnected active call via InCallService.Call.disconnect()")
                    }
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Exception rejecting call via InCallService: ${e.message}", e)
                    false
                }
            }
            return false
        }
    }
}
