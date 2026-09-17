package com.example.ui

import com.example.voice.MaxSttState

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.util.Locale
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.core.content.ContextCompat
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.AutoResponderEvent
import com.example.data.repository.AppSettings
import com.example.permissions.PermissionHelper
import com.example.voice.VoiceDetectorState
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

// Theme Colors Config for Siri Glassmorphism
data class SiriThemeColors(
    val name: String,
    val bgGradient: List<Color>,
    val cardBorder: Brush,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val glowColor: Color,
    val orbColors: List<Color>
)

object SiriThemePresets {
    val SiriSpectrum = SiriThemeColors(
        name = "Siri Spectrum",
        bgGradient = listOf(Color(0xFF060913), Color(0xFF0F172A), Color(0xFF070B19)),
        cardBorder = Brush.linearGradient(
            colors = listOf(Color(0x8000F5FF), Color(0x80FF007A), Color(0x809D00FF))
        ),
        primaryAccent = Color(0xFF00F5FF),
        secondaryAccent = Color(0xFFFF007A),
        glowColor = Color(0xFF7B2CBF),
        orbColors = listOf(Color(0xFF00F5FF), Color(0xFFFF007A), Color(0xFF9D00FF), Color(0xFF3A86FF))
    )

    val CyberNeon = SiriThemeColors(
        name = "Cyber Neon",
        bgGradient = listOf(Color(0xFF050B08), Color(0xFF0B1B15), Color(0xFF040A0D)),
        cardBorder = Brush.linearGradient(
            colors = listOf(Color(0x8039FF14), Color(0x8000E5FF), Color(0x80FF0055))
        ),
        primaryAccent = Color(0xFF39FF14),
        secondaryAccent = Color(0xFF00E5FF),
        glowColor = Color(0xFF00FFCC),
        orbColors = listOf(Color(0xFF39FF14), Color(0xFF00E5FF), Color(0xFF00FF99), Color(0xFFFF0055))
    )

    val AuroraEmerald = SiriThemeColors(
        name = "Aurora Emerald",
        bgGradient = listOf(Color(0xFF030E0C), Color(0xFF09231E), Color(0xFF020C0A)),
        cardBorder = Brush.linearGradient(
            colors = listOf(Color(0x8000E676), Color(0x8000B0FF), Color(0x801DE9B6))
        ),
        primaryAccent = Color(0xFF00E676),
        secondaryAccent = Color(0xFF00B0FF),
        glowColor = Color(0xFF1DE9B6),
        orbColors = listOf(Color(0xFF00E676), Color(0xFF00B0FF), Color(0xFF1DE9B6), Color(0xFF64FFDA))
    )

    val SolarGold = SiriThemeColors(
        name = "Solar Gold",
        bgGradient = listOf(Color(0xFF100B03), Color(0xFF241505), Color(0xFF0F0801)),
        cardBorder = Brush.linearGradient(
            colors = listOf(Color(0x80FFB300), Color(0x80FF3D00), Color(0x80FF9100))
        ),
        primaryAccent = Color(0xFFFFB300),
        secondaryAccent = Color(0xFFFF3D00),
        glowColor = Color(0xFFFF6D00),
        orbColors = listOf(Color(0xFFFFB300), Color(0xFFFF3D00), Color(0xFFFF9100), Color(0xFFFFEA00))
    )

    fun getTheme(presetName: String): SiriThemeColors {
        return when (presetName) {
            "Cyber Neon" -> CyberNeon
            "Aurora Emerald" -> AuroraEmerald
            "Solar Gold" -> SolarGold
            else -> SiriSpectrum
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoResponderScreen(viewModel: AutoResponderViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settings.collectAsState()
    val missingPermissions by viewModel.missingPermissions.collectAsState()
    val liveCallStatus by viewModel.liveCallStatus.collectAsState()
    val liveVoiceState by viewModel.liveVoiceState.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val voiceDetectorState by viewModel.voiceDetectorState.collectAsState()
    val rmsDbLevel by viewModel.rmsDbLevel.collectAsState()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsState()
    val events by viewModel.events.collectAsState()
    val callHistoryLogs by viewModel.callHistoryLogs.collectAsState()
    val acceptedCallsCount by viewModel.acceptedCallsCount.collectAsState()
    val rejectedCallsCount by viewModel.rejectedCallsCount.collectAsState()
    val simCallState by viewModel.incomingCallSimState.collectAsState()

    val currentTheme = remember(settings.themePreset) {
        SiriThemePresets.getTheme(settings.themePreset)
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissions()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        modifier = Modifier
            .fillMaxSize()
            .testTag("max_assistant_screen")
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(currentTheme.bgGradient)
                )
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Top Siri Header & Status Bar
                SiriHeaderBar(
                    settings = settings,
                    isServiceRunning = isServiceRunning,
                    liveVoiceState = liveVoiceState,
                    theme = currentTheme,
                    onToggleMaxAssistant = { viewModel.toggleMaxAssistant(it) },
                    onOpenApiKeyDialog = { showApiKeyDialog = true }
                )

                // Theme Preset Chips
                SiriThemeSelectorRow(
                    currentPreset = settings.themePreset,
                    theme = currentTheme,
                    onSelectPreset = { viewModel.setThemePreset(it) }
                )

                // Missing Permission Banner if needed
                if (missingPermissions.isNotEmpty()) {
                    SiriPermissionBanner(
                        missingPermissions = missingPermissions,
                        theme = currentTheme,
                        onRequestPermissions = {
                            permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS)
                        }
                    )
                }

                // Categorized Dashboard Tabs
                SiriCategorizedTabRow(
                    selectedTabIndex = selectedTabIndex,
                    theme = currentTheme,
                    onTabSelected = { selectedTabIndex = it }
                )

                // Tab Content Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (selectedTabIndex) {
                        0 -> VoiceCallAnnouncerTab(viewModel, settings, currentTheme)
                        1 -> WhatsAppControlTab(viewModel, currentTheme)
                        2 -> AutoScrollTab(viewModel, currentTheme)
                        3 -> EmergencySosTab(viewModel, currentTheme)
                        4 -> BlocklistSpamTab(viewModel, settings, currentTheme)
                        5 -> HardwareSystemControlTab(viewModel, settings, currentTheme)
                        6 -> AppMediaControlTab(viewModel, settings, currentTheme)
                        7 -> WorkbenchAndEventsTab(viewModel, settings, events, callHistoryLogs, acceptedCallsCount, rejectedCallsCount, simCallState, currentTheme)
                    }
                }

                // Bottom Padding for Voice Orb
                Spacer(modifier = Modifier.height(100.dp))
            }

            // Floating Animated Siri Glowing Orb at Bottom Center
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
            ) {
                SiriVoiceOrb(
                    isListening = voiceDetectorState is VoiceDetectorState.Listening || simCallState.phase == "LISTENING",
                    isSpeaking = isTtsSpeaking || simCallState.phase == "ANNOUNCING",
                    rmsDbLevel = rmsDbLevel,
                    theme = currentTheme,
                    onClick = {
                        if (voiceDetectorState is VoiceDetectorState.Listening) {
                            viewModel.stopLiveVoiceRecognitionTest()
                        } else if (isTtsSpeaking) {
                            viewModel.stopTtsVoice()
                        } else {
                            viewModel.testTtsVoice("MAX Assistant Active")
                            scope.launch {
                                snackbarHostState.showSnackbar("MAX Voice Orb Activated")
                            }
                        }
                    }
                )
            }
        }
    }

    // API Key Dialog
    if (showApiKeyDialog) {
        ApiKeyConfigDialog(
            viewModel = viewModel,
            theme = currentTheme,
            onDismiss = { showApiKeyDialog = false }
        )
    }
}

@Composable
fun SiriHeaderBar(
    settings: AppSettings,
    isServiceRunning: Boolean,
    liveVoiceState: String,
    theme: SiriThemeColors,
    onToggleMaxAssistant: (Boolean) -> Unit,
    onOpenApiKeyDialog: () -> Unit
) {
    SiriGlassCard(
        theme = theme,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("siri_header_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MAX ASSISTANT",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = theme.primaryAccent,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isServiceRunning) theme.primaryAccent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f))
                            .border(1.dp, if (isServiceRunning) theme.primaryAccent else Color.Gray, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isServiceRunning) "ACTIVE" else "STANDBY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isServiceRunning) theme.primaryAccent else Color.LightGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isServiceRunning) liveVoiceState else "Voice Control Disabled",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenApiKeyDialog,
                    modifier = Modifier.testTag("api_key_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "API Key",
                        tint = theme.secondaryAccent
                    )
                }

                Switch(
                    checked = settings.isMaxAssistantEnabled,
                    onCheckedChange = onToggleMaxAssistant,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = theme.primaryAccent,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.testTag("master_max_switch")
                )
            }
        }
    }
}

@Composable
fun SiriThemeSelectorRow(
    currentPreset: String,
    theme: SiriThemeColors,
    onSelectPreset: (String) -> Unit
) {
    val presets = listOf("Siri Spectrum", "Cyber Neon", "Aurora Emerald", "Solar Gold")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEach { preset ->
            val isSelected = preset == currentPreset
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isSelected) theme.primaryAccent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f)
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 0.5.dp,
                        color = if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.2f),
                        shape = CircleShape
                    )
                    .clickable { onSelectPreset(preset) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
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
}

@Composable
fun SiriPermissionBanner(
    missingPermissions: List<String>,
    theme: SiriThemeColors,
    onRequestPermissions: () -> Unit
) {
    SiriGlassCard(
        theme = theme,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Permission Alert",
                tint = Color(0xFFFFB300),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Permissions Required",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${missingPermissions.size} permission(s) missing for full voice control.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(containerColor = theme.primaryAccent),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Grant", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiriCategorizedTabRow(
    selectedTabIndex: Int,
    theme: SiriThemeColors,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf(
        "Voice Call",
        "WhatsApp",
        "Auto Scroll",
        "Emergency SOS",
        "Blocklist",
        "Hardware",
        "Apps/Media",
        "Workbench"
    )

    PrimaryTabRow(
        selectedTabIndex = selectedTabIndex,
        containerColor = Color.Transparent,
        contentColor = theme.primaryAccent,
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorOffset(selectedTabIndex),
                color = theme.primaryAccent,
                width = 32.dp
            )
        },
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTabIndex == index) theme.primaryAccent else Color.White.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
            )
        }
    }
}

// CATEGORY 1: Voice Call Announcer & Control
@Composable
fun VoiceCallAnnouncerTab(
    viewModel: AutoResponderViewModel,
    settings: AppSettings,
    theme: SiriThemeColors
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SiriSectionHeader(title = "Voice Call Announcer & Control", icon = Icons.Default.RecordVoiceOver, theme = theme)
        }

        item {
            SiriToggleCard(
                title = "Caller Voice Announcer",
                subtitle = "Speak caller name using Text-To-Speech on incoming call",
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                checked = settings.isCallAnnouncerEnabled,
                theme = theme,
                onCheckedChange = { viewModel.toggleCallAnnouncer(it) }
            )
        }

        item {
            SiriToggleCard(
                title = "Voice Command Call Control",
                subtitle = "Listen for 'Accept' or 'Reject' spoken commands",
                icon = Icons.Default.Mic,
                checked = settings.isVoiceCallControlEnabled,
                theme = theme,
                onCheckedChange = { viewModel.toggleVoiceCallControl(it) }
            )
        }

        item {
            SiriToggleCard(
                title = "Auto-Speakerphone on Accept",
                subtitle = "Turn on loudspeaker automatically when answered via voice",
                icon = Icons.Default.PhoneInTalk,
                checked = settings.autoSpeakerphoneOnAccept,
                theme = theme,
                onCheckedChange = { viewModel.setAutoSpeakerphoneOnAccept(it) }
            )
        }

        item {
            SiriGlassCard(theme = theme) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Announcement Voice Tuning",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Speech Rate: ${String.format("%.1fx", settings.ttsSpeechRate)}",
                        fontSize = 12.sp,
                        color = theme.primaryAccent
                    )
                    Slider(
                        value = settings.ttsSpeechRate,
                        onValueChange = { viewModel.setTtsSpeechRate(it) },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = theme.primaryAccent,
                            activeTrackColor = theme.primaryAccent
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Voice Pitch: ${String.format("%.1fx", settings.ttsPitch)}",
                        fontSize = 12.sp,
                        color = theme.secondaryAccent
                    )
                    Slider(
                        value = settings.ttsPitch,
                        onValueChange = { viewModel.setTtsPitch(it) },
                        valueRange = 0.5f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = theme.secondaryAccent,
                            activeTrackColor = theme.secondaryAccent
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.testTtsVoice("Sarah Connor") },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primaryAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Test Voice", tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Preview Caller Announcement", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            var sampleText by remember { mutableStateOf("नमस्ते! मैं मैक्स हूँ। मैं आपकी क्या मदद कर सकता हूँ?") }
            var ttsResultStatus by remember { mutableStateOf<String?>(null) }

            SiriGlassCard(theme = theme) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.RecordVoiceOver, contentDescription = null, tint = theme.primaryAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ElevenLabs Multilingual TTS Engine",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = sampleText,
                        onValueChange = { sampleText = it },
                        label = { Text("Speech Text (Hindi / English)", color = Color.LightGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.primaryAccent,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ttsResultStatus?.let { status ->
                        Text(
                            text = status,
                            fontSize = 11.sp,
                            color = if (status.startsWith("Error")) Color(0xFFFF6B6B) else theme.primaryAccent,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.testElevenLabsVoice(
                                text = sampleText,
                                voiceId = "21m00Tcm4TlvDq8ikWAM"
                            ) { status ->
                                ttsResultStatus = status
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primaryAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generate_elevenlabs_tts_btn")
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play ElevenLabs Audio", tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate & Play ElevenLabs TTS", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            MaxSttVoiceChatCard(viewModel = viewModel, theme = theme)
        }

        item {
            SiriGlassCard(theme = theme) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Voice Command Keywords",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = settings.acceptKeywords,
                        onValueChange = { viewModel.setAcceptKeywords(it) },
                        label = { Text("Accept Phrase Keywords", color = Color.LightGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.primaryAccent,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = settings.rejectKeywords,
                        onValueChange = { viewModel.setRejectKeywords(it) },
                        label = { Text("Reject Phrase Keywords", color = Color.LightGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.secondaryAccent,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// CATEGORY 2: Blocklist & Spam Management
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlocklistSpamTab(
    viewModel: AutoResponderViewModel,
    settings: AppSettings,
    theme: SiriThemeColors
) {
    var newNumberInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SiriSectionHeader(title = "Blocklist & Spam Management", icon = Icons.Default.Block, theme = theme)
        }

        item {
            SiriToggleCard(
                title = "Enable Blocklist Protection",
                subtitle = "Filter unwanted calls and automatic spam rejections",
                icon = Icons.Default.Security,
                checked = settings.isBlocklistEnabled,
                theme = theme,
                onCheckedChange = { viewModel.toggleBlocklist(it) }
            )
        }

        item {
            SiriToggleCard(
                title = "Auto-Reject Known Spam",
                subtitle = "Instantly disconnect calls from flagged spam callers",
                icon = Icons.Default.CallEnd,
                checked = settings.autoRejectSpam,
                theme = theme,
                onCheckedChange = { viewModel.toggleAutoRejectSpam(it) }
            )
        }

        item {
            SiriToggleCard(
                title = "Block Unknown / Private Numbers",
                subtitle = "Block callers with hidden or withheld caller ID",
                icon = Icons.Default.MicOff,
                checked = settings.blockUnknownNumbers,
                theme = theme,
                onCheckedChange = { viewModel.toggleBlockUnknownNumbers(it) }
            )
        }

        item {
            SiriGlassCard(theme = theme) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Blocked Phone Numbers (${settings.blockedNumbers.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newNumberInput,
                            onValueChange = { newNumberInput = it },
                            placeholder = { Text("+1 800-555-xxxx", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = theme.primaryAccent,
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (newNumberInput.isNotBlank()) {
                                    viewModel.addBlockedNumber(newNumberInput)
                                    newNumberInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primaryAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = Color.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Blocked Numbers List Chips
                    if (settings.blockedNumbers.isEmpty()) {
                        Text(
                            text = "No blocked numbers configured.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            settings.blockedNumbers.forEach { num ->
                                SiriChip(
                                    label = num,
                                    theme = theme,
                                    onDelete = { viewModel.removeBlockedNumber(num) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// CATEGORY 3: Hardware & System Control
@Composable
fun HardwareSystemControlTab(
    viewModel: AutoResponderViewModel,
    settings: AppSettings,
    theme: SiriThemeColors
) {
    val context = LocalContext.current
    val isFlashlightOn by viewModel.isFlashlightOn.collectAsState()
    val soundMode by viewModel.soundMode.collectAsState()
    val isWifiEnabled by viewModel.isWifiEnabled.collectAsState()
    val brightnessPercent by viewModel.brightnessPercent.collectAsState()
    val canWriteSettings = remember { viewModel.canWriteSystemSettings() }

    var spokenTestQuery by remember { mutableStateOf("turn on flashlight") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SiriSectionHeader(title = "Mobile Quick Settings Control", icon = Icons.Default.Tune, theme = theme)
        }

        // Quick Settings Toggles Card (Wi-Fi, Sound Mode, Flashlight)
        item {
            SiriGlassCard(theme = theme) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Quick Settings Hardware Toggles",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Control hardware toggles manually or via natural spoken voice commands.",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. Wi-Fi Control Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isWifiEnabled) Icons.Default.Wifi else Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = if (isWifiEnabled) theme.primaryAccent else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Wi-Fi Network", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(if (isWifiEnabled) "Connected / Enabled" else "Disabled", fontSize = 11.sp, color = Color.Gray)
                            }
                        }

                        Button(
                            onClick = { viewModel.toggleWifi(!isWifiEnabled) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isWifiEnabled) theme.primaryAccent else Color.DarkGray
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("toggle_wifi_btn")
                        ) {
                            Text(
                                text = if (isWifiEnabled) "ON" else "OFF",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isWifiEnabled) Color.Black else Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Flashlight / Torch Control Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = if (isFlashlightOn) Color(0xFFFFD600) else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Flashlight / Torch", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(if (isFlashlightOn) "Active / Shining" else "Off", fontSize = 11.sp, color = Color.Gray)
                            }
                        }

                        Button(
                            onClick = { viewModel.toggleFlashlight(!isFlashlightOn) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFlashlightOn) Color(0xFFFFD600) else Color.DarkGray
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("toggle_flashlight_btn")
                        ) {
                            Text(
                                text = if (isFlashlightOn) "ON" else "OFF",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Sound Mode Selector (Normal, Vibrate, Silent)
                    Text("Device Sound Mode", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val modes = listOf(
                            Triple(com.example.toggle.SoundMode.NORMAL, "Ring", Icons.AutoMirrored.Filled.VolumeUp),
                            Triple(com.example.toggle.SoundMode.VIBRATE, "Vibrate", Icons.Default.Vibration),
                            Triple(com.example.toggle.SoundMode.SILENT, "Silent", Icons.Default.VolumeMute)
                        )

                        modes.forEach { (mode, label, icon) ->
                            val isSelected = soundMode == mode
                            Button(
                                onClick = { viewModel.setSoundMode(mode) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) theme.secondaryAccent else Color.White.copy(alpha = 0.08f)
                                ),
                                border = if (isSelected) BorderStroke(1.dp, theme.secondaryAccent) else null,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sound_mode_${label.lowercase()}")
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.Black else Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Screen Brightness Control Card
        item {
            SiriGlassCard(theme = theme) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.BrightnessMedium, contentDescription = null, tint = theme.primaryAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Screen Brightness Control", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Text(
                            text = "$brightnessPercent%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.primaryAccent
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Adjust screen brightness level via slider, quick presets, or spoken voice commands.",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )

                    if (!canWriteSettings) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFF9800).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFFFF9800)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "System Write Permission Required",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF9800)
                                )
                                Text(
                                    text = "To change system screen brightness, grant 'Modify System Settings' permission.",
                                    fontSize = 10.sp,
                                    color = Color.LightGray
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = { viewModel.openWriteSettingsPermission(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Grant System Settings Permission", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Brightness Slider
                    Slider(
                        value = brightnessPercent.toFloat(),
                        onValueChange = { viewModel.setScreenBrightness(it.toInt()) },
                        valueRange = 5f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = theme.primaryAccent,
                            activeTrackColor = theme.primaryAccent,
                            inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.testTag("brightness_slider")
                    )

                    // Quick Brightness Presets
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val presets = listOf(
                            Pair("Dim 15%", 15),
                            Pair("Medium 50%", 50),
                            Pair("High 80%", 80),
                            Pair("Max 100%", 100)
                        )

                        presets.forEach { (label, value) ->
                            OutlinedButton(
                                onClick = { viewModel.setScreenBrightness(value) },
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (brightnessPercent == value) theme.primaryAccent else Color.Gray),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("preset_brightness_$value")
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 9.sp,
                                    color = if (brightnessPercent == value) theme.primaryAccent else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Voice Toggle Integration & Interactive Workbench Card
        item {
            SiriGlassCard(theme = theme) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = theme.secondaryAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Spoken Voice Toggle Assistant", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Supported spoken triggers in English & Hindi:",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val voiceExamples = listOf(
                        "• Flashlight: \"Turn on flashlight\", \"Torch off\", \"फ़्लैशलाइट ऑन\"",
                        "• Wi-Fi: \"Wi-Fi on\", \"Turn off wifi\", \"वाईफाई बंद\"",
                        "• Sound: \"Silent mode\", \"Vibrate mode\", \"Ring mode\", \"Mute phone\"",
                        "• Brightness: \"Set brightness to 80%\", \"Dim screen\", \"Max brightness\""
                    )

                    voiceExamples.forEach { ex ->
                        Text(text = ex, fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f), lineHeight = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = spokenTestQuery,
                        onValueChange = { spokenTestQuery = it },
                        label = { Text("Simulate Spoken Voice Command", color = Color.LightGray, fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.secondaryAccent,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.processSttUserQuery(spokenTestQuery) },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.secondaryAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_voice_toggle_cmd_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Execute Spoken Toggle Command", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            SiriToggleCard(
                title = "Bluetooth Headset Routing",
                subtitle = "Route voice recognition & announcements via Bluetooth mic/AirPods",
                icon = Icons.Default.Bluetooth,
                checked = settings.isBluetoothVoiceControl,
                theme = theme,
                onCheckedChange = { viewModel.toggleBluetoothVoiceControl(it) }
            )
        }

        item {
            SiriToggleCard(
                title = "Flashlight Strobe Alert",
                subtitle = "Flash camera LED on incoming calls when in silent mode",
                icon = Icons.Default.FlashOn,
                checked = settings.isFlashlightAlerts,
                theme = theme,
                onCheckedChange = { viewModel.toggleFlashlightAlerts(it) }
            )
        }

        item {
            SiriToggleCard(
                title = "Haptic Command Feedback",
                subtitle = "Tactile vibration bump when MAX detects voice commands",
                icon = Icons.Default.Vibration,
                checked = settings.isHapticFeedbackEnabled,
                theme = theme,
                onCheckedChange = { viewModel.toggleHapticFeedback(it) }
            )
        }

        item {
            SiriToggleCard(
                title = "Proximity Sensor Auto-Silence",
                subtitle = "Silence call ringer automatically when phone is turned face down",
                icon = Icons.Default.Sensors,
                checked = settings.isProximitySensorSilence,
                theme = theme,
                onCheckedChange = { viewModel.toggleProximitySensorSilence(it) }
            )
        }
    }
}

// CATEGORY 4: App & Media Control (YouTube, WhatsApp)
@Composable
fun AppMediaControlTab(
    viewModel: AutoResponderViewModel,
    settings: AppSettings,
    theme: SiriThemeColors
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SiriSectionHeader(title = "App & Media Control", icon = Icons.Default.AppShortcut, theme = theme)
        }

        item {
            SiriToggleCard(
                title = "YouTube Video Auto-Pause",
                subtitle = "Pause YouTube videos automatically when an incoming call rings",
                icon = Icons.Default.VideoLibrary,
                checked = settings.isYouTubeMediaAutoPause,
                theme = theme,
                onCheckedChange = { viewModel.toggleYouTubeMediaAutoPause(it) }
            )
        }

        item {
            SiriToggleCard(
                title = "WhatsApp Voice Assistant",
                subtitle = "Auto-read incoming WhatsApp notifications and speak response",
                icon = Icons.Default.Sms,
                checked = settings.isWhatsAppAutoRead,
                theme = theme,
                onCheckedChange = { viewModel.toggleWhatsAppAutoRead(it) }
            )
        }

        item {
            SiriToggleCard(
                title = "Spotify & Music Audio Ducking",
                subtitle = "Lower background music volume during MAX voice interaction",
                icon = Icons.Default.MusicNote,
                checked = settings.isSpotifyAutoDucking,
                theme = theme,
                onCheckedChange = { viewModel.toggleSpotifyAutoDucking(it) }
            )
        }
    }
}

// CATEGORY 5: Workbench & Event Logs
@Composable
fun WorkbenchAndEventsTab(
    viewModel: AutoResponderViewModel,
    settings: AppSettings,
    events: List<AutoResponderEvent>,
    callHistoryLogs: List<com.example.data.db.CallHistoryLog>,
    acceptedCount: Int,
    rejectedCount: Int,
    simCallState: IncomingCallSimState,
    theme: SiriThemeColors
) {
    val dateFormat = remember { java.text.SimpleDateFormat("MMM dd, HH:mm:ss", java.util.Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SiriSectionHeader(title = "Room Database & Call Logs", icon = Icons.Default.PhoneInTalk, theme = theme)
        }

        // Room Call History Card
        item {
            SiriGlassCard(theme = theme) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Assistant Call History (Room DB)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Stored locally in SQLite database",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }

                        if (callHistoryLogs.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearCallHistoryLogs() }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Clear Call History",
                                    tint = Color.Gray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Summary Stats Badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.primaryAccent.copy(alpha = 0.15f))
                                .padding(vertical = 8.dp, horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${callHistoryLogs.size}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.primaryAccent
                                )
                                Text("Total Calls", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF00E676).copy(alpha = 0.15f))
                                .padding(vertical = 8.dp, horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$acceptedCount",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E676)
                                )
                                Text("Accepted", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFF5252).copy(alpha = 0.15f))
                                .padding(vertical = 8.dp, horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$rejectedCount",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5252)
                                )
                                Text("Rejected/Blocked", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }

        // Call History Items
        if (callHistoryLogs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No calls logged in Room database yet. Calls answered or rejected by MAX will appear here.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(callHistoryLogs) { log ->
                SiriCallHistoryLogItem(log = log, dateFormat = dateFormat, theme = theme)
            }
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
            SiriSectionHeader(title = "Testing Workbench & Siri Events", icon = Icons.Default.AutoAwesome, theme = theme)
        }

        item {
            SiriGlassCard(theme = theme) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Live Siri Call Simulation",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Trigger a full incoming call cycle with caller announcement & voice recognition.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { viewModel.simulateIncomingCallWithVoiceAssistant("Sarah Connor", "+1 555-0199") },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primaryAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Call, contentDescription = "Simulate", tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simulate Call", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        if (simCallState.isRinging) {
                            OutlinedButton(
                                onClick = { viewModel.endIncomingCallSimulation() },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("End Call", color = Color.Red)
                            }
                        }
                    }

                    if (simCallState.isRinging) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("Call Phase: ${simCallState.phase}", fontSize = 12.sp, color = theme.primaryAccent, fontWeight = FontWeight.Bold)
                                Text(simCallState.statusText, fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Event Logs (${events.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (events.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearHistory() }) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Clear", tint = Color.Gray)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Logs", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }

        if (events.isEmpty()) {
            item {
                Text(
                    text = "No events logged yet. Trigger a simulated call to populate logs.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(20.dp)
                )
            }
        } else {
            items(events) { event ->
                SiriEventLogItem(event = event, theme = theme)
            }
        }
    }
}

// Reusable UI Components
@Composable
fun SiriGlassCard(
    theme: SiriThemeColors,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0x1A12182A))
            .border(
                border = Stroke(width = 1.2f).let {
                    androidx.compose.foundation.BorderStroke(1.dp, theme.cardBorder)
                },
                shape = RoundedCornerShape(22.dp)
            )
    ) {
        content()
    }
}

@Composable
fun SiriSectionHeader(
    title: String,
    icon: ImageVector,
    theme: SiriThemeColors
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = theme.primaryAccent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
fun SiriToggleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    theme: SiriThemeColors,
    onCheckedChange: (Boolean) -> Unit
) {
    SiriGlassCard(theme = theme) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (checked) theme.primaryAccent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (checked) theme.primaryAccent else Color.Gray,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = theme.primaryAccent,
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.DarkGray
                )
            )
        }
    }
}

@Composable
fun SiriChip(
    label: String,
    theme: SiriThemeColors,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, theme.primaryAccent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, fontSize = 12.sp, color = Color.White)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete",
                tint = Color.LightGray,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onDelete() }
            )
        }
    }
}

@Composable
fun SiriCallHistoryLogItem(
    log: com.example.data.db.CallHistoryLog,
    dateFormat: java.text.SimpleDateFormat,
    theme: SiriThemeColors
) {
    val badgeColor = when (log.actionTaken) {
        "ACCEPTED" -> Color(0xFF00E676)
        "REJECTED" -> Color(0xFFFF5252)
        "BLOCKED_AUTO_REJECTED" -> Color(0xFFFFAB00)
        else -> theme.primaryAccent
    }

    SiriGlassCard(theme = theme) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (log.actionTaken) {
                        "ACCEPTED" -> Icons.Default.Call
                        "REJECTED" -> Icons.Default.CallEnd
                        "BLOCKED_AUTO_REJECTED" -> Icons.Default.Block
                        else -> Icons.Default.PhoneInTalk
                    },
                    contentDescription = log.actionTaken,
                    tint = badgeColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (log.callerName.isNotBlank()) log.callerName else log.phoneNumber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeColor.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = log.actionTaken,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = log.phoneNumber,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )

                if (!log.voiceCommandUsed.isNullOrEmpty()) {
                    Text(
                        text = "Triggered by: '${log.voiceCommandUsed}'",
                        fontSize = 10.sp,
                        color = theme.primaryAccent
                    )
                }

                Text(
                    text = dateFormat.format(java.util.Date(log.timestamp)),
                    fontSize = 9.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun SiriEventLogItem(
    event: AutoResponderEvent,
    theme: SiriThemeColors
) {
    SiriGlassCard(theme = theme) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.eventType,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.primaryAccent
                )
                Text(
                    text = DateFormat.format("hh:mm a", event.timestamp).toString(),
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = event.senderOrNumber,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            if (event.generatedReply != null) {
                Text(
                    text = "Reply: ${event.generatedReply}",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// ANIMATED Siri Voice Orb Canvas Component
@Composable
fun SiriVoiceOrb(
    isListening: Boolean,
    isSpeaking: Boolean,
    rmsDbLevel: Float,
    theme: SiriThemeColors,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "siri_orb_anim")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = if (isListening || isSpeaking) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 600 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    val waveHeight = if (isListening) (rmsDbLevel / 10f).coerceIn(1f, 3f) else 1f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .testTag("siri_voice_orb_container")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        // Equalizer waveform bars when active
        if (isListening || isSpeaking) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.height(24.dp)
            ) {
                repeat(5) { i ->
                    val barScale by infiniteTransition.animateFloat(
                        initialValue = 0.2f,
                        targetValue = (0.5f + (i % 3) * 0.25f) * waveHeight,
                        animationSpec = infiniteRepeatable(
                            animation = tween(300 + i * 100, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "bar_$i"
                    )

                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height((24 * barScale).dp)
                            .clip(CircleShape)
                            .background(theme.primaryAccent)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }

        // Multi-Layered Glowing AI Orb
        Box(
            modifier = Modifier
                .size(70.dp)
                .scale(pulseScale),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2.2f

                // Outer Radial Glow Aura
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            theme.glowColor.copy(alpha = 0.7f),
                            theme.primaryAccent.copy(alpha = 0.3f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius * 1.4f
                    ),
                    radius = radius * 1.3f
                )

                // Rotating Siri Gradient Core
                rotate(rotationAngle, center) {
                    theme.orbColors.forEachIndexed { index, color ->
                        val angle = (index * (360f / theme.orbColors.size)) * (Math.PI / 180f)
                        val offsetX = center.x + (cos(angle) * 12f).toFloat()
                        val offsetY = center.y + (sin(angle) * 12f).toFloat()

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(color, color.copy(alpha = 0.2f), Color.Transparent),
                                center = Offset(offsetX, offsetY),
                                radius = radius * 0.8f
                            ),
                            radius = radius * 0.75f,
                            center = Offset(offsetX, offsetY)
                        )
                    }
                }

                // Inner Bright Specular Center
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = radius * 0.35f,
                    center = center
                )
            }
        }
    }
}

@Composable
fun ApiKeyConfigDialog(
    viewModel: AutoResponderViewModel,
    theme: SiriThemeColors,
    onDismiss: () -> Unit
) {
    val geminiKey by viewModel.apiKeyText.collectAsState()
    val elevenLabsKey by viewModel.elevenLabsApiKeyText.collectAsState()
    val isValidatingKey by viewModel.isValidatingElevenLabsKey.collectAsState()
    val validationStatus by viewModel.elevenLabsValidationStatus.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = ElevenLabs, 1 = Gemini
    var tempGeminiKey by remember { mutableStateOf(geminiKey) }
    var tempElevenLabsKey by remember { mutableStateOf(elevenLabsKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = theme.primaryAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("API Keys Manager", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Tab selector for ElevenLabs / Gemini
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        label = { Text("ElevenLabs API", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = theme.primaryAccent,
                            selectedLabelColor = Color.Black,
                            containerColor = Color.White.copy(alpha = 0.08f),
                            labelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        label = { Text("Gemini AI API", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = theme.secondaryAccent,
                            selectedLabelColor = Color.Black,
                            containerColor = Color.White.copy(alpha = 0.08f),
                            labelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (activeTab == 0) {
                    // ElevenLabs Section
                    Text(
                        "Input your ElevenLabs API Key to generate ultra-realistic multilingual TTS voice responses.",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = tempElevenLabsKey,
                        onValueChange = { tempElevenLabsKey = it },
                        placeholder = { Text("xi-api-key...", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.primaryAccent,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("elevenlabs_api_key_input")
                    )

                    if (isValidatingKey) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = theme.primaryAccent,
                                strokeWidth = 2.dp
                            )
                            Text(
                                "Validating API key with ElevenLabs...",
                                fontSize = 11.sp,
                                color = theme.primaryAccent
                            )
                        }
                    }

                    validationStatus?.let { status ->
                        Spacer(modifier = Modifier.height(10.dp))
                        val isSuccess = status.contains("validated", ignoreCase = true) || status.contains("success", ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSuccess) Color(0xFF064E3B) else Color(0xFF7F1D1D),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = status,
                                fontSize = 11.sp,
                                color = if (isSuccess) Color(0xFF6EE7B7) else Color(0xFFFCA5A5),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                viewModel.validateAndSaveElevenLabsApiKey(tempElevenLabsKey)
                            },
                            enabled = !isValidatingKey && tempElevenLabsKey.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primaryAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("validate_elevenlabs_key_btn")
                        ) {
                            Text("Validate & Save", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.clearElevenLabsApiKey()
                                tempElevenLabsKey = ""
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Clear", color = Color.LightGray, fontSize = 12.sp)
                        }
                    }
                } else {
                    // Gemini Section
                    Text(
                        "Enter your Gemini API key for AI response generation and context reasoning:",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = tempGeminiKey,
                        onValueChange = { tempGeminiKey = it },
                        placeholder = { Text("AIzaSy...", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.secondaryAccent,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_api_key_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            viewModel.saveApiKey(tempGeminiKey)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.secondaryAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Gemini Key", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
fun MaxSttVoiceChatCard(
    viewModel: AutoResponderViewModel,
    theme: SiriThemeColors
) {
    val context = LocalContext.current
    val sttState by viewModel.sttState.collectAsState()
    val rmsDbLevel by viewModel.sttRmsDbLevel.collectAsState()
    val partialText by viewModel.sttPartialText.collectAsState()
    val pipelineStatus by viewModel.sttPipelineStatus.collectAsState()
    val conversationLog by viewModel.sttConversationLog.collectAsState()

    var selectedLanguage by remember { mutableStateOf("hi-IN") } // "hi-IN" or "en-US"

    // Permission launcher for RECORD_AUDIO
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startSttAssistantListening(selectedLanguage)
        } else {
            viewModel.refreshPermissions()
        }
    }

    val isListening = sttState is MaxSttState.Listening

    // Pulsating animation for Mic button when listening
    val infiniteTransition = rememberInfiniteTransition(label = "stt_mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stt_mic_scale"
    )

    SiriGlassCard(theme = theme) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = theme.primaryAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Native STT Voice Assistant",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Language Chips
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = selectedLanguage == "hi-IN",
                        onClick = { selectedLanguage = "hi-IN" },
                        label = { Text("हिंदी (hi-IN)", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = theme.primaryAccent,
                            selectedLabelColor = Color.Black,
                            containerColor = Color.White.copy(alpha = 0.08f),
                            labelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = selectedLanguage == "en-US",
                        onClick = { selectedLanguage = "en-US" },
                        label = { Text("English (en-US)", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = theme.primaryAccent,
                            selectedLabelColor = Color.Black,
                            containerColor = Color.White.copy(alpha = 0.08f),
                            labelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Interactive STT Mic Control Area
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(80.dp)
                ) {
                    // Pulsating outer glow ring when active
                    if (isListening) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(theme.primaryAccent.copy(alpha = 0.3f))
                        )
                    }

                    // Interactive Mic Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(if (isListening) theme.secondaryAccent else theme.primaryAccent)
                            .clickable {
                                if (isListening) {
                                    viewModel.stopSttAssistantListening()
                                } else {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        viewModel.startSttAssistantListening(selectedLanguage)
                                    } else {
                                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            }
                            .testTag("stt_mic_button")
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop Listening" else "Start Listening",
                            tint = Color.Black,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Visual State & Equalizer Indicator
                Text(
                    text = when (val state = sttState) {
                        is MaxSttState.Idle -> "Tap Microphone to Speak to MAX"
                        is MaxSttState.Preparing -> "Initializing Speech Recognition..."
                        is MaxSttState.Listening -> state.message
                        is MaxSttState.Recognized -> "Speech Recognized!"
                        is MaxSttState.Error -> state.message
                        else -> "Tap Microphone to Speak to MAX"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (sttState is MaxSttState.Error) Color(0xFFFF6B6B) else theme.primaryAccent
                )

                // Equalizer wave bars during live listening
                if (isListening) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.height(20.dp)
                    ) {
                        val waveScale = (rmsDbLevel / 10f).coerceIn(0.2f, 2.5f)
                        repeat(7) { i ->
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height((16 * (0.3f + (i % 3) * 0.3f) * waveScale).dp)
                                    .clip(CircleShape)
                                    .background(theme.primaryAccent)
                            )
                        }
                    }
                }

                // Live Partial Recognition Text Preview
                if (partialText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "\"$partialText\"",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Automated Data Flow Status Banner
            pipelineStatus?.let { status ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(0.5.dp, theme.primaryAccent.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = theme.primaryAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = status,
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Voice Chat History
            if (conversationLog.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Voice Conversation History",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    conversationLog.takeLast(4).forEach { (userQuery, aiReply) ->
                        // User Speech Bubble (Right aligned)
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp, 12.dp, 0.dp, 12.dp),
                                color = theme.primaryAccent.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, theme.primaryAccent.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = userQuery,
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        // Gemini AI + ElevenLabs Answer Bubble (Left aligned)
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp),
                                color = Color.White.copy(alpha = 0.1f),
                                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RecordVoiceOver,
                                        contentDescription = null,
                                        tint = theme.secondaryAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = aiReply,
                                        fontSize = 12.sp,
                                        color = Color.White
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

@Composable
fun WhatsAppControlTab(
    viewModel: AutoResponderViewModel,
    theme: SiriThemeColors
) {
    val context = LocalContext.current
    val messages by viewModel.whatsAppMessages.collectAsState()
    val status by viewModel.whatsAppStatus.collectAsState()
    val isNotifGranted = viewModel.isNotificationListenerGranted()

    var testSender by remember { mutableStateOf("Rahul Sharma") }
    var testMessage by remember { mutableStateOf("Hey, are you free for the meeting at 4 PM?") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Notification Listener Permission Header Card
        item {
            SiriGlassCard(theme = theme) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = null,
                                tint = if (isNotifGranted) Color(0xFF25D366) else Color(0xFFFFB300)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "WhatsApp Notification Listener",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isNotifGranted) Color(0xFF25D366).copy(alpha = 0.2f) else Color(0xFFFFB300).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, if (isNotifGranted) Color(0xFF25D366) else Color(0xFFFFB300))
                        ) {
                            Text(
                                text = if (isNotifGranted) "ACTIVE" else "PERMISSION NEEDED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isNotifGranted) Color(0xFF25D366) else Color(0xFFFFB300),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isNotifGranted)
                            "MAX is listening for incoming WhatsApp notifications to read them aloud & send voice/AI replies."
                        else
                            "To intercept WhatsApp notifications and send voice replies, MAX requires Notification Listener access in Android settings.",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )

                    if (!isNotifGranted) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.openNotificationListenerSettings(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primaryAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("enable_notif_access_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant Notification Listener Permission", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Live Status Banner
        status?.let { st ->
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, theme.primaryAccent.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = theme.primaryAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = st, fontSize = 11.sp, color = Color.White, lineHeight = 15.sp)
                    }
                }
            }
        }

        // Intercepted WhatsApp Messages List Section
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Intercepted Messages (${messages.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (messages.isNotEmpty()) {
                    TextButton(onClick = { viewModel.whatsAppManager.clearMessages() }) {
                        Text("Clear All", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }

        if (messages.isEmpty()) {
            item {
                SiriGlassCard(theme = theme) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.Sms, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No intercepted WhatsApp messages yet.", fontSize = 13.sp, color = Color.Gray)
                            Text("Incoming WhatsApp messages will appear here automatically.", fontSize = 11.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
        } else {
            items(messages, key = { it.id }) { msg ->
                SiriGlassCard(theme = theme) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF25D366).copy(alpha = 0.2f))
                                ) {
                                    Icon(imageVector = Icons.Default.Chat, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(text = msg.sender, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(text = DateFormat.format("hh:mm a", msg.timestamp).toString(), fontSize = 10.sp, color = Color.Gray)
                                }
                            }

                            if (msg.replyAction != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = theme.primaryAccent.copy(alpha = 0.15f)
                                ) {
                                    Text("Replyable", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.primaryAccent, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "\"${msg.text}\"",
                            fontSize = 13.sp,
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(10.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Action Buttons: Read Aloud | Dictate Voice Reply | AI Auto-Reply
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // 1. Read Aloud
                            Button(
                                onClick = { viewModel.readAloudWhatsAppMessage(msg) },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.primaryAccent.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, theme.primaryAccent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = theme.primaryAccent, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Read Aloud", fontSize = 10.sp, color = theme.primaryAccent)
                            }

                            // 2. Dictate Voice Reply
                            Button(
                                onClick = { viewModel.startDictatingWhatsAppReply(msg.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.secondaryAccent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Voice Reply", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }

                            // 3. AI Reply & Send
                            Button(
                                onClick = { viewModel.generateAiWhatsAppReplyAndSend(msg.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.primaryAccent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("AI Reply", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Simulation / Testing Workbench for WhatsApp Control
        item {
            SiriGlassCard(theme = theme) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = theme.secondaryAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("WhatsApp Test Workbench", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Simulate an incoming WhatsApp notification to verify Read Aloud, Voice-to-Text Replies, and AI direct response.", fontSize = 11.sp, color = Color.LightGray)

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = testSender,
                        onValueChange = { testSender = it },
                        label = { Text("Sender Name", color = Color.LightGray, fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.secondaryAccent,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = testMessage,
                        onValueChange = { testMessage = it },
                        label = { Text("WhatsApp Message Text", color = Color.LightGray, fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.secondaryAccent,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            viewModel.whatsAppManager.onMessageReceived(
                                sender = testSender,
                                text = testMessage,
                                packageName = "com.whatsapp",
                                notificationKey = "simulated_key_${System.currentTimeMillis()}",
                                replyAction = null
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.secondaryAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("simulate_whatsapp_msg_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simulate Incoming WhatsApp Message", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AutoScrollTab(
    viewModel: AutoResponderViewModel,
    theme: SiriThemeColors
) {
    val context = LocalContext.current
    val isAccessibilityConnected by viewModel.isAccessibilityConnected.collectAsState()
    val isAutoScrolling by viewModel.isAutoScrolling.collectAsState()
    val autoScrollSpeedMs by viewModel.autoScrollSpeedMs.collectAsState()
    val accessibilityStatus by viewModel.accessibilityStatus.collectAsState()

    var testAutoTypeText by remember { mutableStateOf("Great video! Thanks for sharing!") }
    var voiceTestTrigger by remember { mutableStateOf("scroll down") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Accessibility Service Permission Card
        item {
            SiriGlassCard(theme = theme) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = null,
                                tint = if (isAccessibilityConnected) theme.primaryAccent else Color(0xFFFFB300)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Accessibility Service Status",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isAccessibilityConnected) theme.primaryAccent.copy(alpha = 0.2f) else Color(0xFFFFB300).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, if (isAccessibilityConnected) theme.primaryAccent else Color(0xFFFFB300))
                        ) {
                            Text(
                                text = if (isAccessibilityConnected) "ACTIVE" else "PERMISSION NEEDED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAccessibilityConnected) theme.primaryAccent else Color(0xFFFFB300),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isAccessibilityConnected)
                            "MAX Accessibility Service is connected and listening for spoken gesture commands & auto-typing requests."
                        else
                            "To enable hands-free screen scrolling and voice auto-typing in social feeds & forms, grant Accessibility permission.",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )

                    if (!isAccessibilityConnected) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.openAccessibilitySettings(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primaryAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("enable_accessibility_access_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Enable Accessibility Service in Settings", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 2. Action Status Banner
        accessibilityStatus?.let { status ->
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, theme.primaryAccent.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.TouchApp, contentDescription = null, tint = theme.primaryAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = status, fontSize = 11.sp, color = Color.White, lineHeight = 15.sp)
                    }
                }
            }
        }

        // 3. Screen Auto-Scrolling Controller Card
        item {
            SiriGlassCard(theme = theme) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.SwapVert, contentDescription = null, tint = theme.secondaryAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Screen Auto Scroll Controls", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        if (isAutoScrolling) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF25D366).copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, Color(0xFF25D366))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("AUTO-SCROLLING", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF25D366))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Automatically scroll through Instagram Reels, YouTube Shorts, Twitter/X feeds, and web pages hands-free.",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Speed Presets
                    Text("Auto-Scroll Speed / Interval", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val speeds = listOf(
                            Pair("Fast (1s)", 1000L),
                            Pair("Medium (2s)", 2000L),
                            Pair("Slow (3s)", 3000L)
                        )

                        speeds.forEach { (label, speedMs) ->
                            val isSelected = autoScrollSpeedMs == speedMs
                            OutlinedButton(
                                onClick = { viewModel.startAutoScroll(speedMs) },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSelected) theme.primaryAccent else Color.Gray),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scroll_speed_$speedMs")
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    color = if (isSelected) theme.primaryAccent else Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons Row: Scroll Up | Scroll Down | Continuous Auto Scroll
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { viewModel.performScrollUp() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scroll_up_btn")
                        ) {
                            Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scroll Up", fontSize = 10.sp, color = Color.White)
                        }

                        Button(
                            onClick = { viewModel.performScrollDown() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scroll_down_btn")
                        ) {
                            Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scroll Down", fontSize = 10.sp, color = Color.White)
                        }

                        if (isAutoScrolling) {
                            Button(
                                onClick = { viewModel.stopAutoScroll() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("stop_auto_scroll_btn")
                            ) {
                                Icon(imageVector = Icons.Default.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stop Scroll", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.startAutoScroll() },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.secondaryAccent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("start_auto_scroll_btn")
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Auto Scroll", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 4. Auto-type Dispatcher Card
        item {
            SiriGlassCard(theme = theme) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Keyboard, contentDescription = null, tint = theme.primaryAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Auto-type Text Dispatcher", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Simulate typing actions into focused comment fields, search inputs, and forms when prompted via speech or manual trigger.",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = testAutoTypeText,
                        onValueChange = { testAutoTypeText = it },
                        label = { Text("Text to Auto-type into Active Field", color = Color.LightGray, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.primaryAccent,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auto_type_input_field")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.autoTypeInFocusedField(testAutoTypeText) },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primaryAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dispatch_auto_type_btn")
                    ) {
                        Icon(imageVector = Icons.Default.TouchApp, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Dispatch Auto-Type into Active Screen Field", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // 5. Voice Trigger Examples & Simulator Workbench
        item {
            SiriGlassCard(theme = theme) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = theme.secondaryAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Voice Trigger Commands & Workbench", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Supported spoken triggers in English & Hindi:",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val accessibilityExamples = listOf(
                        "• Scroll Down: \"Scroll down\", \"Next page\", \"Swipe down\", \"स्क्रॉल डाउन\", \"नीचे करो\"",
                        "• Scroll Up: \"Scroll up\", \"Previous page\", \"Swipe up\", \"स्क्रॉल अप\", \"ऊपर करो\"",
                        "• Auto Scroll: \"Auto scroll\", \"Start scrolling\", \"Continuous scroll\", \"ऑटो स्क्रॉल\"",
                        "• Stop Scroll: \"Stop scroll\", \"Pause scroll\", \"Halt scroll\", \"स्क्रॉल रोको\"",
                        "• Auto Type: \"Type Great video!\", \"Write Hello World\", \"टाइप करो नमस्कार\""
                    )

                    accessibilityExamples.forEach { ex ->
                        Text(text = ex, fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f), lineHeight = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = voiceTestTrigger,
                        onValueChange = { voiceTestTrigger = it },
                        label = { Text("Simulate Spoken Accessibility Command", color = Color.LightGray, fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.secondaryAccent,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.processSttUserQuery(voiceTestTrigger) },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.secondaryAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_accessibility_voice_cmd_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Execute Spoken Accessibility Command", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun EmergencySosTab(
    viewModel: AutoResponderViewModel,
    theme: SiriThemeColors
) {
    val context = LocalContext.current
    val currentLocation by viewModel.currentLocation.collectAsState()
    val sosContacts by viewModel.sosContacts.collectAsState()
    val isSosDispatching by viewModel.isSosDispatching.collectAsState()
    val sosStatus by viewModel.sosStatus.collectAsState()

    var newContactInput by remember { mutableStateOf("") }
    var sosCustomNote by remember { mutableStateOf("") }
    var testSosVoiceCmd by remember { mutableStateOf("send sos alert") }

    val hasLocationPerm = viewModel.emergencySosManager.hasLocationPermission()

    val locationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.fetchCurrentLocation()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Live Location Tracking Card
        item {
            SiriGlassCard(theme = theme) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (currentLocation != null) theme.primaryAccent else Color(0xFFFFB300)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Live Location & GPS Tracker",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (hasLocationPerm) theme.primaryAccent.copy(alpha = 0.2f) else Color(0xFFFFB300).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, if (hasLocationPerm) theme.primaryAccent else Color(0xFFFFB300))
                        ) {
                            Text(
                                text = if (hasLocationPerm) "GPS ACTIVE" else "PERM REQUIRED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasLocationPerm) theme.primaryAccent else Color(0xFFFFB300),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (!hasLocationPerm) {
                        Text(
                            text = "Grant location access so MAX can track live GPS coordinates and send Google Maps links in emergency alerts.",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                locationPermLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primaryAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("grant_location_perm_btn")
                        ) {
                            Icon(imageVector = Icons.Default.MyLocation, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant GPS Location Permission", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        val loc = currentLocation
                        if (loc != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.05f),
                                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Coordinates: ${String.format(Locale.ROOT, "%.5f", loc.latitude)}, ${String.format(Locale.ROOT, "%.5f", loc.longitude)}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "±${loc.accuracy.toInt()}m",
                                            fontSize = 10.sp,
                                            color = theme.primaryAccent
                                        )
                                    }

                                    loc.addressName?.let { addr ->
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Address: $addr",
                                            fontSize = 11.sp,
                                            color = Color.LightGray,
                                            lineHeight = 15.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(loc.mapsUrl))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Log.e("EmergencySosTab", "Error opening map: ${e.message}")
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, theme.secondaryAccent),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("open_maps_link_btn")
                                        ) {
                                            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, tint = theme.secondaryAccent, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Open Google Maps", fontSize = 10.sp, color = theme.secondaryAccent)
                                        }

                                        Button(
                                            onClick = { viewModel.fetchCurrentLocation() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("refresh_gps_btn")
                                        ) {
                                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Refresh GPS", fontSize = 10.sp, color = Color.White)
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Fetching current GPS coordinates...", fontSize = 12.sp, color = Color.LightGray)
                                Button(
                                    onClick = { viewModel.fetchCurrentLocation() },
                                    colors = ButtonDefaults.buttonColors(containerColor = theme.primaryAccent),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Get Location", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. SOS Emergency Contacts Management Card
        item {
            SiriGlassCard(theme = theme) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Sms, contentDescription = null, tint = theme.secondaryAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Designated SOS Emergency Contacts", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Contacts added here will automatically receive SMS alerts with your live GPS location when SOS is triggered.",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newContactInput,
                            onValueChange = { newContactInput = it },
                            label = { Text("Emergency Phone Number", color = Color.LightGray, fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = theme.primaryAccent,
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("add_sos_contact_input")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (newContactInput.isNotBlank()) {
                                    viewModel.addSosContact(newContactInput)
                                    newContactInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primaryAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("add_sos_contact_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (sosContacts.isEmpty()) {
                        Text(
                            text = "No emergency contacts configured yet. Please add at least one number.",
                            fontSize = 11.sp,
                            color = Color(0xFFFFB300)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            sosContacts.forEach { contactNum ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.08f),
                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(imageVector = Icons.Default.Call, contentDescription = null, tint = theme.primaryAccent, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = contactNum, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }

                                        IconButton(
                                            onClick = { viewModel.removeSosContact(contactNum) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = "Remove Contact", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Emergency SOS Dispatcher Trigger Card
        item {
            SiriGlassCard(theme = theme) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF3D00))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Emergency SOS Dispatcher", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        if (isSosDispatching) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFFFF3D00), strokeWidth = 2.dp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Pressing this button or saying 'Send SOS' will immediately broadcast your live GPS location SMS to all saved emergency contacts.",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = sosCustomNote,
                        onValueChange = { sosCustomNote = it },
                        label = { Text("Optional Emergency Note (e.g. Medical Emergency)", color = Color.LightGray, fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF3D00),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sos_custom_note_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.dispatchSosAlert(sosCustomNote) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D00)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("trigger_emergency_sos_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("TRIGGER EMERGENCY SOS NOW", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    sosStatus?.let { statusMsg ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(0.5.dp, theme.primaryAccent.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = theme.primaryAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = statusMsg, fontSize = 11.sp, color = Color.White, lineHeight = 15.sp)
                            }
                        }
                    }
                }
            }
        }

        // 4. Voice Trigger Commands & Simulator
        item {
            SiriGlassCard(theme = theme) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = theme.secondaryAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Voice Trigger Commands & Simulator", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Supported hands-free spoken trigger phrases in English & Hindi:",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val sosExamples = listOf(
                        "• Emergency SOS: \"Send SOS\", \"Emergency alert\", \"Help me\", \"एसओएस भेजो\", \"इमरजेंसी अलर्ट\"",
                        "• Live Location: \"Send my location\", \"Share location\", \"Where am I\", \"मेरी लोकेशन भेजो\""
                    )

                    sosExamples.forEach { ex ->
                        Text(text = ex, fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f), lineHeight = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = testSosVoiceCmd,
                        onValueChange = { testSosVoiceCmd = it },
                        label = { Text("Simulate Spoken Emergency Command", color = Color.LightGray, fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.secondaryAccent,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.processSttUserQuery(testSosVoiceCmd) },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.secondaryAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_sos_voice_cmd_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Execute Spoken Emergency Command", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

