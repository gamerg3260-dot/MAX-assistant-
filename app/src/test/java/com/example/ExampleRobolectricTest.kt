package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.AppSettingsRepository
import com.example.permissions.PermissionHelper
import com.example.security.SecureKeyManager
import com.example.voice.CallAnnouncer
import com.example.voice.ContactResolver
import com.example.voice.VoiceCommand
import com.example.voice.VoiceCommandDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MAX Assistant", appName)
    }

    @Test
    fun `test secure key manager save and clear`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SecureKeyManager.saveApiKey(context, "test_api_key_12345")
        assertEquals("test_api_key_12345", SecureKeyManager.getApiKey(context))
        assertTrue(SecureKeyManager.hasValidApiKey(context))

        SecureKeyManager.clearCustomApiKey(context)
    }

    @Test
    fun `test settings repository defaults and updates`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = AppSettingsRepository.getInstance(context)
        assertNotNull(repo.settings.value)
        assertTrue(repo.settings.value.isMaxAssistantEnabled)
        assertTrue(repo.settings.value.isCallAnnouncerEnabled)
        assertTrue(repo.settings.value.isVoiceCallControlEnabled)

        repo.setTtsSpeechRate(1.25f)
        assertEquals(1.25f, repo.settings.value.ttsSpeechRate, 0.01f)
    }

    @Test
    fun `test required permissions list includes voice and call answering`() {
        val permissions = PermissionHelper.REQUIRED_PERMISSIONS
        assertTrue(permissions.contains(android.Manifest.permission.ANSWER_PHONE_CALLS))
        assertTrue(permissions.contains(android.Manifest.permission.RECORD_AUDIO))
        assertTrue(permissions.contains(android.Manifest.permission.READ_PHONE_STATE))
        assertTrue(permissions.contains(android.Manifest.permission.READ_CONTACTS))
    }

    @Test
    fun `test voice command detector matching`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val detector = VoiceCommandDetector(context)

        assertEquals(VoiceCommand.ACCEPT, detector.evaluateCommand(listOf("accept")))
        assertEquals(VoiceCommand.ACCEPT, detector.evaluateCommand(listOf("please receive call")))
        assertEquals(VoiceCommand.ACCEPT, detector.evaluateCommand(listOf("answer the phone")))
        assertEquals(VoiceCommand.ACCEPT, detector.evaluateCommand(listOf("yes")))

        assertEquals(VoiceCommand.REJECT, detector.evaluateCommand(listOf("reject")))
        assertEquals(VoiceCommand.REJECT, detector.evaluateCommand(listOf("decline call")))
        assertEquals(VoiceCommand.REJECT, detector.evaluateCommand(listOf("disconnect")))

        assertEquals(VoiceCommand.SILENCE, detector.evaluateCommand(listOf("silence")))
        assertEquals(VoiceCommand.SILENCE, detector.evaluateCommand(listOf("mute")))
    }

    @Test
    fun `test announcement template builder`() {
        val text = CallAnnouncer.buildAnnouncementText(
            callerNameOrNumber = "Sarah Connor",
            template = "Incoming call from {name}",
            repeatCount = 2
        )
        assertEquals("Incoming call from Sarah Connor. Incoming call from Sarah Connor", text)
    }
}
