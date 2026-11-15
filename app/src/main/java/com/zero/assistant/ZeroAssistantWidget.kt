package com.zero.assistant

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import com.zero.assistant.core.ReminderRepository
import com.zero.assistant.core.ReminderSchedule
import com.zero.assistant.data.PlannerPreferences
import com.zero.assistant.data.TorchController
import com.zero.assistant.data.WakeWordSettings
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.Instant

class ZeroAssistantWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, createViews(context, manager, it)) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        manager.updateAppWidget(appWidgetId, createViews(context, manager, appWidgetId))
    }

    companion object {
        private const val PREFERENCES = "zero_widget"
        private const val KEY_STATUS = "temporary_status"
        private const val KEY_STATUS_UNTIL = "temporary_status_until"
        private val statusHandler = Handler(Looper.getMainLooper())
        const val ACTION_SLOT = "com.zero.assistant.widget.ACTION_SLOT"
        const val EXTRA_SLOT = "com.zero.assistant.widget.EXTRA_SLOT"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, ZeroAssistantWidget::class.java)
            manager.getAppWidgetIds(provider).forEach { id ->
                manager.updateAppWidget(id, createViews(context, manager, id))
            }
        }

        fun setTemporaryStatus(context: Context, status: String, durationMillis: Long = 15_000L) {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STATUS, status)
                .putLong(KEY_STATUS_UNTIL, System.currentTimeMillis() + durationMillis)
                .apply()
            refresh(context)
            statusHandler.postDelayed({ refresh(context.applicationContext) }, durationMillis)
        }

        fun clearTemporaryStatus(context: Context) {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_STATUS)
                .remove(KEY_STATUS_UNTIL)
                .apply()
            refresh(context)
        }

        private fun createViews(context: Context, manager: AppWidgetManager, widgetId: Int): RemoteViews {
            val options = manager.getAppWidgetOptions(widgetId)
            val expanded = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) >= 85
            val views = RemoteViews(context.packageName, R.layout.zero_assistant_widget)
            views.setViewVisibility(R.id.widget_compact, if (expanded) android.view.View.GONE else android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_expanded, if (expanded) android.view.View.VISIBLE else android.view.View.GONE)

            val actions = WidgetActionSettings.actions(context)
            val torchIsOn = runCatching { TorchController.state(context) == true }.getOrDefault(false)
            val status = when {
                WakeWordSettings.active(context) -> "Listening..."
                temporaryStatus(context) != null -> temporaryStatus(context).orEmpty()
                else -> nextReminderStatus(context) ?: "Ready to listen"
            }
            views.setTextViewText(R.id.widget_status, status)

            for (slot in 0..3) {
                val label = if (actions[slot] == WidgetQuickAction.Flashlight) {
                    if (torchIsOn) "Torch on" else "Flashlight"
                } else {
                    actions[slot].label
                }
                if (slot < 2) {
                    views.setTextViewText(actionViewId(slot), label)
                    views.setOnClickPendingIntent(actionViewId(slot), slotPendingIntent(context, slot))
                }
                views.setTextViewText(expandedActionViewId(slot), label)
                views.setOnClickPendingIntent(expandedActionViewId(slot), slotPendingIntent(context, slot))
            }
            views.setOnClickPendingIntent(R.id.widget_mic_compact, voicePendingIntent(context))
            views.setOnClickPendingIntent(R.id.widget_mic_expanded, voicePendingIntent(context))
            return views
        }

        private fun temporaryStatus(context: Context): String? {
            val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val until = preferences.getLong(KEY_STATUS_UNTIL, 0L)
            if (System.currentTimeMillis() >= until) return null
            return preferences.getString(KEY_STATUS, null)
        }

        private fun nextReminderStatus(context: Context): String? = runCatching {
            val now = System.currentTimeMillis()
            val next = ReminderRepository(PlannerPreferences(context)).all()
                .mapNotNull { reminder ->
                    ReminderSchedule.nextTriggerEpochMillis(reminder, now, ZoneId.systemDefault())
                        ?.let { it to reminder.title }
                }
                .minByOrNull { it.first }
                ?: return@runCatching null
            val time = DateTimeFormatter.ofPattern("EEE h:mm a")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(next.first))
            "Next Reminder · ${next.second} · $time"
        }.getOrNull()

        private fun slotPendingIntent(context: Context, slot: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                8200 + slot,
                Intent(context, WidgetActionReceiver::class.java)
                    .setAction(ACTION_SLOT)
                    .putExtra(EXTRA_SLOT, slot),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun voicePendingIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                8290,
                Intent(context, TransparentVoiceTriggerActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun actionViewId(slot: Int): Int = when (slot) {
            0 -> R.id.widget_action_0
            1 -> R.id.widget_action_1
            else -> throw IllegalArgumentException("Only compact widget action slots 0 and 1 are visible.")
        }

        private fun expandedActionViewId(slot: Int): Int = when (slot) {
            0 -> R.id.widget_expanded_action_0
            1 -> R.id.widget_expanded_action_1
            2 -> R.id.widget_expanded_action_2
            3 -> R.id.widget_expanded_action_3
            else -> throw IllegalArgumentException("Widget action slot is out of range.")
        }
    }
}
