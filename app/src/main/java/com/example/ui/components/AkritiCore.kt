package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.data.model.AssistantState
import com.example.ui.theme.AkritiCyanGlow
import com.example.ui.theme.AkritiCyanListening
import com.example.ui.theme.AkritiEmeraldGlow
import com.example.ui.theme.AkritiEmeraldSpeaking
import com.example.ui.theme.AkritiIndigoPrimary
import com.example.ui.theme.AkritiIndigoVariant
import com.example.ui.theme.AkritiRoseError
import com.example.ui.theme.AkritiRoseGlow

@Composable
fun AkritiCore(
    state: AssistantState,
    rmsDb: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "akritiCoreTransition")

    // Slow ambient breathing
    val ambientScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientPulse"
    )

    // Orbital ring 1 rotation (clockwise)
    val ringRotation1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1Rotate"
    )

    // Orbital ring 2 rotation (counter-clockwise)
    val ringRotation2 by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2Rotate"
    )

    // Expanding outer energy pulse (for listening and speaking)
    val energyPulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "energyPulse"
    )

    // Dynamic scale driven by mic RMS
    val micScale = if (state.isListening) {
        1.0f + (rmsDb.coerceIn(0f, 1f) * 0.4f)
    } else {
        1.0f
    }

    val coreColor by animateColorAsState(
        targetValue = when (state) {
            AssistantState.IDLE -> AkritiIndigoPrimary
            AssistantState.LISTENING_FOR_WAKE_WORD -> Color(0xFF0284C7)
            AssistantState.WAKE_DETECTED, AssistantState.LISTENING, AssistantState.LISTENING_FOR_COMMAND -> AkritiCyanListening
            AssistantState.PROCESSING -> AkritiIndigoVariant
            AssistantState.EXECUTING -> Color(0xFF00E5FF)
            AssistantState.SPEAKING -> AkritiEmeraldSpeaking
            AssistantState.ERROR -> AkritiRoseError
        },
        animationSpec = tween(350),
        label = "coreColor"
    )

    val glowColor by animateColorAsState(
        targetValue = when (state) {
            AssistantState.IDLE -> AkritiIndigoPrimary.copy(alpha = 0.25f)
            AssistantState.LISTENING_FOR_WAKE_WORD -> Color(0x330284C7)
            AssistantState.WAKE_DETECTED, AssistantState.LISTENING, AssistantState.LISTENING_FOR_COMMAND -> AkritiCyanGlow
            AssistantState.PROCESSING -> AkritiIndigoPrimary.copy(alpha = 0.35f)
            AssistantState.EXECUTING -> Color(0x4000E5FF)
            AssistantState.SPEAKING -> AkritiEmeraldGlow
            AssistantState.ERROR -> AkritiRoseGlow
        },
        animationSpec = tween(350),
        label = "glowColor"
    )

    Box(
        modifier = modifier.size(170.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing energy aura
        if (state != AssistantState.IDLE) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(energyPulse * micScale)
                    .clip(CircleShape)
                    .background(glowColor)
            )
        }

        // HUD Orbital Arcs Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            val arcPadding = 12.dp.toPx()
            val radius = (size.minDimension / 2f) - arcPadding

            // Ring 1 (Outer segmented orbit)
            drawArc(
                color = coreColor.copy(alpha = 0.35f),
                startAngle = ringRotation1,
                sweepAngle = 70f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = coreColor.copy(alpha = 0.25f),
                startAngle = ringRotation1 + 120f,
                sweepAngle = 50f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = coreColor.copy(alpha = 0.35f),
                startAngle = ringRotation1 + 240f,
                sweepAngle = 60f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Ring 2 (Inner counter-rotating orbit)
            drawArc(
                color = coreColor.copy(alpha = 0.45f),
                startAngle = ringRotation2,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = strokeWidth * 0.8f, cap = StrokeCap.Round)
            )
            drawArc(
                color = coreColor.copy(alpha = 0.3f),
                startAngle = ringRotation2 + 180f,
                sweepAngle = 60f,
                useCenter = false,
                style = Stroke(width = strokeWidth * 0.8f, cap = StrokeCap.Round)
            )
        }

        // Core Interactive Spherical Orb
        val coreScale = when (state) {
            AssistantState.IDLE -> ambientScale
            AssistantState.LISTENING_FOR_WAKE_WORD -> ambientScale * 1.02f
            AssistantState.WAKE_DETECTED -> 1.15f
            AssistantState.LISTENING, AssistantState.LISTENING_FOR_COMMAND -> micScale
            AssistantState.PROCESSING, AssistantState.EXECUTING -> ambientScale * 1.08f
            AssistantState.SPEAKING -> ambientScale * 1.1f
            AssistantState.ERROR -> 1.0f
        }

        Box(
            modifier = Modifier
                .size(92.dp)
                .scale(coreScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            coreColor,
                            coreColor.copy(alpha = 0.82f),
                            coreColor.copy(alpha = 0.45f)
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = Color.White),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                AssistantState.IDLE -> {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Tap to speak",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                AssistantState.LISTENING_FOR_WAKE_WORD -> {
                    Icon(
                        imageVector = Icons.Default.Hearing,
                        contentDescription = "Listening for Akriti wake word",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
                AssistantState.WAKE_DETECTED, AssistantState.LISTENING, AssistantState.LISTENING_FOR_COMMAND -> {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Listening for command",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }
                AssistantState.PROCESSING -> {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Processing command",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
                AssistantState.EXECUTING -> {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Executing action",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                AssistantState.SPEAKING -> {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Speaking, tap to stop",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                AssistantState.ERROR -> {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = "Error state, tap to reset",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }
    }
}
