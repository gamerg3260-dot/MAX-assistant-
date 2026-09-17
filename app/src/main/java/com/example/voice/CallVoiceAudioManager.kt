package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.util.Log

/**
 * Handles Audio Focus, Ringer volume management, feedback tones, and Speakerphone routing
 * to ensure TTS announcements and SpeechRecognizer listening do not collide with incoming ringing.
 */
class CallVoiceAudioManager(private val context: Context) {
    private val tag = "CallVoiceAudioManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var originalRingerMode: Int? = null
    private var originalRingerVolume: Int? = null

    fun requestVoiceAssistantAudioFocus(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { focusChange ->
                        Log.d(tag, "Audio focus changed: $focusChange")
                    }
                    .build()

                audioFocusRequest = focusRequest
                val result = audioManager.requestAudioFocus(focusRequest)
                result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                val result = audioManager.requestAudioFocus(
                    { focusChange -> Log.d(tag, "Legacy focus changed: $focusChange") },
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
                result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to request audio focus: ${e.message}", e)
            false
        }
    }

    fun releaseVoiceAssistantAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to abandon audio focus: ${e.message}", e)
        }
    }

    fun playListeningPromptBeep() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            // ToneGenerator releases automatically after tone in brief duration
        } catch (e: Exception) {
            Log.e(tag, "Error playing prompt beep: ${e.message}")
        }
    }

    fun playCommandRecognizedBeep() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 90)
            toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 200)
        } catch (e: Exception) {
            Log.e(tag, "Error playing ack beep: ${e.message}")
        }
    }

    fun setSpeakerphoneOn(on: Boolean) {
        try {
            audioManager.mode = if (on) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = on
            Log.d(tag, "Speakerphone set to $on")
        } catch (e: Exception) {
            Log.e(tag, "Error changing speakerphone state: ${e.message}", e)
        }
    }
}
