package com.zero.assistant

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
    val view = remember(context) { OrganicOrbView(context) }
    view.setOrbState(activity, reducedMotion)
    DisposableEffect(lifecycleOwner, view) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.safeResume()
                Lifecycle.Event.ON_STOP -> view.safePause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.safePause()
        }
    }
    Box(modifier.semantics { contentDescription = "ZERO assistant orb, ${activity.description}" }) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { view },
            update = { it.setOrbState(activity, reducedMotion) },
        )
    }
}
