package com.zero.assistant

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = remember(context) {
        if (OrganicOrbView.supportsOpenGl(context)) OrganicOrbView(context) else null
    }
    view?.setOrbState(activity, reducedMotion)
    DisposableEffect(lifecycleOwner, view) {
        val currentView = view ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> currentView.safeResume()
                Lifecycle.Event.ON_STOP -> currentView.safePause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            currentView.safePause()
        }
    }
    Box(modifier.semantics { contentDescription = "ZERO assistant orb, ${activity.description}" }) {
        if (view != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { view },
                update = { it.setOrbState(activity, reducedMotion) },
            )
        } else {
            StaticOrbFallback(activity)
        }
    }
}

@Composable
private fun StaticOrbFallback(activity: OrbActivity) {
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) * 0.36f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF050817), Color(0x66101638), Color.Transparent),
                center = center,
                radius = radius * 1.55f,
            ),
            center = center,
            radius = radius * 1.55f,
        )
        val glow = when (activity) {
            OrbActivity.Idle -> Color(0xFF684CFF)
            OrbActivity.Listening -> Color(0xFF19D8FF)
            OrbActivity.Processing -> Color(0xFFF02BBE)
            OrbActivity.Speaking -> Color(0xFF19D8FF)
        }
        val mesh = OrganicOrbGeometry.create(3)
        for (edgeOffset in mesh.edges.indices step 2) {
            val first = mesh.edges[edgeOffset] * 3
            val second = mesh.edges[edgeOffset + 1] * 3
            val x1 = center.x + mesh.vertices[first] * radius
            val y1 = center.y + mesh.vertices[first + 1] * radius
            val x2 = center.x + mesh.vertices[second] * radius
            val y2 = center.y + mesh.vertices[second + 1] * radius
            val path = Path().apply {
                moveTo(x1, y1)
                val bend = 1f + 0.12f * kotlin.math.sin((edgeOffset + y1).toDouble()).toFloat()
                quadraticTo(
                    center.x + (x1 + x2) / 2f * bend,
                    center.y + (y1 + y2) / 2f * bend,
                    x2,
                    y2,
                )
            }
            drawPath(path, glow.copy(alpha = 0.22f + 0.28f * ((mesh.vertices[first + 2] + 1f) / 2f)), style = Stroke(width = 1f))
        }
        drawCircle(glow.copy(alpha = 0.45f), radius = radius, center = center, style = Stroke(width = 1f))
    }
}
