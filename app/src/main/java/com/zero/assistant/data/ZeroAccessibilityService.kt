package com.zero.assistant.data

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import com.zero.assistant.core.OnScreenAction
import com.zero.assistant.core.OnScreenActionParser
import com.zero.assistant.core.AssistantTrigger
import com.zero.assistant.BotFaceMode
import com.zero.assistant.FloatingBotOverlayService
import com.zero.assistant.FloatingBotSettings
import java.util.ArrayDeque
import java.util.Locale

open class ZeroAccessibilityService : AccessibilityService() {
    private lateinit var edgePanel: AssistantOverlayService
    private var volumeUpPressed = false
    private var volumeDownPressed = false
    private var volumeUpDownAt = 0L
    private var volumeDownDownAt = 0L
    private var volumePairLatched = false
    private var pendingSensitiveText: String? = null
    private var pendingSensitivePackage: String? = null
    private var pendingSensitiveFieldId: String? = null
    private var screenReaderTts: TextToSpeech? = null
    private var screenReaderReady = false
    private val assistantPanelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!::edgePanel.isInitialized) return
            val receivedIntent = intent ?: return
            if (receivedIntent.action == WakeWordSettings.ACTION_ON_SCREEN_ACTION) {
                handleOnScreenAction(receivedIntent.getStringExtra(WakeWordSettings.EXTRA_ON_SCREEN_ACTION).orEmpty())
                return
            }
            val enabled = getSharedPreferences("zero_accessibility", Context.MODE_PRIVATE)
                .getBoolean("assistant_edge_panel", false)
            val preview = receivedIntent.getBooleanExtra(WakeWordSettings.EXTRA_PANEL_PREVIEW, false)
            if ((!enabled && !preview) || receivedIntent.getBooleanExtra(WakeWordSettings.EXTRA_PANEL_DISMISS, false)) {
                edgePanel.dismiss()
            } else {
                edgePanel.show(
                    receivedIntent.getStringExtra(WakeWordSettings.EXTRA_PANEL_MESSAGE).orEmpty(),
                    receivedIntent.getLongExtra(WakeWordSettings.EXTRA_PANEL_DISMISS_AFTER_MS, 0L),
                    preview = preview,
                    pendingText = receivedIntent.getStringExtra(WakeWordSettings.EXTRA_PANEL_PENDING_TEXT),
                    faceMode = receivedIntent.getStringExtra(WakeWordSettings.EXTRA_PANEL_FACE_MODE)
                        ?.let { runCatching { BotFaceMode.valueOf(it) }.getOrNull() }
                        ?: BotFaceMode.Listening,
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        screenReaderTts = TextToSpeech(applicationContext) { status ->
            val engine = screenReaderTts ?: return@TextToSpeech
            val voice = engine.voices.orEmpty()
                .filterNot { it.isNetworkConnectionRequired }
                .firstOrNull { it.locale.language == Locale.getDefault().language }
                ?: engine.voices.orEmpty().firstOrNull { !it.isNetworkConnectionRequired }
            screenReaderReady = status == TextToSpeech.SUCCESS &&
                voice != null && runCatching { engine.voice = voice }.isSuccess
        }
        edgePanel = AssistantOverlayService(this)
        ContextCompat.registerReceiver(
            this,
            assistantPanelReceiver,
            IntentFilter().apply {
                addAction(WakeWordSettings.ACTION_ASSISTANT_PANEL)
                addAction(WakeWordSettings.ACTION_ON_SCREEN_ACTION)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo?.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            eventTypes = 0
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        } ?: AccessibilityServiceInfo().apply {
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (!HardwareActivationSettings.volumePairEnabled(this) || event == null) return false
        val isVolumeUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isVolumeDown = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (!isVolumeUp && !isVolumeDown) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (isVolumeUp && !volumeUpPressed) {
                volumeUpPressed = true
                volumeUpDownAt = event.eventTime
            } else if (isVolumeDown && !volumeDownPressed) {
                volumeDownPressed = true
                volumeDownDownAt = event.eventTime
            }
            if (volumeUpPressed && volumeDownPressed && !volumePairLatched) {
                val syncWindow = kotlin.math.abs(volumeUpDownAt - volumeDownDownAt)
                if (syncWindow <= VOLUME_PAIR_SYNC_WINDOW_MS) {
                    volumePairLatched = true
                    activateVolumeTrigger()
                }
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            if (isVolumeUp) {
                volumeUpPressed = false
                volumeUpDownAt = 0L
            } else {
                volumeDownPressed = false
                volumeDownDownAt = 0L
            }
            if (!volumeUpPressed && !volumeDownPressed) volumePairLatched = false
        }
        return false
    }

    private fun activateVolumeTrigger() {
        if (AssistantActionSettings.testModeEnabled(this)) {
            val message = "${AssistantTrigger.VOLUME_UP_DOWN.label} detected."
            screenReaderTts?.takeIf { screenReaderReady }
                ?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "zero-trigger-test")
            sendBroadcast(
                Intent(ActionExecutor.ACTION_TRIGGER_TEST_FEEDBACK)
                    .setPackage(packageName)
                    .putExtra(ActionExecutor.EXTRA_TRIGGER_TEST_MESSAGE, message),
            )
        } else {
            if (FloatingBotSettings.volumeToggleEnabled(this)) {
                if (!FloatingBotOverlayService.toggle(this) &&
                    FloatingBotOverlayService.hasOverlayPermission(this)
                ) {
                    WakeWordSettings.updateAssistantPanel(this, "ZERO could not display the floating bot.", 5_000L)
                }
            } else {
                ActionExecutor.execute(this, AssistantActionSettings.actionFor(this, AssistantTrigger.VOLUME_UP_DOWN)) { message ->
                    screenReaderTts?.takeIf { screenReaderReady }
                        ?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "zero-trigger-feedback")
                }
            }
        }
    }

    private fun handleOnScreenAction(commandText: String) {
        if (!getSharedPreferences("zero_accessibility", Context.MODE_PRIVATE)
                .getBoolean("assistant_edge_panel", false)
        ) {
            WakeWordSettings.updateAssistantPanel(this, "Enable Assistant Edge Panel to use screen actions.", 6_000L)
            return
        }
        if (getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true) {
            pendingSensitiveText = null
            pendingSensitivePackage = null
            pendingSensitiveFieldId = null
            WakeWordSettings.updateAssistantPanel(this, "Unlock to use screen actions.", 6_000L)
            return
        }
        when (commandText) {
            "confirm-type" -> confirmSensitiveType()
            "cancel-type" -> {
                pendingSensitiveText = null
                pendingSensitivePackage = null
                pendingSensitiveFieldId = null
                WakeWordSettings.updateAssistantPanel(this, "Text entry cancelled.", 4_000L, faceMode = BotFaceMode.Idle.name)
            }
            else -> when (val action = OnScreenActionParser.parse(commandText)) {
                null -> WakeWordSettings.updateAssistantPanel(this, "I couldn't match that screen command.", 5_000L)
                is OnScreenAction.TypeText -> typeIntoFocusedField(action.text)
                else -> executeScreenAction(action)
            }
        }
    }

    private fun executeScreenAction(action: OnScreenAction) {
        val root = rootInActiveWindow
        val message = when (action) {
            is OnScreenAction.Click -> {
                val match = findMatchingNode(root, action.label)
                val clickable = match?.let(::findClickableAncestor)
                if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                    "Tapped ${action.label}."
                } else {
                    "I couldn't tap a visible “${action.label}” control."
                }
            }
            OnScreenAction.ScrollForward, OnScreenAction.ScrollBackward -> {
                val scrollable = findScrollableNode(root)
                val direction = if (action == OnScreenAction.ScrollForward) {
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                } else {
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                }
                if (scrollable?.performAction(direction) == true) "Scrolled." else "No scrollable area is available."
            }
            OnScreenAction.Back -> if (performGlobalAction(GLOBAL_ACTION_BACK)) "Went back." else "Back action was unavailable."
            OnScreenAction.Home -> if (performGlobalAction(GLOBAL_ACTION_HOME)) "Went to Home." else "Home action was unavailable."
            OnScreenAction.PlayPause -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Toggled music.")
            OnScreenAction.NextTrack -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT, "Skipped to the next track.")
            OnScreenAction.PreviousTrack -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Returned to the previous track.")
            OnScreenAction.ReadScreen -> readActiveScreen(summarize = false)
            OnScreenAction.SummarizeScreen -> readActiveScreen(summarize = true)
            is OnScreenAction.TypeText -> return typeIntoFocusedField(action.text)
        }
        WakeWordSettings.updateAssistantPanel(
            this,
            message,
            6_000L,
            faceMode = when {
                message.startsWith("I couldn't") || message.startsWith("No ") || message.contains("unavailable") ->
                    BotFaceMode.Idle.name
                else -> BotFaceMode.Success.name
            },
        )
    }

    private fun readActiveScreen(summarize: Boolean): String {
        val visibleText = findNodes(rootInActiveWindow)
            .asSequence()
            .filter { it.isVisibleToUser }
            .flatMap { sequenceOf(it.text, it.contentDescription) }
            .mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
            .take(if (summarize) 6 else 12)
            .joinToString(". ")
            .take(MAX_SCREEN_READING_CHARS)
        if (visibleText.isBlank()) return "There is no readable text on the active screen."
        val spoken = if (summarize) visibleText.substringBeforeLast(". ", visibleText) else visibleText
        if (screenReaderReady) {
            screenReaderTts?.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "zero-screen-reader")
        }
        return if (screenReaderReady) {
            if (summarize) "Screen summary: $spoken" else "Reading screen: $spoken"
        } else {
            "Offline text-to-speech is unavailable. Visible text: $spoken"
        }
    }

    private fun typeIntoFocusedField(text: String) {
        val root = rootInActiveWindow
        if (root == null) {
            WakeWordSettings.updateAssistantPanel(this, "No focused text field is available.", 6_000L)
            return
        }
        val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditableNode(root)
        if (field == null || !field.isEditable) {
            WakeWordSettings.updateAssistantPanel(this, "No focused text field is available.", 6_000L)
            return
        }
        val packageName = root.packageName?.toString().orEmpty()
        if (packageName in SENSITIVE_MESSAGE_PACKAGES) {
            pendingSensitiveText = text
            pendingSensitivePackage = packageName
            pendingSensitiveFieldId = field.viewIdResourceName
            WakeWordSettings.updateAssistantPanel(
                this,
                "Review text before inserting it.",
                preview = true,
                faceMode = BotFaceMode.ActingOnScreen.name,
                pendingText = text,
            )
            return
        }
        val succeeded = field.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) },
        )
        WakeWordSettings.updateAssistantPanel(
            this,
            if (succeeded) "Text entered in the focused field." else "The focused field rejected text entry.",
            6_000L,
            faceMode = if (succeeded) BotFaceMode.Success.name else BotFaceMode.Idle.name,
        )
    }

    private fun confirmSensitiveType() {
        val text = pendingSensitiveText
        val expectedPackage = pendingSensitivePackage
        val expectedFieldId = pendingSensitiveFieldId
        pendingSensitiveText = null
        pendingSensitivePackage = null
        pendingSensitiveFieldId = null
        if (text == null || expectedPackage == null) {
            WakeWordSettings.updateAssistantPanel(this, "There is no text waiting for confirmation.", 5_000L)
            return
        }
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() != expectedPackage) {
            WakeWordSettings.updateAssistantPanel(this, "The active app changed. Text was not inserted.", 6_000L)
            return
        }
        val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditableNode(root)
        if (expectedFieldId != null && field?.viewIdResourceName != expectedFieldId) {
            WakeWordSettings.updateAssistantPanel(this, "The text field changed. Text was not inserted.", 6_000L)
            return
        }
        val succeeded = field != null && field.isEditable && field.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) },
        )
        WakeWordSettings.updateAssistantPanel(
            this,
            if (succeeded) "Confirmed text was inserted. It was not sent." else "Text could not be inserted.",
            7_000L,
            faceMode = if (succeeded) BotFaceMode.Success.name else BotFaceMode.Idle.name,
        )
    }

    private fun findMatchingNode(root: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? =
        findNodes(root).firstOrNull { node ->
            node.isVisibleToUser && (
                node.text?.toString()?.trim()?.equals(label, ignoreCase = true) == true ||
                    node.contentDescription?.toString()?.trim()?.equals(label, ignoreCase = true) == true
                )
        }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    private fun findEditableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
        findNodes(root).firstOrNull { it.isVisibleToUser && it.isEditable }

    private fun findScrollableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
        findNodes(root).firstOrNull { it.isVisibleToUser && it.isScrollable }

    private fun findNodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        val result = mutableListOf<AccessibilityNodeInfo>()
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            result += node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(pending::addLast)
            }
        }
        return result
    }

    private fun dispatchMediaKey(keyCode: Int, message: String): String {
        val audioManager = getSystemService(AudioManager::class.java)
            ?: return "Media controls are unavailable."
        val now = android.os.SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)
        audioManager.dispatchMediaKeyEvent(down)
        audioManager.dispatchMediaKeyEvent(up)
        return message
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() {
        volumeUpPressed = false
        volumeDownPressed = false
        volumeUpDownAt = 0L
        volumeDownDownAt = 0L
        volumePairLatched = false
    }

    override fun onDestroy() {
        unregisterReceiver(assistantPanelReceiver)
        edgePanel.dismiss()
        screenReaderTts?.stop()
        screenReaderTts?.shutdown()
        screenReaderTts = null
        super.onDestroy()
    }

    private companion object {
        const val VOLUME_PAIR_SYNC_WINDOW_MS = 150L
        const val MAX_SCREEN_READING_CHARS = 600
        val SENSITIVE_MESSAGE_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.google.android.apps.messaging",
            "org.telegram.messenger",
            "org.thoughtcrime.securesms",
            "com.facebook.orca",
            "com.viber.voip",
            "com.discord",
            "com.instagram.android",
            "com.snapchat.android",
            "com.skype.raider",
        )
    }
}
