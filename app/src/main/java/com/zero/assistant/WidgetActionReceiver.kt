package com.zero.assistant

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.zero.assistant.data.TorchController

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ZeroAssistantWidget.ACTION_SLOT) return
        val slot = intent.getIntExtra(ZeroAssistantWidget.EXTRA_SLOT, -1)
        val action = WidgetActionSettings.actions(context).getOrNull(slot) ?: return
        when (action) {
            WidgetQuickAction.Flashlight -> toggleFlashlight(context)
            WidgetQuickAction.Scanner -> openPage(context, "Scanner")
            WidgetQuickAction.VoiceNote -> openPage(context, "Notes")
            WidgetQuickAction.Emergency -> openPage(context, "Emergency")
        }
    }

    private fun toggleFlashlight(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ZeroAssistantWidget.setTemporaryStatus(context, "Allow camera access to use flashlight", 5_000L)
            context.startActivity(
                Intent(context, TransparentWidgetCameraActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        TorchController.toggle(context).fold(
            onSuccess = { enabled ->
                ZeroAssistantWidget.setTemporaryStatus(context, if (enabled) "Flashlight on" else "Flashlight off", 2_500L)
            },
            onFailure = { failure ->
                ZeroAssistantWidget.setTemporaryStatus(
                    context,
                    failure.message?.take(36) ?: "Flashlight unavailable",
                    5_000L,
                )
            },
        )
    }

    private fun openPage(context: Context, page: String) {
        ZeroAssistantWidget.setTemporaryStatus(context, "Opening $page", 3_000L)
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(com.zero.assistant.data.ActionExecutor.EXTRA_OPEN_PAGE, page),
        )
    }
}
