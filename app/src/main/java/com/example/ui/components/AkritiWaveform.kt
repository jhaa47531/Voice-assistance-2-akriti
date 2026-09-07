package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.AssistantState
import com.example.ui.theme.AkritiCyanListening
import com.example.ui.theme.AkritiEmeraldSpeaking
import com.example.ui.theme.AkritiIndigoPrimary
import com.example.ui.theme.AkritiRoseError
import kotlin.math.sin

@Composable
fun AkritiWaveform(
    state: AssistantState,
    rmsDb: Float,
    modifier: Modifier = Modifier,
    waveformHeight: Dp = 44.dp,
    barCount: Int = 28
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveformTransition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveformPhase"
    )

    val primaryColor = when (state) {
        AssistantState.IDLE -> AkritiIndigoPrimary.copy(alpha = 0.45f)
        AssistantState.LISTENING, AssistantState.LISTENING_FOR_COMMAND, AssistantState.WAKE_DETECTED -> AkritiCyanListening
        AssistantState.LISTENING_FOR_WAKE_WORD -> AkritiCyanListening.copy(alpha = 0.6f)
        AssistantState.PROCESSING, AssistantState.EXECUTING -> AkritiIndigoPrimary
        AssistantState.SPEAKING -> AkritiEmeraldSpeaking
        AssistantState.ERROR -> AkritiRoseError
    }

    val secondaryColor = when (state) {
        AssistantState.IDLE -> AkritiCyanListening.copy(alpha = 0.25f)
        AssistantState.LISTENING, AssistantState.LISTENING_FOR_COMMAND, AssistantState.WAKE_DETECTED -> Color(0xFF38BDF8)
        AssistantState.LISTENING_FOR_WAKE_WORD -> Color(0xFF0284C7).copy(alpha = 0.5f)
        AssistantState.PROCESSING, AssistantState.EXECUTING -> Color(0xFF818CF8)
        AssistantState.SPEAKING -> Color(0xFF34D399)
        AssistantState.ERROR -> Color(0xFFFDA4AF)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(waveformHeight)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val totalSpacing = width / (barCount + 1)
        val barWidth = (totalSpacing * 0.55f).coerceIn(2.5f, 6.5f)

        val brush = Brush.verticalGradient(
            colors = listOf(primaryColor, secondaryColor, primaryColor),
            startY = 0f,
            endY = height
        )

        val baseActivity = when (state) {
            AssistantState.IDLE -> 0.12f
            AssistantState.LISTENING_FOR_WAKE_WORD -> 0.25f
            AssistantState.WAKE_DETECTED -> 0.65f
            AssistantState.LISTENING, AssistantState.LISTENING_FOR_COMMAND -> (0.4f + (rmsDb.coerceIn(0f, 1f) * 0.9f)).coerceIn(0.2f, 1.0f)
            AssistantState.PROCESSING -> 0.55f
            AssistantState.EXECUTING -> 0.7f
            AssistantState.SPEAKING -> 0.85f
            AssistantState.ERROR -> 0.2f
        }

        for (i in 0 until barCount) {
            val normalizedX = i.toFloat() / barCount.toFloat()
            // Center envelope weighting (bell curve effect so ends taper nicely)
            val bellWeight = sin(normalizedX * Math.PI).toFloat().coerceAtLeast(0.15f)

            // Dynamic oscillating wave height
            val waveOscillation = sin(phase + (normalizedX * 4f * Math.PI)).toFloat()
            val variableHeight = (height * 0.85f) * baseActivity * bellWeight * (0.6f + 0.4f * waveOscillation)
            val finalHeight = variableHeight.coerceIn(4f, height)

            val x = (i + 1) * totalSpacing - (barWidth / 2f)
            val top = centerY - (finalHeight / 2f)

            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, top),
                size = Size(barWidth, finalHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
