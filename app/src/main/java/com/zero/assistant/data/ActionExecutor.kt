package com.zero.assistant.data

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
import com.zero.assistant.MainActivity
import com.zero.assistant.BotBubbleAction
import com.zero.assistant.TransparentWidgetCameraActivity
import com.zero.assistant.TransparentVoiceTriggerActivity
import com.zero.assistant.core.AssistantAction

object ActionExecutor {
    const val EXTRA_OPEN_PAGE = "com.zero.assistant.extra.OPEN_PAGE"
    const val ACTION_TRIGGER_TEST_FEEDBACK = "com.zero.assistant.action.TRIGGER_TEST_FEEDBACK"
    const val EXTRA_TRIGGER_TEST_MESSAGE = "com.zero.assistant.extra.TRIGGER_TEST_MESSAGE"

    fun execute(
        context: Context,
        action: AssistantAction,
        speak: (String) -> Unit = {},
    ): String {
        val result = when (action) {
            AssistantAction.OPEN_ASSISTANT -> {
                startCommandListening(context)
            }
            AssistantAction.OPEN_UPI_SCANNER -> {
                openPage(context, "Scanner")
                "Opening ZERO's scanner. It does not initiate payments."
            }
            AssistantAction.OPEN_CAMERA -> {
                val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) == null) "No camera app is available."
                else {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "Opening the camera."
                }
            }
            AssistantAction.TOGGLE_FLASHLIGHT -> TorchController.toggle(context)
                .fold({ if (it) "Flashlight on." else "Flashlight off." }, { it.message ?: "Flashlight could not be changed." })
            AssistantAction.AUTO_CLICK_TARGET -> {
                val response = startCommandListening(context)
                if (response.startsWith("ZERO is listening")) {
                    "ZERO is listening. Say “click” followed by the visible control you want tapped."
                } else response
            }
            AssistantAction.SCREEN_NAVIGATION -> {
                WakeWordSettings.requestOnScreenAction(context, "go back")
                "Requested the accessibility Back action. Enable ZERO Accessibility and its Edge Panel if needed."
            }
            AssistantAction.SYSTEM_MEDIA_CONTROL -> dispatchMediaToggle(context)
            AssistantAction.OFFLINE_CALL_SMS -> {
                val intent = Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) == null) "No phone dialer is available."
                else {
                    context.startActivity(intent)
                    "Opened the dialer. Review the number before placing a call."
                }
            }
            AssistantAction.EMERGENCY_SOS -> {
                openPage(context, "Emergency")
                "Opening Emergency Quick Mode. Review actions before sending; standard SMS is not satellite messaging."
            }
            AssistantAction.OFFLINE_VOICE_NOTE -> {
                openPage(context, "Notes")
                "Opening your local notes."
            }
        }
        speak(result)
        return result
    }

    fun executeBotBubble(context: Context, bubble: BotBubbleAction): String {
        when (bubble) {
            BotBubbleAction.Voice -> {
                context.startActivity(
                    Intent(context, TransparentVoiceTriggerActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return "Starting an on-device voice command."
            }
            BotBubbleAction.Flashlight -> {
                context.startActivity(
                    Intent(context, TransparentWidgetCameraActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return "Checking flashlight access."
            }
            else -> Unit
        }
        val action = bubble.assistantAction ?: return "This floating bot action is unavailable."
        return execute(context, action)
    }

    private fun dispatchMediaToggle(context: Context): String {
        val manager = context.getSystemService(AudioManager::class.java) ?: return "Media controls are unavailable."
        val now = android.os.SystemClock.uptimeMillis()
        manager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
        manager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
        return "Toggled media playback."
    }

    private fun startCommandListening(context: Context): String =
        runCatching {
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                Intent(context, WakeWordService::class.java).setAction(WakeWordSettings.ACTION_ACTIVATE),
            )
        }.fold(
            onSuccess = { "ZERO is listening for a command." },
            onFailure = { "ZERO could not start listening: ${it.message ?: "microphone service unavailable"}." },
        )

    private fun openPage(context: Context, page: String) {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_PAGE, page),
        )
    }

}
