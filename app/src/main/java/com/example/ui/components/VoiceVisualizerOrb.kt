package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
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
fun VoiceVisualizerOrb(
    state: AssistantState,
    rmsDb: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbTransition")

    // Ambient breathing animation
    val ambientScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientPulse"
    )

    // Wave ring 1 expansion
    val waveRing1 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveRing1"
    )

    // Wave ring 2 expansion
    val waveRing2 by infiniteTransition.animateFloat(
        initialValue = 1.15f,
        targetValue = 1.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveRing2"
    )

    // Rotation for processing
    val rotateProcessing by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotateProcessing"
    )

    // Target colors based on state
    val coreColor by animateColorAsState(
        targetValue = when (state) {
            AssistantState.IDLE -> AkritiIndigoPrimary
            AssistantState.LISTENING, AssistantState.LISTENING_FOR_COMMAND, AssistantState.LISTENING_FOR_WAKE_WORD, AssistantState.WAKE_DETECTED -> AkritiCyanListening
            AssistantState.PROCESSING, AssistantState.EXECUTING -> AkritiIndigoVariant
            AssistantState.SPEAKING -> AkritiEmeraldSpeaking
            AssistantState.ERROR -> AkritiRoseError
        },
        animationSpec = tween(400),
        label = "coreColor"
    )

    val glowColor by animateColorAsState(
        targetValue = when (state) {
            AssistantState.IDLE -> AkritiIndigoPrimary.copy(alpha = 0.25f)
            AssistantState.LISTENING, AssistantState.LISTENING_FOR_COMMAND, AssistantState.LISTENING_FOR_WAKE_WORD, AssistantState.WAKE_DETECTED -> AkritiCyanGlow
            AssistantState.PROCESSING, AssistantState.EXECUTING -> AkritiIndigoPrimary.copy(alpha = 0.35f)
            AssistantState.SPEAKING -> AkritiEmeraldGlow
            AssistantState.ERROR -> AkritiRoseGlow
        },
        animationSpec = tween(400),
        label = "glowColor"
    )

    // Dynamic scale driven by microphone RMS dB when listening
    val micDynamicScale = if (state.isListening) {
        1.0f + (rmsDb * 0.45f)
    } else {
        1.0f
    }

    Box(
        modifier = modifier.size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing ring 2 (Active when listening or speaking)
        if (state.isListening || state == AssistantState.SPEAKING) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .scale(waveRing2 * micDynamicScale)
                    .clip(CircleShape)
                    .background(glowColor.copy(alpha = 0.35f))
            )
        }

        // Outer pulsing ring 1
        if (state != AssistantState.IDLE) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .scale(
                        when {
                            state.isListening -> waveRing1 * micDynamicScale
                            state == AssistantState.SPEAKING -> waveRing1
                            state == AssistantState.PROCESSING || state == AssistantState.EXECUTING -> rotateProcessing * 1.2f
                            else -> ambientScale * 1.15f
                        }
                    )
                    .clip(CircleShape)
                    .background(glowColor)
            )
        }

        // Core interactive Orb Button
        val coreScale = when {
            state == AssistantState.IDLE -> ambientScale
            state.isListening -> micDynamicScale
            state == AssistantState.PROCESSING || state == AssistantState.EXECUTING -> rotateProcessing
            state == AssistantState.SPEAKING -> ambientScale * 1.05f
            else -> 1.0f
        }

        Box(
            modifier = Modifier
                .size(76.dp)
                .scale(coreScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            coreColor,
                            coreColor.copy(alpha = 0.85f)
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
            when {
                state == AssistantState.IDLE -> {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Tap to speak",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                state.isListening || state == AssistantState.WAKE_DETECTED -> {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Listening",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
                state == AssistantState.PROCESSING || state == AssistantState.EXECUTING -> {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Thinking",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                state == AssistantState.SPEAKING -> {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop speaking",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = "Error, tap to retry",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }
    }
}
