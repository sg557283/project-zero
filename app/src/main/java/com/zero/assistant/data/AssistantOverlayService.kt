package com.zero.assistant.data

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.zero.assistant.BotFaceMode
import com.zero.assistant.DotMatrixBotFace
import com.zero.assistant.ui.OverlayBridgeShape

internal class AssistantOverlayService(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
        ?: throw IllegalStateException("Android window service is unavailable.")
    private val handler = Handler(Looper.getMainLooper())
    private var view: ComposeView? = null
    private var owner: OverlayLifecycleOwner? = null
    private var attached = false
    private val dismissRunnable = Runnable { dismiss() }

    fun show(
        message: String,
        dismissAfterMillis: Long,
        preview: Boolean = false,
        pendingText: String? = null,
        faceMode: BotFaceMode = BotFaceMode.Listening,
    ) {
        val composeView = view ?: createView().also { view = it }
        composeView.setContent {
            OverlayPanel(
                message = message,
                faceMode = faceMode,
                style = OverlayPreferencesRepository.style(service),
                locked = isDeviceLocked(),
                reduceMotion = service.getSharedPreferences("zero_accessibility", Context.MODE_PRIVATE)
                    .getBoolean("reduced_motion", false),
                preview = preview,
                pendingText = pendingText,
                onAction = ::onPanelAction,
                onDismiss = ::dismiss,
            )
        }
        if (!attached) {
            windowManager.addView(composeView, layoutParams())
            attached = true
        } else {
            windowManager.updateViewLayout(composeView, layoutParams())
        }
        handler.removeCallbacks(dismissRunnable)
        if (dismissAfterMillis > 0L) handler.postDelayed(dismissRunnable, dismissAfterMillis)
    }

    fun dismiss() {
        handler.removeCallbacks(dismissRunnable)
        val current = view
        if (attached && current != null) {
            windowManager.removeView(current)
            attached = false
        }
        owner?.destroy()
        owner = null
        view = null
    }

    private fun createView(): ComposeView {
        val lifecycleOwner = OverlayLifecycleOwner().also { owner = it }
        return ComposeView(service).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
        }
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val style = OverlayPreferencesRepository.style(service)
        val rightEdge = style == OverlayDesignStyle.RightEdge
        val screenWidthDp = service.resources.displayMetrics.widthPixels / service.resources.displayMetrics.density
        val width = dp(minOf(if (rightEdge) 250 else 360, (screenWidthDp - 12f).toInt()))
        return WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = if (rightEdge) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = if (rightEdge) 0 else dp(28)
        }
    }

    private fun isDeviceLocked(): Boolean =
        service.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true

    private fun onPanelAction(action: String) {
        when (action) {
            "pause music" -> WakeWordSettings.requestOnScreenAction(service, action)
            "tap-help" -> show("Say “tap [button]” to choose a visible control.", 6_000L, faceMode = BotFaceMode.ActingOnScreen)
            "type-help" -> show("Say “type [text]” to enter text into the focused field.", 6_000L, faceMode = BotFaceMode.ActingOnScreen)
            "confirm-type", "cancel-type" -> WakeWordSettings.requestOnScreenAction(service, action)
        }
    }

    private fun dp(value: Int): Int = (value * service.resources.displayMetrics.density).toInt()
}

@Composable
private fun OverlayPanel(
    message: String,
    faceMode: BotFaceMode,
    style: OverlayDesignStyle,
    locked: Boolean,
    reduceMotion: Boolean,
    preview: Boolean,
    pendingText: String?,
    onAction: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val rightEdge = style == OverlayDesignStyle.RightEdge
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = if (rightEdge) Alignment.CenterEnd else Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(start = 25.dp),
            shape = OverlayBridgeShape(),
            color = Color(0xF2090A0F),
            shadowElevation = 14.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(Color(0xFF22D3EE).copy(alpha = 0.62f), Color(0xFFF43F9E).copy(alpha = 0.48f)),
                ),
            ),
        ) {
            Column(
                Modifier.fillMaxSize().padding(start = 34.dp, end = 8.dp, top = 7.dp, bottom = 5.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                if (locked) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Unlock to review or use screen actions.",
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                        )
                        TextButton(onClick = onDismiss, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                            Text("×", maxLines = 1, softWrap = false, fontSize = 19.sp)
                        }
                    }
                } else if (pendingText != null) {
                    Text(
                        message,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        pendingText,
                        color = Color(0xFFB8C2D8),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onAction("confirm-type") }) {
                            Text("Insert", maxLines = 1, softWrap = false)
                        }
                        TextButton(onClick = { onAction("cancel-type") }) {
                            Text("Cancel", maxLines = 1, softWrap = false)
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            message,
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!locked) OverlayWaveform(reduceMotion)
                        if (!locked) {
                            TextButton(onClick = { onAction("pause music") }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                Text("▶/Ⅱ", maxLines = 1, softWrap = false, fontSize = 11.sp)
                            }
                            TextButton(onClick = { onAction("tap-help") }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                Text("TAP", maxLines = 1, softWrap = false, fontSize = 10.sp)
                            }
                            TextButton(onClick = { onAction("type-help") }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                Text("TYPE", maxLines = 1, softWrap = false, fontSize = 10.sp)
                            }
                        }
                        TextButton(onClick = onDismiss, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                            Text("×", maxLines = 1, softWrap = false, fontSize = 19.sp)
                        }
                    }
                    if (preview) {
                        Text("Preview overlay", color = Color(0xFF22D3EE), fontSize = 10.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
        DotMatrixBotFace(
            mode = faceMode,
            reduceMotion = reduceMotion,
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }
}

@Composable
private fun OverlayWaveform(reduceMotion: Boolean) {
    val transition = rememberInfiniteTransition(label = "overlay-waveform")
    val phase by transition.animateFloat(
        initialValue = if (reduceMotion) 0.5f else 0f,
        targetValue = if (reduceMotion) 0.5f else 1f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "waveform-phase",
    )
    Canvas(Modifier.size(width = 22.dp, height = 16.dp).padding(horizontal = 3.dp)) {
        val barWidth = size.width / 7f
        for (index in 0 until 5) {
            val wave = if (reduceMotion) 0.5f else (phase + index * 0.21f) % 1f
            val barHeight = size.height * (0.25f + wave * 0.7f)
            val x = index * barWidth * 1.5f + barWidth / 2f
            drawLine(
                color = if (index % 2 == 0) Color(0xFF22D3EE) else Color(0xFFF43F9E),
                start = Offset(x, (size.height - barHeight) / 2f),
                end = Offset(x, (size.height + barHeight) / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

private class OverlayLifecycleOwner :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    init {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
