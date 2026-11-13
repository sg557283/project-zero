package com.zero.assistant

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val mesh = remember { OrganicOrbGeometry.create(2) }
    Box(modifier.semantics { contentDescription = "ZERO assistant orb, ${activity.description}" }) {
        val transition = rememberInfiniteTransition(label = "assistant-orb")
        val pulse by transition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(tween(1_800), RepeatMode.Reverse),
            label = "orb-pulse",
        )
        val rotation by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing)),
            label = "orb-rotation",
        )
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = minOf(size.width, size.height) * 0.34f
            val scale = if (reducedMotion) 1f else pulse
            val angle = if (reducedMotion) 0f else rotation
            val glow = when (activity) {
                OrbActivity.Idle -> Color(0xFFA18BFF)
                OrbActivity.Listening -> Color(0xFF36E9FF)
                OrbActivity.Processing -> Color(0xFFFF58D7)
                OrbActivity.Speaking -> Color(0xFF54EDFF)
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow.copy(alpha = 0.72f), glow.copy(alpha = 0.32f), Color.Transparent),
                    center = center,
                    radius = radius * 1.85f * scale,
                ),
                center = center,
                radius = radius * 1.85f * scale,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF354D9E), Color(0xFF192A5A), Color(0xFF0A1230)),
                    center = Offset(center.x - radius * 0.3f, center.y - radius * 0.36f),
                    radius = radius * 1.45f,
                ),
                center = center,
                radius = radius * 0.91f * scale,
            )
            val rotationCos = cos(angle)
            val rotationSin = sin(angle)
            for (edgeOffset in mesh.edges.indices step 2) {
                val first = mesh.edges[edgeOffset] * 3
                val second = mesh.edges[edgeOffset + 1] * 3
                val x1 = mesh.vertices[first]
                val y1 = mesh.vertices[first + 1]
                val x2 = mesh.vertices[second]
                val y2 = mesh.vertices[second + 1]
                val start = Offset(
                    center.x + (x1 * rotationCos - y1 * rotationSin) * radius * scale,
                    center.y + (x1 * rotationSin + y1 * rotationCos) * radius * scale,
                )
                val end = Offset(
                    center.x + (x2 * rotationCos - y2 * rotationSin) * radius * scale,
                    center.y + (x2 * rotationSin + y2 * rotationCos) * radius * scale,
                )
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    quadraticTo(
                        center.x + (start.x + end.x - 2f * center.x) * 0.54f,
                        center.y + (start.y + end.y - 2f * center.y) * 0.54f,
                        end.x,
                        end.y,
                    )
                }
                drawPath(path, glow.copy(alpha = 0.94f), style = Stroke(width = 2.2f))
            }
            drawCircle(glow.copy(alpha = 0.3f), radius = radius * 0.72f * scale, center = center, style = Stroke(width = 2f))
            drawCircle(glow.copy(alpha = 1f), radius = radius * 0.91f * scale, center = center, style = Stroke(width = 4f))
        }
    }
}
