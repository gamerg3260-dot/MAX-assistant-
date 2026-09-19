package com.example.ui

import android.content.Context
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.AppSettings
import com.example.permissions.PermissionHelper
import com.example.toggle.SoundMode

@Composable
fun IntegratedSettingsTab(
    viewModel: AutoResponderViewModel,
    settings: AppSettings,
    missingPermissions: List<String>,
    theme: SiriThemeColors,
    onRequestPermissions: () -> Unit,
    onOpenApiKeyDialog: () -> Unit
) {
    val context = LocalContext.current
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val isOverlayActive by viewModel.isOverlayActive.collectAsState()
    val soundMode by viewModel.deviceToggleManager.soundMode.collectAsState()
    val isWifiEnabled by viewModel.deviceToggleManager.isWifiEnabled.collectAsState()
    val isTorchOn by viewModel.deviceToggleManager.isFlashlightOn.collectAsState()
    val apiKey by viewModel.apiKeyText.collectAsState()

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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. QUICK CONTROLS DASHBOARD
        SiriSectionHeader(
            title = "Quick Controls & Master State",
            icon = Icons.Default.PowerSettingsNew,
            theme = theme
        )

        SiriGlassCard(theme = theme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Master Assistant Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isServiceRunning) theme.primaryAccent.copy(alpha = 0.2f)
                                    else Color.White.copy(alpha = 0.08f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Master Switch",
                                tint = if (isServiceRunning) theme.primaryAccent else Color.Gray,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "MAX Assistant Service",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (isServiceRunning) "Active foreground background engine" else "Standby - Tap to activate",
                                fontSize = 12.sp,
                                color = if (isServiceRunning) theme.primaryAccent else Color.White.copy(alpha = 0.6f)
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
                        modifier = Modifier.testTag("settings_master_switch")
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                // System Overlay Floating Bubble Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
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
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
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
                            checkedTrackColor = theme.secondaryAccent,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("settings_overlay_switch")
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                // Sound Mode & DND Quick Chips
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Sound & DND Mode",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f)
                    )

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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
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
                }

                // Quick Hardware Toggles: Wi-Fi & Torch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Wi-Fi Tile
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isWifiEnabled) theme.primaryAccent.copy(alpha = 0.18f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .border(
                                width = if (isWifiEnabled) 1.dp else 0.5.dp,
                                color = if (isWifiEnabled) theme.primaryAccent else Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.toggleWifi(!isWifiEnabled) }
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = "Wi-Fi",
                                tint = if (isWifiEnabled) theme.primaryAccent else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Wi-Fi",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isWifiEnabled) "Connected" else "OFF",
                                    fontSize = 10.sp,
                                    color = if (isWifiEnabled) theme.primaryAccent else Color.Gray
                                )
                            }
                        }
                    }

                    // Torch Tile
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isTorchOn) Color(0xFFFFB300).copy(alpha = 0.18f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .border(
                                width = if (isTorchOn) 1.dp else 0.5.dp,
                                color = if (isTorchOn) Color(0xFFFFB300) else Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.toggleFlashlight(!isTorchOn) }
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "Flashlight",
                                tint = if (isTorchOn) Color(0xFFFFB300) else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Torch",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isTorchOn) "Active" else "OFF",
                                    fontSize = 10.sp,
                                    color = if (isTorchOn) Color(0xFFFFB300) else Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. GEMINI AI INTELLIGENCE & API CONFIGURATION
        SiriSectionHeader(
            title = "Gemini AI & Intelligence Configuration",
            icon = Icons.Default.AutoAwesome,
            theme = theme
        )

        SiriGlassCard(theme = theme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val hasApiKey = apiKey.isNotBlank()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "API Key",
                            tint = if (hasApiKey) Color(0xFF00E676) else Color(0xFFFFB300),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Gemini Flash AI Model",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (hasApiKey) "Active & Ready for Voice AI" else "Using Fallback/Server Mode",
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
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("settings_config_api_key_btn")
                    ) {
                        Text(
                            text = if (hasApiKey) "Edit Key" else "Set Key",
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

                // AI Persona Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "AI Response Persona",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f)
                    )

                    val personas = listOf("In a Meeting", "Driving", "At Work", "Sleeping", "Personal")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        personas.forEach { persona ->
                            val isSelected = settings.selectedPersona == persona
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) theme.primaryAccent.copy(alpha = 0.25f)
                                        else Color.White.copy(alpha = 0.06f)
                                    )
                                    .border(
                                        width = if (isSelected) 1.2.dp else 0.5.dp,
                                        color = if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.setPersona(persona) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = persona,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // AI Custom Instructions
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Custom AI Assistant Instructions",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f)
                    )

                    OutlinedTextField(
                        value = customInstructionsInput,
                        onValueChange = { newText -> customInstructionsInput = newText },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_custom_instructions_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.primaryAccent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White.copy(alpha = 0.9f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 3,
                        placeholder = { Text("Enter custom response behavior...", color = Color.Gray) }
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

        // 3. VOICE ENGINE & CALL ANNOUNCER PREFERENCES
        SiriSectionHeader(
            title = "Voice Engine & Call Announcer",
            icon = Icons.Default.RecordVoiceOver,
            theme = theme
        )

        SiriGlassCard(theme = theme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Call Announcer Master Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Automated Call Announcer",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Reads caller contact name or spells unknown digits aloud",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    Switch(
                        checked = settings.isCallAnnouncerEnabled,
                        onCheckedChange = { isChecked -> viewModel.toggleCallAnnouncer(isChecked) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = theme.primaryAccent
                        ),
                        modifier = Modifier.testTag("settings_call_announcer_switch")
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                // TTS Speech Rate Slider
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

                // TTS Speech Pitch Slider
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

                // Announcement Repeat Count
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Announcement Repeat Count",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 2, 3).forEach { count ->
                            val isSelected = settings.announcementRepeatCount == count
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) theme.primaryAccent.copy(alpha = 0.25f)
                                        else Color.White.copy(alpha = 0.08f)
                                    )
                                    .border(
                                        width = if (isSelected) 1.2.dp else 0.5.dp,
                                        color = if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.setAnnouncementRepeatCount(count) }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${count}x Repeat",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // Custom Announcement Template
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Spoken Announcement Template",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customTemplateInput,
                            onValueChange = { newTemplate -> customTemplateInput = newTemplate },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_template_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = theme.primaryAccent,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                viewModel.setAnnouncementTemplate(customTemplateInput)
                                viewModel.testTtsVoice("Sarah Connor")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primaryAccent),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("Test", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 4. TELEPHONY & DIRECT CALLING SETTINGS
        SiriSectionHeader(
            title = "Telephony & Direct Calling",
            icon = Icons.Default.Call,
            theme = theme
        )

        SiriGlassCard(theme = theme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Direct Calling Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Direct Calling",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Direct Voice Calling Active",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Commands like 'Call Mom' dial immediately via Intent.ACTION_CALL without dialer UI",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                // Auto Speakerphone on Accept
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Speakerphone on Accept",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Enables loudspeaker automatically when call is answered via voice",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    Switch(
                        checked = settings.autoSpeakerphoneOnAccept,
                        onCheckedChange = { isChecked -> viewModel.setAutoSpeakerphoneOnAccept(isChecked) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = theme.primaryAccent
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                // Proximity Sensor Silence on Flip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Flip / Proximity Silence",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Silences incoming call ringer when phone is placed face down",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    Switch(
                        checked = settings.isProximitySensorSilence,
                        onCheckedChange = { isChecked -> viewModel.toggleProximitySensorSilence(isChecked) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = theme.primaryAccent
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )

                // Bluetooth Headset Voice Control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bluetooth Headset Voice Control",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Route voice recognition and TTS through connected Bluetooth audio",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    Switch(
                        checked = settings.isBluetoothVoiceControl,
                        onCheckedChange = { isChecked -> viewModel.toggleBluetoothVoiceControl(isChecked) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = theme.primaryAccent
                        )
                    )
                }
            }
        }

        // 5. PERMISSIONS AUDIT & MANAGER
        SiriSectionHeader(
            title = "Permissions & System Access",
            icon = Icons.Default.Security,
            theme = theme
        )

        SiriGlassCard(theme = theme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "System Permissions Audit",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Ensure all required permissions are granted for seamless hands-free operation.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )

                PermissionHelper.REQUIRED_PERMISSIONS.forEach { perm ->
                    val isGranted = !missingPermissions.contains(perm)
                    val label = PermissionHelper.getPermissionLabel(perm)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isGranted) Color(0xFF00E676).copy(alpha = 0.08f)
                                else Color(0xFFFFB300).copy(alpha = 0.12f)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = if (isGranted) "Granted" else "Missing",
                                tint = if (isGranted) Color(0xFF00E676) else Color(0xFFFFB300),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }

                        Text(
                            text = if (isGranted) "GRANTED" else "REQUIRED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isGranted) Color(0xFF00E676) else Color(0xFFFFB300)
                        )
                    }
                }

                if (missingPermissions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onRequestPermissions,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.primaryAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_grant_all_permissions_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Grant",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Grant ${missingPermissions.size} Missing Permissions",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
