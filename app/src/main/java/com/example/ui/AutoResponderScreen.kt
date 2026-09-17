package com.example.ui

import android.Manifest
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.Warning
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
                        1 -> BlocklistSpamTab(viewModel, settings, currentTheme)
                        2 -> HardwareSystemControlTab(viewModel, settings, currentTheme)
                        3 -> AppMediaControlTab(viewModel, settings, currentTheme)
                        4 -> WorkbenchAndEventsTab(viewModel, settings, events, callHistoryLogs, acceptedCallsCount, rejectedCallsCount, simCallState, currentTheme)
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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SiriSectionHeader(title = "Hardware & System Control", icon = Icons.Default.Tune, theme = theme)
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
    val apiKey by viewModel.apiKeyText.collectAsState()
    var tempKey by remember { mutableStateOf(apiKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        title = {
            Text("Gemini API Key Configuration", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "Enter your Gemini API key for AI-generated response features:",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = tempKey,
                    onValueChange = { tempKey = it },
                    placeholder = { Text("AIzaSy...", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = theme.primaryAccent,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.saveApiKey(tempKey)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.primaryAccent)
            ) {
                Text("Save Key", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}
