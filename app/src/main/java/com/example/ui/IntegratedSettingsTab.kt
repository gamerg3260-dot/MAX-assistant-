package com.example.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.example.data.repository.AppSettings
import com.example.toggle.SoundMode
import com.example.permissions.PermissionHelper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import android.graphics.BitmapFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Nested Navigation Screens for MAX Assistant Settings
 */
enum class SettingsSubScreen {
    MAIN,
    VOICE_AI,
    HARDWARE,
    SECURITY_SOS
}

@Composable
fun IntegratedSettingsTab(
    viewModel: AutoResponderViewModel,
    settings: AppSettings,
    missingPermissions: List<String>,
    theme: SiriThemeColors,
    onRequestPermissions: () -> Unit,
    onOpenApiKeyDialog: () -> Unit
) {
    var currentSubScreen by remember { mutableStateOf(SettingsSubScreen.MAIN) }

    // System Back Handler for seamless sub-screen navigation
    BackHandler(enabled = currentSubScreen != SettingsSubScreen.MAIN) {
        currentSubScreen = SettingsSubScreen.MAIN
    }

    AnimatedContent(
        targetState = currentSubScreen,
        transitionSpec = {
            if (targetState != SettingsSubScreen.MAIN) {
                (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                    slideOutHorizontally { width -> -width / 3 } + fadeOut()
                )
            } else {
                (slideInHorizontally { width -> -width / 3 } + fadeIn()).togetherWith(
                    slideOutHorizontally { width -> width } + fadeOut()
                )
            }
        },
        label = "SettingsNestedNavigation"
    ) { subScreen ->
        when (subScreen) {
            SettingsSubScreen.MAIN -> MainSettingsScreen(
                viewModel = viewModel,
                settings = settings,
                theme = theme,
                onNavigate = { currentSubScreen = it },
                onOpenApiKeyDialog = onOpenApiKeyDialog
            )

            SettingsSubScreen.VOICE_AI -> VoiceAiSettingsSubScreen(
                viewModel = viewModel,
                settings = settings,
                theme = theme,
                onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                onOpenApiKeyDialog = onOpenApiKeyDialog
            )

            SettingsSubScreen.HARDWARE -> HardwareSettingsSubScreen(
                viewModel = viewModel,
                settings = settings,
                theme = theme,
                onBack = { currentSubScreen = SettingsSubScreen.MAIN }
            )

            SettingsSubScreen.SECURITY_SOS -> SecuritySosSettingsSubScreen(
                viewModel = viewModel,
                settings = settings,
                missingPermissions = missingPermissions,
                theme = theme,
                onBack = { currentSubScreen = SettingsSubScreen.MAIN },
                onRequestPermissions = onRequestPermissions
            )
        }
    }
}

// ==========================================
// 1. MAIN SETTINGS SCREEN WITH CATEGORY CARDS
// ==========================================
@Composable
private fun MainSettingsScreen(
    viewModel: AutoResponderViewModel,
    settings: AppSettings,
    theme: SiriThemeColors,
    onNavigate: (SettingsSubScreen) -> Unit,
    onOpenApiKeyDialog: () -> Unit
) {
    val context = LocalContext.current
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val isOverlayActive by viewModel.isOverlayActive.collectAsState()
    val apiKey by viewModel.apiKeyText.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 260.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- COMPACT HEADER WITH BRANDING AND GLOBAL TOGGLE SWITCH ---
        SiriGlassCard(theme = theme) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (isServiceRunning) theme.primaryAccent.copy(alpha = 0.25f)
                                else Color.White.copy(alpha = 0.08f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isServiceRunning) theme.primaryAccent else Color.White.copy(alpha = 0.15f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Master Switch",
                            tint = if (isServiceRunning) theme.primaryAccent else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "MAX Assistant Pro",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isServiceRunning) Color(0xFF00E676).copy(alpha = 0.2f)
                                        else Color.White.copy(alpha = 0.1f)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isServiceRunning) "ACTIVE" else "STANDBY",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isServiceRunning) Color(0xFF00E676) else Color.Gray
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isServiceRunning) "Core hands-free engine is running" else "Global assistant toggle is OFF",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }

                Switch(
                    checked = settings.isMaxAssistantEnabled,
                    onCheckedChange = { isChecked -> viewModel.toggleMaxAssistant(isChecked) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = theme.primaryAccent,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("settings_master_switch")
                )
            }
        }

        // --- SECTION HEADER: SETTINGS CATEGORIES ---
        SiriSectionHeader(
            title = "Configuration Categories",
            icon = Icons.Default.Tune,
            theme = theme
        )

        // --- CATEGORY CARD 1: VOICE AI ---
        CategoryNavigationCard(
            title = "Voice AI & Barge-In",
            subtitle = "Real-Time Interruption, Language, Pitch, Rate, AI Engine & Bi-directional WebSocket",
            badges = listOf(
                if (settings.isBargeInEnabled) "Barge-In Active" else "Barge-In Off",
                if (settings.isRealtimeWebSocketEnabled) "WebSocket Low-Latency" else "Standard REST",
                settings.voiceLanguage,
                settings.voiceResponseStyle
            ),
            icon = Icons.Default.RecordVoiceOver,
            iconTint = theme.primaryAccent,
            theme = theme,
            testTag = "settings_cat_voice_ai",
            onClick = { onNavigate(SettingsSubScreen.VOICE_AI) }
        )

        // --- CATEGORY CARD 2: HARDWARE ---
        CategoryNavigationCard(
            title = "Hardware & Audio I/O",
            subtitle = "Microphone Source, Speaker Output, Sensitivity, Equalizer & Device Radios",
            badges = listOf(
                settings.microphoneSource,
                settings.speakerOutput,
                settings.equalizerPreset
            ),
            icon = Icons.Default.GraphicEq,
            iconTint = theme.secondaryAccent,
            theme = theme,
            testTag = "settings_cat_hardware",
            onClick = { onNavigate(SettingsSubScreen.HARDWARE) }
        )

        // --- CATEGORY CARD 3: SECURITY & SOS ---
        CategoryNavigationCard(
            title = "Security & SOS",
            subtitle = "Loud Anti-Theft Siren, Intruder Selfie Capture, Voice ID & Emergency SOS",
            badges = listOf(
                if (settings.isAntiTheftSirenEnabled) "Loud Siren Active" else "Siren Off",
                if (settings.isIntruderSelfieCaptureEnabled) "Intruder Selfie On" else "Selfie Off",
                if (settings.isMotionDetectionAlarmEnabled) "Motion Sensor Armed" else "Motion Standby",
                if (settings.isVoiceIdEnabled) "Voice ID Active" else "Voice ID Off"
            ),
            icon = Icons.Default.Security,
            iconTint = Color(0xFFFF5252),
            theme = theme,
            testTag = "settings_cat_security_sos",
            onClick = { onNavigate(SettingsSubScreen.SECURITY_SOS) }
        )

        // --- QUICK ESSENTIALS: THEME & OVERLAY ---
        SiriSectionHeader(
            title = "System Appearance & Overlay",
            icon = Icons.Default.ColorLens,
            theme = theme
        )

        SiriGlassCard(theme = theme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // System Overlay Floating Bubble Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.toggleSystemOverlay(context) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isOverlayActive) theme.secondaryAccent.copy(alpha = 0.2f)
                                    else Color.White.copy(alpha = 0.08f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "System Overlay",
                                tint = if (isOverlayActive) theme.secondaryAccent else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Floating System Bubble",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = if (isOverlayActive) "Screen overlay bubble is active" else "Overlay bubble is hidden",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Switch(
                        checked = isOverlayActive,
                        onCheckedChange = { viewModel.toggleSystemOverlay(context) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = theme.secondaryAccent
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_overlay_switch")
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                // Theme Presets Scroll
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Accent Theme Preset",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SiriThemePresets.allPresets.forEach { preset ->
                            val isSelected = preset.name == settings.themePreset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) theme.primaryAccent.copy(alpha = 0.25f)
                                        else Color.White.copy(alpha = 0.07f)
                                    )
                                    .border(
                                        width = if (isSelected) 1.2.dp else 0.5.dp,
                                        color = if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.setThemePreset(preset.name) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(preset.primaryAccent)
                                    )
                                    Text(
                                        text = preset.name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.8f)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Active",
                                            tint = theme.primaryAccent,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. DEDICATED SUB-SCREEN: VOICE AI
// ==========================================
@Composable
private fun VoiceAiSettingsSubScreen(
    viewModel: AutoResponderViewModel,
    settings: AppSettings,
    theme: SiriThemeColors,
    onBack: () -> Unit,
    onOpenApiKeyDialog: () -> Unit
) {
    val context = LocalContext.current
    val apiKey by viewModel.apiKeyText.collectAsState()
    var testTtsStatus by remember { mutableStateOf<String?>(null) }
    var customTemplateInput by remember(settings.announcementTemplate) {
        mutableStateOf(settings.announcementTemplate)
    }
    var customInstructionsInput by remember(settings.customInstructions) {
        mutableStateOf(settings.customInstructions)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 260.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Navigation Top Bar
        SubScreenTopBar(
            title = "Voice AI Settings",
            subtitle = "Engine, Speech Tone, Pitch & Assistant Controls",
            theme = theme,
            onBack = onBack
        )

        // --- 1. DEFAULT VOICE ASSISTANT ---
        SiriGlassCard(theme = theme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Default System Voice Assistant",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Set MAX Assistant as the primary voice assistant for home button / power key hold",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }

                    Switch(
                        checked = settings.isDefaultVoiceAssistant,
                        onCheckedChange = { isChecked -> viewModel.setDefaultVoiceAssistant(isChecked) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = theme.primaryAccent
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_default_assistant_switch")
                    )
                }

                Button(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                context.startActivity(intent)
                            } catch (_: Exception) { }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "System Assistant Settings",
                        tint = theme.primaryAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open Android Default Assistant Settings",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // --- 2. LANGUAGE SELECTION ---
        SiriGlassCard(theme = theme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Assistant Voice Language",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Select primary spoken language for MAX Native TTS and STT recognition",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )

                val languages = listOf(
                    "Hindi (India)",
                    "English (India)",
                    "English (US)",
                    "Hinglish (India)",
                    "Spanish (Latin)",
                    "Tamil (India)"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    languages.forEach { lang ->
                        val isSelected = settings.voiceLanguage == lang
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) theme.primaryAccent.copy(alpha = 0.25f)
                                    else Color.White.copy(alpha = 0.07f)
                                )
                                .border(
                                    width = if (isSelected) 1.2.dp else 0.5.dp,
                                    color = if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { viewModel.setVoiceLanguage(lang) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = lang,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        // --- 3. VOICE RESPONSE STYLE ---
        SiriGlassCard(theme = theme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Voice Response Style",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                val styles = listOf(
                    "Conversational" to "Natural & balanced tone",
                    "Concise & Fast" to "Brief, direct answers",
                    "Detailed & Helpful" to "Comprehensive explanations",
                    "Friendly & Warm" to "Empathetic & supportive",
                    "Formal & Professional" to "Structured & business"
                )

                styles.forEach { (style, desc) ->
                    val isSelected = settings.voiceResponseStyle == style
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) theme.primaryAccent.copy(alpha = 0.18f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .border(
                                width = if (isSelected) 1.dp else 0.5.dp,
                                color = if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { viewModel.setVoiceResponseStyle(style) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = style,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) theme.primaryAccent else Color.White
                            )
                            Text(
                                text = desc,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = theme.primaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- 4. VOICE PITCH & SPEECH RATE SLIDERS ---
        SiriGlassCard(theme = theme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Speech Synthesis Pitch & Speed",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Speech Pitch Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Speech Pitch (Tone)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = String.format("%.2fx", settings.ttsPitch),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.secondaryAccent
                        )
                    }
                    Slider(
                        value = settings.ttsPitch,
                        onValueChange = { newPitch -> viewModel.setTtsPitch(newPitch) },
                        valueRange = 0.5f..2.0f,
                        steps = 15,
                        colors = SliderDefaults.colors(
                            thumbColor = theme.secondaryAccent,
                            activeTrackColor = theme.secondaryAccent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                }

                // Speech Rate Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Speech Rate (Pacing)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = String.format("%.2fx", settings.ttsSpeechRate),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.primaryAccent
                        )
                    }
                    Slider(
                        value = settings.ttsSpeechRate,
                        onValueChange = { newRate -> viewModel.setTtsSpeechRate(newRate) },
                        valueRange = 0.5f..2.0f,
                        steps = 15,
                        colors = SliderDefaults.colors(
                            thumbColor = theme.primaryAccent,
                            activeTrackColor = theme.primaryAccent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                }

                Button(
                    onClick = {
                        testTtsStatus = "Testing voice synthesis..."
                        viewModel.testMaxNativeTts(
                            text = "नमस्ते! मैं मैक्स हूँ। आपकी आवाज सेटिंग्स सफलतापूर्वक लागू हो गई हैं।"
                        ) { status ->
                            testTtsStatus = status
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primaryAccent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Test Voice",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play Voice Synthesis Test", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                testTtsStatus?.let { status ->
                    Text(
                        text = status,
                        fontSize = 11.sp,
                        color = theme.primaryAccent,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // --- 5. AI ENGINE & API KEYS ---
        SiriGlassCard(theme = theme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val hasApiKey = apiKey.isNotBlank()
                val currentProvider = com.example.ai.ApiProvider.fromId(settings.activeAiProvider)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "API Key",
                            tint = if (hasApiKey) Color(0xFF00E676) else Color(0xFFFFB300),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "${currentProvider.displayName} • ${settings.modelName}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (hasApiKey) "Active & Validated" else "Fallback Mode",
                                fontSize = 11.sp,
                                color = if (hasApiKey) Color(0xFF00E676) else Color(0xFFFFB300)
                            )
                        }
                    }

                    Button(
                        onClick = onOpenApiKeyDialog,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasApiKey) Color.White.copy(alpha = 0.15f) else theme.primaryAccent
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (hasApiKey) "Manage Keys" else "Set Key",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasApiKey) Color.White else Color.Black
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                // Custom AI Instructions
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Custom Assistant Instructions",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f)
                    )

                    OutlinedTextField(
                        value = customInstructionsInput,
                        onValueChange = { newText -> customInstructionsInput = newText },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.primaryAccent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White.copy(alpha = 0.9f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        maxLines = 3,
                        placeholder = { Text("Enter custom behavior instructions...", color = Color.Gray) }
                    )

                    Button(
                        onClick = { viewModel.setCustomInstructions(customInstructionsInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primaryAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text("Save Instructions", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- 6. REAL-TIME BARGE-IN & BI-DIRECTIONAL WEBSOCKET ---
        val isBargeInMonitoring by viewModel.isBargeInMonitoring.collectAsState()
        val isBargeInInterrupted by viewModel.isBargeInInterrupted.collectAsState()
        val bargeInCount by viewModel.bargeInCount.collectAsState()
        val currentMicDb by viewModel.bargeInCurrentMicDb.collectAsState()
        val lastInterruptionReason by viewModel.lastBargeInReason.collectAsState()
        val isWsConnected by viewModel.isRealtimeWebSocketConnected.collectAsState()
        val wsLatencyMs by viewModel.realtimeLatencyMs.collectAsState()

        SiriGlassCard(theme = theme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (settings.isBargeInEnabled) Color(0xFF00E5FF).copy(alpha = 0.2f)
                                    else Color.White.copy(alpha = 0.08f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Barge-in Icon",
                                tint = if (settings.isBargeInEnabled) Color(0xFF00E5FF) else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Real-Time Barge-In (Instant Interruption)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "Interrupt assistant playback immediately when you start speaking",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        }
                    }

                    Switch(
                        checked = settings.isBargeInEnabled,
                        onCheckedChange = { isChecked -> viewModel.setBargeInEnabled(isChecked) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFF00E5FF)
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_barge_in_switch")
                    )
                }

                // WebSocket Full Duplex Streaming Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "WebSocket",
                                tint = if (settings.isRealtimeWebSocketEnabled) theme.primaryAccent else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Bi-Directional WebSocket Streaming",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = if (isWsConnected) "Connected (Latency: ${wsLatencyMs}ms)" else "Low-latency streaming socket connection",
                            fontSize = 10.sp,
                            color = if (isWsConnected) Color(0xFF00E676) else Color.White.copy(alpha = 0.6f)
                        )
                    }

                    Switch(
                        checked = settings.isRealtimeWebSocketEnabled,
                        onCheckedChange = { isChecked -> viewModel.setRealtimeWebSocketEnabled(isChecked) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = theme.primaryAccent
                        ),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                }

                // Sensitivity Slider
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Barge-In Sensitivity (VAD Threshold)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        val sensitivityLabel = when {
                            settings.bargeInSensitivity < 0.4f -> "Low (Loud Only)"
                            settings.bargeInSensitivity < 0.7f -> "Medium"
                            settings.bargeInSensitivity < 0.85f -> "High (Recommended)"
                            else -> "Ultra Responsive"
                        }
                        Text(
                            text = "$sensitivityLabel (${String.format("%.2f", settings.bargeInSensitivity)})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )
                    }

                    Slider(
                        value = settings.bargeInSensitivity,
                        onValueChange = { newSens -> viewModel.setBargeInSensitivity(newSens) },
                        valueRange = 0.2f..1.0f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                }

                // Live Barge-In Telemetry & Diagnostic Status Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isBargeInInterrupted -> Color(0xFFFF5252)
                                                isBargeInMonitoring -> Color(0xFF00E5FF)
                                                else -> Color.Gray
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        isBargeInInterrupted -> "BARGE-IN TRIGGERED"
                                        isBargeInMonitoring -> "VAD ACTIVE (LISTENING ON PLAYBACK)"
                                        else -> "STANDBY (IDLE)"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isBargeInInterrupted -> Color(0xFFFF5252)
                                        isBargeInMonitoring -> Color(0xFF00E5FF)
                                        else -> Color.Gray
                                    }
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$bargeInCount Interrupts",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        if (lastInterruptionReason != null) {
                            Text(
                                text = "Last Interrupted: $lastInterruptionReason",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        if (isBargeInMonitoring) {
                            Text(
                                text = "Live Mic RMS: ${String.format("%.1f dB", currentMicDb)}",
                                fontSize = 10.sp,
                                color = Color(0xFF00E5FF)
                            )
                        }
                    }
                }

                // Interactive Test Barge-In Button
                Button(
                    onClick = { viewModel.simulateBargeInInterrupt() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF).copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = "Simulate Interruption",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Simulate Barge-In Speech Interruption",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ==========================================
// 3. DEDICATED SUB-SCREEN: HARDWARE & AUDIO I/O
// ==========================================
@Composable
private fun HardwareSettingsSubScreen(
    viewModel: AutoResponderViewModel,
    settings: AppSettings,
    theme: SiriThemeColors,
    onBack: () -> Unit
) {
    val soundMode by viewModel.deviceToggleManager.soundMode.collectAsState()
    val isWifiEnabled by viewModel.deviceToggleManager.isWifiEnabled.collectAsState()
    val isTorchOn by viewModel.deviceToggleManager.isFlashlightOn.collectAsState()

    var micDropdownExpanded by remember { mutableStateOf(false) }
    var speakerDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 260.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Navigation Top Bar
        SubScreenTopBar(
            title = "Hardware & Audio I/O",
            subtitle = "Microphones, Speakers, Gain Sensitivity & Equalizer",
            theme = theme,
            onBack = onBack
        )

        // --- 1. MICROPHONE SOURCE SELECTION ---
        SiriGlassCard(theme = theme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Microphone Input Source",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Specify which audio hardware captures hands-free commands and wake-words",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )

                val micSources = listOf(
                    "Built-in Mic (Auto)",
                    "Bluetooth Headset Mic",
                    "External USB Mic",
                    "Multi-Array Noise Cancelling"
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, theme.primaryAccent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .clickable { micDropdownExpanded = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Mic",
                                tint = theme.primaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = settings.microphoneSource,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Select",
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    DropdownMenu(
                        expanded = micDropdownExpanded,
                        onDismissRequest = { micDropdownExpanded = false }
                    ) {
                        micSources.forEach { source ->
                            DropdownMenuItem(
                                text = { Text(source) },
                                onClick = {
                                    viewModel.setMicrophoneSource(source)
                                    micDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- 2. SPEAKER OUTPUT SELECTION ---
        SiriGlassCard(theme = theme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Speaker Audio Output",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Configure default audio routing for voice responses and call announcements",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )

                val speakerOutputs = listOf(
                    "Auto (Speakerphone)",
                    "Earpiece (Private)",
                    "Bluetooth A2DP Audio",
                    "Wired Headphones"
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, theme.secondaryAccent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .clickable { speakerDropdownExpanded = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Speaker",
                                tint = theme.secondaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = settings.speakerOutput,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Select",
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    DropdownMenu(
                        expanded = speakerDropdownExpanded,
                        onDismissRequest = { speakerDropdownExpanded = false }
                    ) {
                        speakerOutputs.forEach { output ->
                            DropdownMenuItem(
                                text = { Text(output) },
                                onClick = {
                                    viewModel.setSpeakerOutput(output)
                                    speakerDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- 3. INPUT SENSITIVITY SLIDER ---
        SiriGlassCard(theme = theme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Microphone Input Sensitivity",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (settings.inputSensitivity > 0.8f) "High gain for distant voice pickup"
                            else if (settings.inputSensitivity > 0.4f) "Standard balanced room sensitivity"
                            else "Low gain (noise rejection mode)",
                            fontSize = 11.sp,
                            color = theme.primaryAccent
                        )
                    }
                    Text(
                        text = "${(settings.inputSensitivity * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.primaryAccent
                    )
                }

                Slider(
                    value = settings.inputSensitivity,
                    onValueChange = { newSens -> viewModel.setInputSensitivity(newSens) },
                    valueRange = 0.1f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = theme.primaryAccent,
                        activeTrackColor = theme.primaryAccent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    )
                )
            }
        }

        // --- 4. EQUALIZER CONTROLS ---
        SiriGlassCard(theme = theme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Audio Output Equalizer",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Presets
                val eqPresets = listOf("Voice Clarity", "Bass Boost", "Treble Crisp", "Studio Flat")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    eqPresets.forEach { preset ->
                        val isSelected = settings.equalizerPreset == preset
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) theme.primaryAccent.copy(alpha = 0.25f)
                                    else Color.White.copy(alpha = 0.07f)
                                )
                                .border(
                                    width = if (isSelected) 1.2.dp else 0.5.dp,
                                    color = if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.setEqualizerPreset(preset) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = preset,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Bass Boost Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bass Level", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                        Text("${(settings.bassBoost * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.primaryAccent)
                    }
                    Slider(
                        value = settings.bassBoost,
                        onValueChange = { viewModel.setBassBoost(it) },
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = theme.primaryAccent,
                            activeTrackColor = theme.primaryAccent
                        )
                    )
                }

                // Treble Boost Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Treble Crispness", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                        Text("${(settings.trebleBoost * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.secondaryAccent)
                    }
                    Slider(
                        value = settings.trebleBoost,
                        onValueChange = { viewModel.setTrebleBoost(it) },
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = theme.secondaryAccent,
                            activeTrackColor = theme.secondaryAccent
                        )
                    )
                }
            }
        }

        // --- 5. SYSTEM DEVICE & TELEPHONY CONTROLS ---
        SiriGlassCard(theme = theme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Device Hardware & Telephony Controls",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Sound & DND Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple(SoundMode.NORMAL, "Normal", Icons.Default.VolumeUp),
                        Triple(SoundMode.VIBRATE, "Vibrate", Icons.Default.Vibration),
                        Triple(SoundMode.SILENT, "Silent/DND", Icons.Default.VolumeMute)
                    ).forEach { (mode, label, icon) ->
                        val isSelected = soundMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) theme.primaryAccent.copy(alpha = 0.25f)
                                    else Color.White.copy(alpha = 0.06f)
                                )
                                .border(
                                    width = if (isSelected) 1.2.dp else 0.5.dp,
                                    color = if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { viewModel.setSoundMode(mode) }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.7f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Wi-Fi & Torch Tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Wi-Fi Tile
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isWifiEnabled) theme.primaryAccent.copy(alpha = 0.18f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .border(
                                width = if (isWifiEnabled) 1.dp else 0.5.dp,
                                color = if (isWifiEnabled) theme.primaryAccent else Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { viewModel.toggleWifi(!isWifiEnabled) }
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = "Wi-Fi",
                                tint = if (isWifiEnabled) theme.primaryAccent else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("Wi-Fi", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(if (isWifiEnabled) "ON" else "OFF", fontSize = 9.sp, color = if (isWifiEnabled) theme.primaryAccent else Color.Gray)
                            }
                        }
                    }

                    // Torch Tile
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isTorchOn) Color(0xFFFFB300).copy(alpha = 0.18f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .border(
                                width = if (isTorchOn) 1.dp else 0.5.dp,
                                color = if (isTorchOn) Color(0xFFFFB300) else Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { viewModel.toggleFlashlight(!isTorchOn) }
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "Torch",
                                tint = if (isTorchOn) Color(0xFFFFB300) else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("Torch", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(if (isTorchOn) "Active" else "OFF", fontSize = 9.sp, color = if (isTorchOn) Color(0xFFFFB300) else Color.Gray)
                            }
                        }
                    }
                }

                // Auto-Speakerphone Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { viewModel.setAutoSpeakerphoneOnAccept(!settings.autoSpeakerphoneOnAccept) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Auto-Speakerphone on Accept", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("Enables loudspeaker automatically when answering via voice", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = settings.autoSpeakerphoneOnAccept,
                        onCheckedChange = { viewModel.setAutoSpeakerphoneOnAccept(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = theme.primaryAccent)
                    )
                }

                // Proximity Flip to Silence
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { viewModel.toggleProximitySensorSilence(!settings.isProximitySensorSilence) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Flip / Proximity Sensor Silence", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("Silences incoming ringers when phone is placed face-down", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = settings.isProximitySensorSilence,
                        onCheckedChange = { viewModel.toggleProximitySensorSilence(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = theme.primaryAccent)
                    )
                }
            }
        }
    }
}

// ==========================================
// 4. DEDICATED SUB-SCREEN: SECURITY & SOS
// ==========================================
@Composable
private fun SecuritySosSettingsSubScreen(
    viewModel: AutoResponderViewModel,
    settings: AppSettings,
    missingPermissions: List<String>,
    theme: SiriThemeColors,
    onBack: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val sosContacts by viewModel.emergencySosManager.sosContacts.collectAsState()
    val isAlarmRinging by viewModel.isAlarmRinging.collectAsState()
    val isMotionArmed by viewModel.isMotionArmed.collectAsState()
    val capturedImages by viewModel.capturedIntruderImages.collectAsState()
    val failedUnlockCount by viewModel.failedUnlockCount.collectAsState()
    val lastFailedTimestamp by viewModel.lastFailedTimestamp.collectAsState()
    val lastSecurityStatus by viewModel.lastSecurityStatus.collectAsState()

    var newContactNumber by remember { mutableStateOf("") }
    var pinInput by remember(settings.securityPin) { mutableStateOf(settings.securityPin) }
    var customSosMessageInput by remember(settings.sosAlertMessage) { mutableStateOf(settings.sosAlertMessage) }
    var selectedImageForDialog by remember { mutableStateOf<File?>(null) }
    var testCaptureMessage by remember { mutableStateOf<String?>(null) }

    // Dialog for viewing captured intruder photograph
    if (selectedImageForDialog != null) {
        val file = selectedImageForDialog!!
        val bitmap = remember(file.absolutePath) {
            BitmapFactory.decodeFile(file.absolutePath)
        }
        val formattedDate = remember(file.lastModified()) {
            SimpleDateFormat("EEE, MMM dd, yyyy - hh:mm:ss a", Locale.getDefault()).format(Date(file.lastModified()))
        }

        Dialog(onDismissRequest = { selectedImageForDialog = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF14141E))
                    .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Photo", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                            Text("Intruder Snapshot", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        IconButton(onClick = { selectedImageForDialog = null }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }

                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Intruder capture photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Unable to preview image file", color = Color.Gray, fontSize = 12.sp)
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Captured: $formattedDate", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text("File: ${file.name}", color = Color.Gray, fontSize = 10.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.deleteIntruderImage(file)
                                selectedImageForDialog = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252).copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete Photo", color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { selectedImageForDialog = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Dismiss", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 260.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Navigation Top Bar
        SubScreenTopBar(
            title = "Security & SOS Settings",
            subtitle = "Anti-Theft Siren, Intruder Selfie, Voice ID & Emergency SOS",
            theme = theme,
            onBack = onBack
        )

        // --- ACTIVE SIREN ALERT BANNER (If alarm is ringing) ---
        if (isAlarmRinging) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFFF1744).copy(alpha = 0.25f))
                    .border(1.5.dp, Color(0xFFFF1744), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF1744)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = "Alarm Active", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("THEFT SIREN ACTIVATED!", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            Text("High-decibel alarm sounding at maximum volume", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                        }
                    }

                    Button(
                        onClick = { viewModel.stopSecurityAlarm() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VolumeMute, contentDescription = "Silence", tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SILENCE & RESET ALARM", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // --- 1. ACTIVE THEFT PROTECTION & INTRUDER SENTINEL ---
        SiriGlassCard(theme = theme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF5252).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = "Shield", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text("Theft Defense & Intruder Sentinel", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Active anti-theft siren & silent background snapshot", fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }

                // Status Strip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Sentinel Status: $lastSecurityStatus", fontSize = 10.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                        if (failedUnlockCount > 0) {
                            Text(text = "$failedUnlockCount Failed Unlock Attempts", fontSize = 10.sp, color = Color(0xFFFFB300), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // TOGGLE 1: Loud Anti-Theft Siren
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { viewModel.setAntiTheftSirenEnabled(!settings.isAntiTheftSirenEnabled) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Loud Anti-Theft Siren", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("Overrides silent/vibrate modes to blast high-pitched 120dB alarm on security triggers", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = settings.isAntiTheftSirenEnabled,
                        onCheckedChange = { viewModel.setAntiTheftSirenEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color(0xFFFF5252))
                    )
                }

                // Test Siren Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isAlarmRinging) {
                        Button(
                            onClick = { viewModel.stopSecurityAlarm() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.VolumeMute, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop Siren Alarm", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.triggerTestSecurityAlarm("User Test") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Test Siren", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sound Test Siren", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

                // TOGGLE 2: Intruder Selfie Camera Capture
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { viewModel.setIntruderSelfieCaptureEnabled(!settings.isIntruderSelfieCaptureEnabled) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Intruder Selfie Photo Capture", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("Silently snaps front camera photo in background without showing preview or flash", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = settings.isIntruderSelfieCaptureEnabled,
                        onCheckedChange = { viewModel.setIntruderSelfieCaptureEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = theme.primaryAccent)
                    )
                }

                if (settings.isIntruderSelfieCaptureEnabled) {
                    // Trigger Threshold Choice
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Failed Unlock Trigger Threshold", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2, 3).forEach { threshold ->
                                val isSelected = settings.failedUnlockThreshold == threshold
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) theme.primaryAccent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
                                        .border(1.dp, if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                        .clickable { viewModel.setFailedUnlockThreshold(threshold) }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$threshold Failed ${if (threshold == 1) "Attempt" else "Attempts"}",
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    // Test Silent Snapshot Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                testCaptureMessage = "Snapping silent front photo..."
                                viewModel.intruderSecurityManager.captureSilentFrontCameraSnapshot(reason = "Manual Test") { file ->
                                    testCaptureMessage = if (file != null) "Silent snapshot captured and saved!" else "Camera permission or hardware error."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primaryAccent.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Test Camera", tint = theme.primaryAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Silent Intruder Snapshot", color = theme.primaryAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    testCaptureMessage?.let { msg ->
                        Text(text = msg, fontSize = 10.sp, color = theme.primaryAccent, modifier = Modifier.padding(start = 4.dp))
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

                // TOGGLE 3: Motion & Pickpocket Sensor
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { viewModel.setMotionDetectionAlarmEnabled(!settings.isMotionDetectionAlarmEnabled) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Motion / Pickpocket Sensor", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isMotionArmed) Color(0xFF00E676).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isMotionArmed) "ARMED" else "OFF",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMotionArmed) Color(0xFF00E676) else Color.Gray
                                )
                            }
                        }
                        Text("Sounds siren and snaps photo if phone is abruptly moved or grabbed while armed", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = settings.isMotionDetectionAlarmEnabled,
                        onCheckedChange = { viewModel.setMotionDetectionAlarmEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = theme.secondaryAccent)
                    )
                }
            }
        }

        // --- 2. INTRUDER PHOTO LOGS & EVIDENCE GALLERY ---
        SiriGlassCard(theme = theme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Gallery", tint = theme.primaryAccent, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Intruder Evidence Gallery",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    if (capturedImages.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFF5252).copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${capturedImages.size} Snapped",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5252)
                                )
                            }
                            IconButton(onClick = { viewModel.clearIntruderLogs() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear All", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                if (capturedImages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Clean", tint = Color(0xFF00E676), modifier = Modifier.size(24.dp))
                            Text("No unauthorized intruder attempts detected", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            Text("Photos captured upon failed unlock or theft motion will appear here.", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                } else {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(capturedImages) { imgFile ->
                            val bmp = remember(imgFile.absolutePath) {
                                BitmapFactory.decodeFile(imgFile.absolutePath)
                            }
                            val dateStr = remember(imgFile.lastModified()) {
                                SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(imgFile.lastModified()))
                            }

                            Box(
                                modifier = Modifier
                                    .size(width = 110.dp, height = 130.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .clickable { selectedImageForDialog = imgFile }
                            ) {
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Intruder snapshot",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = "Photo", tint = Color.Gray)
                                    }
                                }

                                // Date Overlay
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.75f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = dateStr,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 3. DEVICE ADMIN PERMISSIONS HOOK ---
        SiriGlassCard(theme = theme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isAdminActive = remember { viewModel.isDeviceAdminActive() }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Lock, contentDescription = "Admin", tint = if (isAdminActive) Color(0xFF00E676) else Color(0xFFFFB300), modifier = Modifier.size(18.dp))
                        Column {
                            Text("Screen Lock Device Admin", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                if (isAdminActive) "Active: Intercepts lockscreen failed attempts"
                                else "Inactive: Enable to detect wrong PIN / Pattern entries",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isAdminActive) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFFFFB300).copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isAdminActive) "ACTIVE" else "ENABLE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAdminActive) Color(0xFF00E676) else Color(0xFFFFB300)
                        )
                    }
                }

                if (!isAdminActive) {
                    Button(
                        onClick = {
                            try {
                                context.startActivity(viewModel.getDeviceAdminEnableIntent())
                            } catch (e: Exception) {
                                Log.e("SecuritySubScreen", "Error launching device admin intent: ${e.message}")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Activate Device Administrator", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. HELPER COMPOSABLES: NAVIGATION CARDS & BARS
// ==========================================
@Composable
private fun CategoryNavigationCard(
    title: String,
    subtitle: String,
    badges: List<String>,
    icon: ImageVector,
    iconTint: Color,
    theme: SiriThemeColors,
    testTag: String,
    onClick: () -> Unit
) {
    SiriGlassCard(
        theme = theme,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick
            )
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.18f))
                        .border(1.dp, iconTint.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 2
                    )
                    if (badges.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            badges.take(3).forEach { badge ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = badge,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = iconTint
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open $title Screen",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SubScreenTopBar(
    title: String,
    subtitle: String,
    theme: SiriThemeColors,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .testTag("settings_back_btn")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Settings",
                tint = theme.primaryAccent,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1
            )
        }
    }
}
