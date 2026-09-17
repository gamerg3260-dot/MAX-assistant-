package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * High-fidelity Siri-style visualizer and glowing wave animation at the bottom of the screen.
 * Responds dynamically to speech RMS level, thinking, and TTS audio playback states.
 */
@Composable
fun SiriGlowWaveVisualizer(
    isListening: Boolean,
    isProcessing: Boolean,
    isSpeaking: Boolean,
    rmsDbLevel: Float = 0f,
    theme: SiriThemeColors,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "siri_wave_motion")

    // Phase animation for continuous fluid motion
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isListening) 900 else if (isProcessing) 1400 else if (isSpeaking) 1100 else 3000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (-2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isListening) 1200 else if (isProcessing) 1800 else if (isSpeaking) 1300 else 4000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )

    // Breathing pulse for idle & thinking states
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    val activeState = isListening || isProcessing || isSpeaking
    val amplitudeMultiplier = when {
        isListening -> ((rmsDbLevel.coerceAtLeast(0f) / 8f) + 0.8f).coerceIn(0.8f, 2.8f)
        isSpeaking -> (1.3f + breathScale * 0.7f)
        isProcessing -> (0.8f + breathScale * 0.5f)
        else -> 0.25f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerY = canvasHeight * 0.65f

            if (canvasWidth <= 0 || canvasHeight <= 0) return@Canvas

            // 1. Ambient bottom edge glow
            val glowColor = when {
                isListening -> Color(0xFFEF4444)
                isProcessing -> theme.secondaryAccent
                isSpeaking -> theme.primaryAccent
                else -> theme.glowColor.copy(alpha = 0.3f)
            }

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        glowColor.copy(alpha = if (activeState) 0.35f else 0.12f),
                        glowColor.copy(alpha = if (activeState) 0.65f else 0.2f)
                    ),
                    startY = 0f,
                    endY = canvasHeight
                ),
                topLeft = Offset(0f, 0f),
                size = size
            )

            // 2. Harmonic Siri waves
            // Wave 1: Primary vibrant accent (Cyan / Neon Blue / Electric Violet)
            val path1 = Path()
            val baseAmp1 = (canvasHeight * 0.38f) * amplitudeMultiplier
            val freq1 = 2.4f

            path1.moveTo(0f, centerY)
            var x = 0f
            val step = 4f
            while (x <= canvasWidth) {
                // Windowing envelope function: zero at ends, max in center
                val normalizedX = (x / canvasWidth) * 2f - 1f // -1 to +1
                val envelope = (1f - normalizedX * normalizedX).coerceAtLeast(0f)
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
                        theme.glowColor,
                        theme.primaryAccent.copy(alpha = 0.2f)
                    )
                ),
                style = Stroke(
                    width = if (activeState) 3.5f else 1.5f,
                    cap = StrokeCap.Round
                )
            )

            // Wave 2: Secondary accent (Magenta / Orchid Pink / Rose)
            val path2 = Path()
            val baseAmp2 = (canvasHeight * 0.32f) * amplitudeMultiplier
            val freq2 = 3.2f

            path2.moveTo(0f, centerY)
            x = 0f
            while (x <= canvasWidth) {
                val normalizedX = (x / canvasWidth) * 2f - 1f
                val envelope = (1f - normalizedX * normalizedX).coerceAtLeast(0f)
                val y = centerY + sin((x / canvasWidth) * (freq2 * Math.PI * 2f) + phase2).toFloat() * (baseAmp2 * envelope)
                path2.lineTo(x, y)
                x += step
            }

            drawPath(
                path = path2,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        theme.secondaryAccent.copy(alpha = 0.2f),
                        theme.secondaryAccent,
                        Color(0xFFF43F5E),
                        theme.secondaryAccent.copy(alpha = 0.2f)
                    )
                ),
                style = Stroke(
                    width = if (activeState) 3.0f else 1.2f,
                    cap = StrokeCap.Round
                )
            )

            // Wave 3: Tertiary subtle bright highlight wave (Pure luminous core)
            if (activeState) {
                val path3 = Path()
                val baseAmp3 = (canvasHeight * 0.22f) * amplitudeMultiplier
                val freq3 = 4.0f
                val phase3 = phase1 * 1.5f

                path3.moveTo(0f, centerY)
                x = 0f
                while (x <= canvasWidth) {
                    val normalizedX = (x / canvasWidth) * 2f - 1f
                    val envelope = (1f - normalizedX * normalizedX).coerceAtLeast(0f)
                    val y = centerY + sin((x / canvasWidth) * (freq3 * Math.PI * 2f) + phase3).toFloat() * (baseAmp3 * envelope)
                    path3.lineTo(x, y)
                    x += step
                }

                drawPath(
                    path = path3,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.85f),
                            Color.Transparent
                        )
                    ),
                    style = Stroke(
                        width = 2.0f,
                        cap = StrokeCap.Round
                    )
                )
            }
        }
    }
}
