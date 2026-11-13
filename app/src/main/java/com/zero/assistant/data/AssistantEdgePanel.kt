package com.zero.assistant.data

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

internal class AssistantEdgePanel(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
        ?: throw IllegalStateException("Android window service is unavailable.")
    private val handler = Handler(Looper.getMainLooper())
    private var container: LinearLayout? = null
    private var statusText: TextView? = null
    private var attached = false
    private val dismissRunnable = Runnable { dismiss() }

    fun show(message: String, dismissAfterMillis: Long) {
        val view = container ?: createView().also { container = it }
        statusText?.text = message
        if (!attached) {
            windowManager.addView(view, layoutParams())
            attached = true
        }
        handler.removeCallbacks(dismissRunnable)
        if (dismissAfterMillis > 0L) handler.postDelayed(dismissRunnable, dismissAfterMillis)
    }

    fun dismiss() {
        handler.removeCallbacks(dismissRunnable)
        val view = container
        if (attached && view != null) {
            windowManager.removeView(view)
            attached = false
        }
    }

    private fun createView(): LinearLayout {
        val root = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.argb(244, 12, 17, 38))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.rgb(67, 205, 255))
            }
            contentDescription = "ZERO assistant edge panel"
        }
        val header = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(service).apply {
            text = "🤖 ZERO"
            textSize = 17f
            setTextColor(Color.rgb(101, 225, 255))
        }, LinearLayout.LayoutParams(0, dp(36), 1f))
        header.addView(TextView(service).apply {
            text = "×"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = "Dismiss ZERO panel"
            setOnClickListener { dismiss() }
        }, LinearLayout.LayoutParams(dp(36), dp(36)))
        root.addView(header)
        statusText = TextView(service).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            maxLines = 4
            setPadding(0, dp(8), 0, dp(10))
        }
        root.addView(statusText)
        root.addView(Button(service).apply {
            text = "Open ZERO"
            setOnClickListener {
                service.packageManager.getLaunchIntentForPackage(service.packageName)?.let { intent ->
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    service.startActivity(intent)
                }
                dismiss()
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))
        return root
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        dp(270),
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        x = dp(8)
    }

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density).toInt()
}
