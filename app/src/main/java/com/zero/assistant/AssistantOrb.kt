package com.zero.assistant

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal enum class OrbActivity(val description: String) {
    Idle("idle"),
    Listening("listening"),
    Processing("processing"),
    Speaking("speaking"),
}

@Composable
internal fun AssistantOrb(
    activity: OrbActivity,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val animatedValues = if (reducedMotion) {
        Triple(0f, 0f, 1f)
    } else {
        val transition = rememberInfiniteTransition(label = "assistant-orb")
        val rotation by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
            label = "orb-rotation",
        )
        val flow by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(8_000, easing = LinearEasing), RepeatMode.Restart),
            label = "orb-flow",
        )
        val pulse by transition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(2_800), RepeatMode.Reverse),
            label = "orb-pulse",
        )
        Triple(rotation, flow, pulse)
    }
    val phase = animatedValues.first
    val wave = animatedValues.second
    val glowScale = animatedValues.third
    val blue = Color(0xFF22BFFF)
    val violet = Color(0xFF8A58FF)
    val pink = Color(0xFFFF3FAD)
    val targetGlow = when (activity) {
        OrbActivity.Idle -> violet
        OrbActivity.Listening -> blue
        OrbActivity.Processing -> pink
        OrbActivity.Speaking -> Color(0xFF66F2D5)
    }
    val targetScale = when (activity) {
        OrbActivity.Idle -> 1f
        OrbActivity.Listening -> 1.2f
        OrbActivity.Processing -> 1.34f
        OrbActivity.Speaking -> 1.22f
    }
    val glow by animateColorAsState(targetGlow, label = "orb-state-glow")
    val activityScale by animateFloatAsState(targetScale, label = "orb-state-scale")

    Box(modifier.semantics { contentDescription = "ZERO assistant orb, ${activity.description}" }) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = minOf(size.width, size.height) * 0.39f
            val glowRadius = radius * 1.75f * glowScale * activityScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow.copy(alpha = 0.24f), violet.copy(alpha = 0.11f), Color.Transparent),
                    center = center,
                    radius = glowRadius,
                ),
                radius = glowRadius,
                center = center,
            )

            fun project(latitude: Float, longitude: Float): Offset {
                val angle = longitude + phase
                val deformation = 1f + 0.035f * sin(longitude * 3f + wave + latitude * 4f)
                val projectedRadius = radius * deformation
                val depth = cos(latitude) * cos(angle)
                val perspective = 0.82f + depth * 0.12f
                return Offset(
                    center.x + cos(latitude) * sin(angle) * projectedRadius * perspective,
                    center.y + sin(latitude) * projectedRadius * perspective,
                )
            }

            val lineWidth = 1.05.dp.toPx()
            for (latitudeIndex in -7..7) {
                val latitude = latitudeIndex * (PI.toFloat() / 16f)
                val path = Path()
                for (segment in 0..72) {
                    val point = project(latitude, segment * (2f * PI.toFloat() / 72f))
                    if (segment == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                val blend = (latitudeIndex + 7) / 14f
                drawPath(
                    path = path,
                    color = mix(blue, pink, blend).copy(alpha = 0.4f + 0.38f * activityScale),
                    style = Stroke(width = lineWidth),
                )
            }
            for (longitudeIndex in 0 until 24) {
                val longitude = longitudeIndex * (2f * PI.toFloat() / 24f)
                val path = Path()
                for (segment in 0..40) {
                    val latitude = -PI.toFloat() / 2f + segment * (PI.toFloat() / 40f)
                    val point = project(latitude, longitude)
                    if (segment == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                drawPath(
                    path = path,
                    color = mix(violet, blue, (longitudeIndex % 24) / 23f).copy(alpha = 0.62f),
                    style = Stroke(width = lineWidth),
                )
            }

            for (particleIndex in 0 until 38) {
                val angle = particleIndex * (2f * PI.toFloat() / 38f) + phase * 0.12f
                val orbit = radius * (1.18f + 0.16f * sin(particleIndex.toFloat() * 2.4f + wave))
                val particle = Offset(center.x + cos(angle) * orbit, center.y + sin(angle) * orbit * 0.72f)
                val alpha = 0.16f + 0.3f * (0.5f + 0.5f * sin(angle + wave))
                drawCircle(glow.copy(alpha = alpha), radius = (1.1f + particleIndex % 3) * density, center = particle)
            }
            drawCircle(
                color = glow.copy(alpha = 0.2f),
                radius = radius * 1.01f,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

private fun mix(start: Color, end: Color, fraction: Float): Color {
    val amount = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * amount,
        green = start.green + (end.green - start.green) * amount,
        blue = start.blue + (end.blue - start.blue) * amount,
        alpha = start.alpha + (end.alpha - start.alpha) * amount,
    )
}