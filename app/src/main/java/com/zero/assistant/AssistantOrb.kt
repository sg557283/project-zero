package com.zero.assistant

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.concurrent.atomic.AtomicReference
import java.util.Locale

internal enum class OrbActivity(val description: String) {
    Idle("idle"),
    Listening("listening"),
    Processing("processing"),
    Speaking("speaking"),
}

internal enum class OrbEmotionCue(val emoji: String, val description: String) {
    Neutral("·", "neutral"),
    Listening("●", "listening"),
    Thinking("🤔", "thinking"),
    Happy("😊", "happy"),
    Curious("❓", "curious"),
    Warning("⚠️", "warning"),
    SmartIdea("💡", "idea"),
    Cool("😎", "cool"),
}

internal fun inferOrbEmotionCue(response: String?): OrbEmotionCue {
    val text = response?.lowercase(Locale.ROOT)?.trim().orEmpty()
    if (text.isEmpty()) return OrbEmotionCue.Neutral
    fun containsWords(vararg words: String): Boolean =
        words.any { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(text) }
    fun containsPhrases(vararg phrases: String): Boolean = phrases.any(text::contains)

    return when {
        containsWords("warning", "offline", "unavailable", "error", "failed", "denied", "cannot", "can't", "couldn't", "issue")
            || containsPhrases("could not", "not running", "not available", "not ready", "problem") -> OrbEmotionCue.Warning
        '?' in text || containsWords("which", "what", "who", "when", "where", "how", "clarify", "confirm")
            || containsPhrases("please specify") -> OrbEmotionCue.Curious
        containsWords("suggest", "option", "shortcut") || containsPhrases("tip:", "idea:", "try ", "you can")
            -> OrbEmotionCue.SmartIdea
        containsWords("cool", "awesome", "nice") || "😎" in text -> OrbEmotionCue.Cool
        containsWords("done", "saved", "opened", "completed", "success", "successfully", "ready", "found", "great", "thanks")
            || "😊" in text -> OrbEmotionCue.Happy
        else -> OrbEmotionCue.Neutral
    }
}

@Composable
internal fun AssistantOrb(
    modifier: Modifier = Modifier,
    size: Dp = 260.dp,
    activity: OrbActivity = OrbActivity.Idle,
    audioAmplitude: Float = 0f,
    reduceMotion: Boolean = false,
    onClick: (() -> Unit)? = null,
    emotionCue: OrbEmotionCue = OrbEmotionCue.Neutral,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val orbView = remember { AtomicReference<OrganicOrbView?>(null) }

    DisposableEffect(lifecycleOwner, orbView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> orbView.get()?.safeResume()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> orbView.get()?.safePause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            orbView.getAndSet(null)?.safePause()
        }
    }

    val orbModifier = modifier
        .defaultMinSize(minWidth = size, minHeight = size)
        .then(
            if (onClick != null) {
                Modifier.clickable(role = Role.Button, onClick = onClick)
            } else {
                Modifier
            },
        )
        .semantics {
            contentDescription = "ZERO assistant orb, ${activity.description}"
            if (onClick != null) role = Role.Button
        }

    val visibleCue = when (activity) {
        OrbActivity.Listening -> OrbEmotionCue.Listening
        OrbActivity.Processing -> OrbEmotionCue.Thinking
        OrbActivity.Idle, OrbActivity.Speaking -> emotionCue
    }

    Box(orbModifier) {
        AndroidView(
            factory = { context ->
                OrganicOrbView(context).also(orbView::set)
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                orbView.set(view)
                view.setOrbState(activity, audioAmplitude, reduceMotion)
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    view.safeResume()
                } else {
                    view.safePause()
                }
            },
        )
        OrbitalEmotionBadge(
            cue = visibleCue,
            reduceMotion = reduceMotion,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-12).dp, y = 12.dp),
        )
    }
}

@Composable
internal fun AssistantOrb(
    activity: OrbActivity,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    AssistantOrb(
        modifier = modifier,
        activity = activity,
        reduceMotion = reducedMotion,
    )
}

@Composable
private fun OrbitalEmotionBadge(
    cue: OrbEmotionCue,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderBrush = Brush.linearGradient(
        listOf(Color(0xFF48E7F1).copy(alpha = 0.78f), Color(0xFF8B78F6).copy(alpha = 0.72f)),
    )
    Box(
        modifier
            .size(38.dp)
            .shadow(8.dp, CircleShape)
            .background(Color(0xFF131620), CircleShape)
            .border(BorderStroke(1.dp, borderBrush), CircleShape)
            .semantics(mergeDescendants = true) {
                contentDescription = "Orb cue: ${cue.description}"
            },
        contentAlignment = Alignment.Center,
    ) {
        when (cue) {
            OrbEmotionCue.Listening -> ListeningCue(reduceMotion)
            OrbEmotionCue.Thinking -> ThinkingCue(reduceMotion)
            else -> if (reduceMotion) {
                CueEmoji(cue.emoji)
            } else {
                Crossfade(
                    targetState = cue,
                    animationSpec = tween(durationMillis = 240),
                    label = "orb-emotion-cue",
                ) { targetCue ->
                    CueEmoji(targetCue.emoji)
                }
            }
        }
    }
}

@Composable
private fun ListeningCue(reduceMotion: Boolean) {
    if (reduceMotion) {
        Box(
            Modifier.size(7.dp).background(Color(0xFF55E8F2), CircleShape),
        )
    } else {
        val transition = rememberInfiniteTransition(label = "orb-listening-cue")
        val first by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
            label = "orb-wave-one",
        )
        val second by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
            label = "orb-wave-two",
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CueWaveBar(first)
            CueWaveBar(second)
            CueWaveBar(first)
        }
    }
}

@Composable
private fun CueWaveBar(level: Float) {
    Box(
        Modifier
            .width(2.dp)
            .height((5f + level * 9f).dp)
            .background(Color(0xFF55E8F2), RoundedCornerShape(50)),
    )
}

@Composable
private fun ThinkingCue(reduceMotion: Boolean) {
    if (reduceMotion) {
        CueEmoji(OrbEmotionCue.Thinking.emoji)
    } else {
        val transition = rememberInfiniteTransition(label = "orb-thinking-cue")
        val first by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(640), RepeatMode.Reverse),
            label = "orb-thinking-one",
        )
        val second by transition.animateFloat(
            initialValue = 0.65f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(640, delayMillis = 140), RepeatMode.Reverse),
            label = "orb-thinking-two",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            CueDot(first)
            CueDot(second)
            CueDot(first)
        }
    }
}

@Composable
private fun CueDot(alpha: Float) {
    Box(Modifier.size(4.dp).background(Color(0xFF8B78F6).copy(alpha = alpha), CircleShape))
}

@Composable
private fun CueEmoji(emoji: String) {
    androidx.compose.material3.Text(
        text = emoji,
        fontSize = 19.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
    )
}
