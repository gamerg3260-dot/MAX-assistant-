package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Visualizer presentation modes for real-time microphone audio visualization.
 */
enum class VisualizerMode(val displayName: String) {
    RIBBON_WAVE("Fluid Wave"),
    SPECTRUM_BARS("Equalizer"),
    RADIAL_AURA("Radial Pulse")
}

/**
 * Real-time audio visualizer component using Canvas in Jetpack Compose.
 * Directly reacts to microphone audio amplitude (RMS dB / level) during voice recognition.
 */
@Composable
fun RealtimeAudioVisualizerCanvas(
    audioAmplitude: Float,
    isListening: Boolean,
    isSpeaking: Boolean = false,
    isProcessing: Boolean = false,
    theme: SiriThemeColors,
    mode: VisualizerMode = VisualizerMode.RIBBON_WAVE,
    sensitivityMultiplier: Float = 1.0f,
    modifier: Modifier = Modifier,
    height: Dp = 100.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_visualizer_infinite")

    // Dynamic wave phase that accelerates when loud voice is detected
    val phaseDuration = remember(isListening, audioAmplitude) {
        if (isListening) {
            val speedFactor = (audioAmplitude * 0.15f).coerceIn(0f, 1.5f)
            ((1200 / (1f + speedFactor)).toInt()).coerceIn(400, 1600)
        } else if (isSpeaking) 1000
        else if (isProcessing) 1400
        else 3200
    }

    val primaryPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = phaseDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "primaryPhase"
    )

    val secondaryPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (-2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (phaseDuration * 1.3f).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "secondaryPhase"
    )

    // Breathing pulse for ambient baseline movement
    val ambientBreath by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientBreath"
    )

    // Smooth reactive spring physics on the incoming microphone amplitude
    val targetAmplitude = remember(audioAmplitude, isListening, isSpeaking, isProcessing, sensitivityMultiplier) {
        when {
            isListening -> {
                // RMS dB typically ranges from 0 to 10+
                val normalizedDb = audioAmplitude.coerceAtLeast(0f) * sensitivityMultiplier
                (normalizedDb + ambientBreath * 0.4f).coerceIn(0.4f, 12.0f)
            }
            isSpeaking -> (2.5f + ambientBreath * 1.5f)
            isProcessing -> (1.2f + ambientBreath * 0.8f)
            else -> 0.2f
        }
    }

    val animatedAmp by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "animatedAmp"
    )

    val isActive = isListening || isSpeaking || isProcessing

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .testTag("realtime_audio_visualizer_canvas")
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            if (canvasWidth <= 0 || canvasHeight <= 0) return@Canvas

            when (mode) {
                VisualizerMode.RIBBON_WAVE -> {
                    drawRibbonWaveMode(
                        canvasWidth = canvasWidth,
                        canvasHeight = canvasHeight,
                        amplitude = animatedAmp,
                        phase1 = primaryPhase,
                        phase2 = secondaryPhase,
                        isActive = isActive,
                        isListening = isListening,
                        theme = theme
                    )
                }
                VisualizerMode.SPECTRUM_BARS -> {
                    drawSpectrumBarsMode(
                        canvasWidth = canvasWidth,
                        canvasHeight = canvasHeight,
                        amplitude = animatedAmp,
                        phase = primaryPhase,
                        isActive = isActive,
                        isListening = isListening,
                        theme = theme
                    )
                }
                VisualizerMode.RADIAL_AURA -> {
                    drawRadialAuraMode(
                        canvasWidth = canvasWidth,
                        canvasHeight = canvasHeight,
                        amplitude = animatedAmp,
                        phase = primaryPhase,
                        isActive = isActive,
                        isListening = isListening,
                        theme = theme
                    )
                }
            }
        }
    }
}

/**
 * Canvas drawing logic for Ribbon Wave mode.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRibbonWaveMode(
    canvasWidth: Float,
    canvasHeight: Float,
    amplitude: Float,
    phase1: Float,
    phase2: Float,
    isActive: Boolean,
    isListening: Boolean,
    theme: SiriThemeColors
) {
    val centerY = canvasHeight * 0.55f

    // 1. Ambient Background Glow
    val glowColor = if (isListening) Color(0xFFEF4444) else theme.primaryAccent
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                glowColor.copy(alpha = if (isActive) (0.08f + amplitude * 0.02f).coerceAtMost(0.35f) else 0.04f),
                Color.Transparent
            ),
            startY = 0f,
            endY = canvasHeight
        ),
        topLeft = Offset(0f, 0f),
        size = size
    )

    // 2. Primary Wave (theme.primaryAccent)
    val path1 = Path()
    val baseAmp1 = (canvasHeight * 0.35f) * (amplitude / 4.5f).coerceIn(0.15f, 1.2f)
    val freq1 = 2.2f
    path1.moveTo(0f, centerY)

    var x = 0f
    val step = 3f
    while (x <= canvasWidth) {
        val normX = (x / canvasWidth) * 2f - 1f // -1 to +1
        val envelope = (1f - normX * normX).coerceAtLeast(0f) // Gaussian-like bell envelope
        val y = centerY + sin((x / canvasWidth) * (freq1 * Math.PI * 2f) + phase1).toFloat() * (baseAmp1 * envelope)
        path1.lineTo(x, y)
        x += step
    }

    drawPath(
        path = path1,
        brush = Brush.horizontalGradient(
            colors = listOf(
                theme.primaryAccent.copy(alpha = 0.2f),
                theme.primaryAccent,
                theme.secondaryAccent,
                theme.primaryAccent.copy(alpha = 0.2f)
            )
        ),
        style = Stroke(
            width = if (isActive) (2.5f + amplitude * 0.4f).coerceIn(2.5f, 6.0f) else 1.5f,
            cap = StrokeCap.Round
        )
    )

    // 3. Secondary Wave (theme.secondaryAccent)
    val path2 = Path()
    val baseAmp2 = (canvasHeight * 0.28f) * (amplitude / 4.8f).coerceIn(0.12f, 1.1f)
    val freq2 = 3.1f
    path2.moveTo(0f, centerY)

    x = 0f
    while (x <= canvasWidth) {
        val normX = (x / canvasWidth) * 2f - 1f
        val envelope = (1f - normX * normX).coerceAtLeast(0f)
        val y = centerY + sin((x / canvasWidth) * (freq2 * Math.PI * 2f) + phase2).toFloat() * (baseAmp2 * envelope)
        path2.lineTo(x, y)
        x += step
    }

    drawPath(
        path = path2,
        brush = Brush.horizontalGradient(
            colors = listOf(
                theme.secondaryAccent.copy(alpha = 0.15f),
                theme.secondaryAccent,
                Color(0xFF38BDF8),
                theme.secondaryAccent.copy(alpha = 0.15f)
            )
        ),
        style = Stroke(
            width = if (isActive) (2.0f + amplitude * 0.3f).coerceIn(2.0f, 4.5f) else 1.2f,
            cap = StrokeCap.Round
        )
    )

    // 4. Luminous Center Core highlight during loud vocal peaks
    if (isActive && amplitude > 1.2f) {
        val pathCore = Path()
        val baseAmpCore = (canvasHeight * 0.18f) * (amplitude / 5.0f).coerceIn(0.1f, 0.9f)
        val freqCore = 4.0f
        pathCore.moveTo(0f, centerY)

        x = 0f
        while (x <= canvasWidth) {
            val normX = (x / canvasWidth) * 2f - 1f
            val envelope = (1f - normX * normX).coerceAtLeast(0f)
            val y = centerY + sin((x / canvasWidth) * (freqCore * Math.PI * 2f) + phase1 * 1.6f).toFloat() * (baseAmpCore * envelope)
            pathCore.lineTo(x, y)
            x += step
        }

        drawPath(
            path = pathCore,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = (0.3f + amplitude * 0.08f).coerceAtMost(0.95f)),
                    Color.Transparent
                )
            ),
            style = Stroke(
                width = 1.8f,
                cap = StrokeCap.Round
            )
        )
    }
}

/**
 * Canvas drawing logic for Equalizer Bars mode.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpectrumBarsMode(
    canvasWidth: Float,
    canvasHeight: Float,
    amplitude: Float,
    phase: Float,
    isActive: Boolean,
    isListening: Boolean,
    theme: SiriThemeColors
) {
    val barCount = 28
    val totalSpacingRatio = 0.35f
    val availableBarSpace = canvasWidth * (1f - totalSpacingRatio)
    val barWidth = (availableBarSpace / barCount).coerceAtLeast(4f)
    val spacing = (canvasWidth * totalSpacingRatio) / (barCount + 1)
    val baseY = canvasHeight * 0.88f
    val maxBarHeight = canvasHeight * 0.76f

    for (i in 0 until barCount) {
        val x = spacing + i * (barWidth + spacing)
        // Frequency profile: human speech frequencies concentrate between bins 4 and 20
        val centerDistance = kotlin.math.abs(i - (barCount / 2f)) / (barCount / 2f) // 0 to 1
        val freqWeight = (1f - centerDistance * 0.65f).coerceIn(0.2f, 1f)

        // Wave variation over bars
        val waveModulation = (sin(phase + i * 0.45f) * 0.25f + 0.75f).toFloat()
        val computedHeight = if (isActive) {
            (maxBarHeight * (amplitude / 6.0f).coerceIn(0.08f, 1.0f) * freqWeight * waveModulation).coerceIn(6f, maxBarHeight)
        } else {
            (6f + sin(phase + i * 0.3f) * 3f).coerceAtLeast(4f)
        }

        val barTop = baseY - computedHeight

        // Draw bar
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    if (isListening) Color(0xFFEF4444) else theme.primaryAccent,
                    theme.secondaryAccent,
                    theme.secondaryAccent.copy(alpha = 0.4f)
                ),
                startY = barTop,
                endY = baseY
            ),
            topLeft = Offset(x, barTop),
            size = Size(barWidth, computedHeight),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        )

        // Peak hold cap dot
        if (isActive && computedHeight > 16f) {
            val peakY = (barTop - 4f).coerceAtLeast(2f)
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = (barWidth / 2.5f).coerceIn(1.5f, 3.5f),
                center = Offset(x + barWidth / 2f, peakY)
            )
        }
    }
}

/**
 * Canvas drawing logic for Radial Aura mode.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRadialAuraMode(
    canvasWidth: Float,
    canvasHeight: Float,
    amplitude: Float,
    phase: Float,
    isActive: Boolean,
    isListening: Boolean,
    theme: SiriThemeColors
) {
    val center = Offset(canvasWidth / 2f, canvasHeight / 2f)
    val maxRadius = kotlin.math.min(canvasWidth, canvasHeight) * 0.42f
    val baseCoreRadius = maxRadius * 0.45f

    // 1. Dynamic Core Glow
    val coreGlowColor = if (isListening) Color(0xFFEF4444) else theme.primaryAccent
    val pulseExpansion = if (isActive) (amplitude * 2.5f).coerceAtMost(maxRadius * 0.4f) else 0f
    val effectiveCoreRadius = baseCoreRadius + pulseExpansion

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                coreGlowColor.copy(alpha = if (isActive) 0.5f else 0.2f),
                theme.secondaryAccent.copy(alpha = if (isActive) 0.25f else 0.08f),
                Color.Transparent
            ),
            center = center,
            radius = effectiveCoreRadius * 1.5f
        ),
        radius = effectiveCoreRadius * 1.5f,
        center = center
    )

    // 2. Radial Voice Ray Spikes
    val rayCount = 36
    val angleStep = (2 * Math.PI) / rayCount

    for (i in 0 until rayCount) {
        val angle = i * angleStep + phase * 0.5f
        val spikeModulation = (sin(angle * 3f + phase * 2f) * 0.3f + 0.7f).toFloat()
        val rayLength = if (isActive) {
            ((maxRadius - effectiveCoreRadius) * (amplitude / 5f).coerceIn(0.1f, 1f) * spikeModulation).coerceAtLeast(4f)
        } else {
            (4f + sin(phase + i) * 2f).coerceAtLeast(2f)
        }

        val startX = center.x + (effectiveCoreRadius * cos(angle)).toFloat()
        val startY = center.y + (effectiveCoreRadius * sin(angle)).toFloat()
        val endX = center.x + ((effectiveCoreRadius + rayLength) * cos(angle)).toFloat()
        val endY = center.y + ((effectiveCoreRadius + rayLength) * sin(angle)).toFloat()

        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    if (isListening) Color(0xFFEF4444) else theme.primaryAccent,
                    theme.secondaryAccent
                ),
                start = Offset(startX, startY),
                end = Offset(endX, endY)
            ),
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = if (isActive) 2.5f else 1.2f,
            cap = StrokeCap.Round
        )
    }

    // 3. Central Luminous Core
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.9f),
                theme.primaryAccent,
                theme.secondaryAccent
            ),
            center = center,
            radius = baseCoreRadius * 0.7f
        ),
        radius = baseCoreRadius * 0.7f,
        center = center
    )
}

/**
 * A self-contained, interactive Real-time Audio Visualizer Card.
 * Displays mode toggle pills, live dB amplitude stats, sensitivity controls,
 * and the Canvas-based real-time visualizer.
 */
@Composable
fun RealtimeAudioVisualizerCard(
    audioAmplitude: Float,
    isListening: Boolean,
    isSpeaking: Boolean = false,
    isProcessing: Boolean = false,
    theme: SiriThemeColors,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMode by remember { mutableStateOf(VisualizerMode.RIBBON_WAVE) }
    var sensitivity by remember { mutableFloatStateOf(1.0f) }

    val activeState = isListening || isSpeaking || isProcessing
    val currentDb = remember(audioAmplitude) {
        if (audioAmplitude <= 0.05f) "-60 dB"
        else String.format("+%.1f dB", audioAmplitude)
    }
    val percentage = remember(audioAmplitude) {
        ((audioAmplitude / 8f) * 100f).coerceIn(0f, 100f).toInt()
    }

    SiriGlassCard(
        theme = theme,
        modifier = modifier
            .fillMaxWidth()
            .testTag("realtime_audio_visualizer_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Status Badge & Live Amplitude Level
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isListening) Color(0xFFEF4444).copy(alpha = 0.2f)
                                else theme.primaryAccent.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.RadioButtonChecked else Icons.Default.GraphicEq,
                            contentDescription = "Microphone Visualizer",
                            tint = if (isListening) Color(0xFFEF4444) else theme.primaryAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Microphone Audio Visualizer",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isListening) Color(0xFFEF4444)
                                        else if (isSpeaking) Color(0xFF10B981)
                                        else theme.primaryAccent
                                    )
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isListening) "STREAMING MIC INPUT • LIVE"
                                else if (isSpeaking) "TTS AUDIO SYNTHESIS • ACTIVE"
                                else if (isProcessing) "GEMINI AI THINKING"
                                else "STANDBY • READY TO LISTEN",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isListening) Color(0xFFEF4444) else theme.primaryAccent
                            )
                        }
                    }
                }

                // Amplitude Meter Badge
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = currentDb,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isListening && audioAmplitude > 1.5f) theme.primaryAccent else Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "$percentage% Level",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            // Mode Selector Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VisualizerMode.values().forEach { mode ->
                    val isSelected = selectedMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) theme.primaryAccent.copy(alpha = 0.22f)
                                else Color.White.copy(alpha = 0.06f)
                            )
                            .border(
                                width = if (isSelected) 1.2.dp else 0.5.dp,
                                color = if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedMode = mode }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.displayName,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                }
            }

            // The Canvas Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xD9090E17))
                    .border(1.dp, theme.primaryAccent.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            ) {
                RealtimeAudioVisualizerCanvas(
                    audioAmplitude = audioAmplitude,
                    isListening = isListening,
                    isSpeaking = isSpeaking,
                    isProcessing = isProcessing,
                    theme = theme,
                    mode = selectedMode,
                    sensitivityMultiplier = sensitivity,
                    modifier = Modifier.fillMaxWidth(),
                    height = 96.dp
                )
            }

            // Controls Footer: Sensitivity + Mic Test Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Sensitivity Boost Pills
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Gain:",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    listOf(1.0f to "1x", 1.5f to "1.5x", 2.0f to "2x").forEach { (boost, label) ->
                        val isSelected = sensitivity == boost
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) theme.primaryAccent.copy(alpha = 0.25f)
                                    else Color.White.copy(alpha = 0.05f)
                                )
                                .border(
                                    width = if (isSelected) 1.dp else 0.5.dp,
                                    color = if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { sensitivity = boost }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) theme.primaryAccent else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Interactive Toggle Button
                Button(
                    onClick = {
                        if (isListening) onStopListening() else onStartListening()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isListening) Color(0xFFEF4444) else theme.primaryAccent
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("visualizer_mic_toggle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = if (isListening) "Stop Mic" else "Listen Now",
                        tint = if (isListening) Color.White else Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isListening) "Stop Mic" else "Listen Now",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isListening) Color.White else Color.Black
                    )
                }
            }
        }
    }
}
