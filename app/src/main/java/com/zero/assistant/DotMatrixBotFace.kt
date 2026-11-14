package com.zero.assistant

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal enum class BotFaceMode {
    Idle,
    Listening,
    Thinking,
    Speaking,
    ActingOnScreen,
    Success,
}

@Composable
internal fun DotMatrixBotFace(
    mode: BotFaceMode,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "dot-matrix-face")
    val phase by transition.animateFloat(
        initialValue = if (reduceMotion) 0.5f else 0f,
        targetValue = if (reduceMotion) 0.5f else 1f,
        animationSpec = infiniteRepeatable(tween(1_200), RepeatMode.Reverse),
        label = "face-phase",
    )
    Box(
        modifier
            .size(54.dp)
            .semantics { contentDescription = "ZERO assistant bot, ${mode.name}" }
            .shadow(12.dp, CircleShape)
            .background(Color(0xFF090A0F), CircleShape)
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color(0xFF22D3EE), Color(0xFF7C3AED), Color(0xFFF43F9E))),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(44.dp)) {
            val spacing = size.minDimension / 10f
            val dotRadius = spacing * 0.16f
            val motion = if (reduceMotion) 0.5f else phase
            for (row in 1..9) {
                for (column in 1..9) {
                    val x = column * spacing
                    val y = row * spacing
                    val nx = (column - 5) / 4f
                    val ny = (row - 5) / 4f
                    if (nx * nx + ny * ny > 1.12f) continue
                    val speakingMode = mode == BotFaceMode.Speaking || mode == BotFaceMode.Success
                    val eyeCenter = if (column < 5) 3 else 7
                    val eyeRegion = row in 3..4 && (column in 2..4 || column in 6..8)
                    val happyEyeDot = eyeRegion &&
                        row == if (column == eyeCenter) 4 else 3
                    val mouthRegion = row in 6..8 && column in 3..7
                    val happyMouthDot = mouthRegion && row == 8 - kotlin.math.abs(column - 5)
                    if (speakingMode && ((eyeRegion && !happyEyeDot) || (mouthRegion && !happyMouthDot))) continue
                    val isEyeArea = if (speakingMode) happyEyeDot else eyeRegion
                    val isMouthArea = if (speakingMode) happyMouthDot else mouthRegion
                    val color = if ((column + row) % 4 == 0) Color(0xFFF43F9E) else Color(0xFF22D3EE)
                    val alpha = when (mode) {
                        BotFaceMode.Listening -> if (isEyeArea && motion > 0.91f) 0.12f else 0.55f + motion * 0.35f
                        BotFaceMode.Thinking -> 0.3f + motion * 0.65f
                        BotFaceMode.Speaking, BotFaceMode.Success -> if (isMouthArea || isEyeArea) 0.92f else 0.42f
                        BotFaceMode.ActingOnScreen -> if ((column + (motion * 8).toInt()) % 4 == 0) 1f else 0.28f
                        BotFaceMode.Idle -> 0.58f
                    }
                    val drawY = when {
                        mode == BotFaceMode.Thinking && row in 3..7 -> y + kotlin.math.sin((column + motion * 6f) * 0.7f) * spacing * 0.7f
                        else -> y
                    }
                    val pulse = if (!reduceMotion && mode == BotFaceMode.Thinking) 0.75f + motion * 0.6f else 1f
                    drawCircle(color.copy(alpha = alpha), dotRadius * pulse, Offset(x, drawY))
                }
            }
        }
    }
}
