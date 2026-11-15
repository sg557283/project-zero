package com.zero.assistant

import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import com.zero.assistant.data.ActionExecutor
import com.zero.assistant.data.WakeWordSettings

class FloatingBotOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var faceView: DottedBotFaceView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var expanded = false
    private var isDismissing = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        runningService = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> if (faceView == null || isDismissing) showBot() else dismissBot()
            ACTION_SHOW -> showBot()
            ACTION_HIDE -> dismissBot()
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun showBot() {
        if (faceView != null || !Settings.canDrawOverlays(this)) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Allow ZERO to display over other apps, then press both volume keys again.", Toast.LENGTH_LONG).show()
                stopSelf()
            }
            return
        }
        val manager = getSystemService(WindowManager::class.java)
        if (manager == null) {
            Log.e(TAG, "WindowManager is unavailable; floating bot could not be shown.")
            stopSelf()
            return
        }
        windowManager = manager
        val face = DottedBotFaceView(
            context = this,
            onExpandedChanged = ::resizeForExpandedState,
            onBubbleSelected = ::executeBubble,
            onTap = { executeBubble(BotBubbleAction.Voice) },
            onDragBy = ::dragVertically,
        )
        val params = createLayoutParams()
        try {
            manager.addView(face, params)
            faceView = face
            layoutParams = params
            isDismissing = false
            setVisible(this, true)
            face.animateEntrance()
        } catch (failure: SecurityException) {
            Log.e(TAG, "Overlay permission was revoked before the floating bot was attached.", failure)
            Toast.makeText(this, "ZERO needs display-over-other-apps permission.", Toast.LENGTH_LONG).show()
            stopSelf()
        } catch (failure: WindowManager.BadTokenException) {
            Log.e(TAG, "Android rejected the floating bot window.", failure)
            stopSelf()
        }
    }

    private fun dismissBot() {
        val face = faceView ?: run {
            setVisible(this, false)
            stopSelf()
            return
        }
        if (isDismissing) return
        isDismissing = true
        face.animateExit {
            removeFaceView()
            stopSelf()
        }
    }

    private fun removeFaceView() {
        val face = faceView ?: return
        try {
            windowManager?.removeView(face)
        } catch (failure: IllegalArgumentException) {
            Log.w(TAG, "Floating bot view was already detached.", failure)
        }
        faceView = null
        layoutParams = null
        windowManager = null
        expanded = false
        isDismissing = false
        setVisible(this, false)
    }

    private fun resizeForExpandedState(showBubbles: Boolean) {
        val face = faceView ?: return
        val manager = windowManager ?: return
        val params = layoutParams ?: return
        expanded = showBubbles
        val centerY = params.y + params.height / 2
        params.width = if (showBubbles) {
            minOf(dp(EXPANDED_WIDTH_DP), (resources.displayMetrics.widthPixels - dp(8)).coerceAtLeast(dp(220)))
        } else {
            dp(COLLAPSED_SIZE_DP)
        }
        params.height = dp(if (showBubbles) EXPANDED_HEIGHT_DP else COLLAPSED_SIZE_DP)
        params.y = (centerY - params.height / 2)
            .coerceIn(0, (resources.displayMetrics.heightPixels - params.height).coerceAtLeast(0))
        try {
            manager.updateViewLayout(face, params)
        } catch (failure: IllegalArgumentException) {
            Log.w(TAG, "Floating bot could not be resized.", failure)
        } catch (failure: WindowManager.BadTokenException) {
            Log.e(TAG, "Floating bot window is no longer attached.", failure)
        }
    }

    private fun dragVertically(deltaPixels: Float) {
        val face = faceView ?: return
        val manager = windowManager ?: return
        val params = layoutParams ?: return
        val maxY = (resources.displayMetrics.heightPixels - face.height).coerceAtLeast(0)
        params.y = (params.y + deltaPixels.toInt()).coerceIn(0, maxY)
        params.x = 0
        try {
            manager.updateViewLayout(face, params)
        } catch (failure: IllegalArgumentException) {
            Log.w(TAG, "Floating bot position could not be updated.", failure)
        }
    }

    private fun executeBubble(action: BotBubbleAction) {
        val face = faceView ?: return
        resizeForExpandedState(false)
        face.setExpression(BotFaceMode.Success)
        try {
            val result = ActionExecutor.executeBotBubble(this, action)
            if (isFailure(result)) face.setExpression(BotFaceMode.Idle)
            else face.playWinkAnimation()
        } catch (failure: SecurityException) {
            Log.e(TAG, "Floating bot action was blocked by Android.", failure)
            face.setExpression(BotFaceMode.Idle)
            WakeWordSettings.updateAssistantPanel(this, "Android blocked that action.", 5_000L)
        } catch (failure: ActivityNotFoundException) {
            Log.e(TAG, "No activity can handle the selected floating bot action.", failure)
            face.setExpression(BotFaceMode.Idle)
            WakeWordSettings.updateAssistantPanel(this, "No app is available for that action.", 5_000L)
        } finally {
            face.postDelayed({ face.setExpression(BotFaceMode.Idle) }, WINK_DURATION_MS)
        }
    }

    private fun setExpression(mode: BotFaceMode) {
        faceView?.setExpression(mode)
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val screenHeight = resources.displayMetrics.heightPixels
        val size = dp(COLLAPSED_SIZE_DP)
        return WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.RIGHT
            x = 0
            y = (screenHeight / 2 - size / 2).coerceAtLeast(0)
            alpha = 1f
        }
    }

    override fun onDestroy() {
        faceView?.let { face ->
            try {
                windowManager?.removeView(face)
            } catch (failure: IllegalArgumentException) {
                Log.w(TAG, "Floating bot view was already detached during service shutdown.", failure)
            }
        }
        faceView = null
        windowManager = null
        layoutParams = null
        setVisible(this, false)
        if (runningService === this) runningService = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun isFailure(message: String): Boolean =
        message.startsWith("I couldn't", ignoreCase = true) ||
            message.contains("unavailable", ignoreCase = true) ||
            message.contains("could not", ignoreCase = true) ||
            message.contains("couldn't", ignoreCase = true)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "ZERO-FloatingBot"
        private const val PREFERENCES = "zero_bot_overlay"
        private const val KEY_VISIBLE = "visible"
        private const val ACTION_TOGGLE = "com.zero.assistant.action.TOGGLE_FLOATING_BOT"
        private const val ACTION_SHOW = "com.zero.assistant.action.SHOW_FLOATING_BOT"
        private const val ACTION_HIDE = "com.zero.assistant.action.HIDE_FLOATING_BOT"
        private const val COLLAPSED_SIZE_DP = 88
        private const val EXPANDED_WIDTH_DP = 330
        private const val EXPANDED_HEIGHT_DP = 300
        private const val WINK_DURATION_MS = 900L
        @Volatile private var runningService: FloatingBotOverlayService? = null

        fun hasOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun toggle(context: Context): Boolean {
            if (!hasOverlayPermission(context)) {
                requestOverlayPermission(context)
                return false
            }
            return start(context, ACTION_TOGGLE)
        }

        fun show(context: Context): Boolean =
            if (hasOverlayPermission(context)) start(context, ACTION_SHOW) else {
                requestOverlayPermission(context)
                false
            }

        fun hide(context: Context) {
            start(context, ACTION_HIDE)
        }

        fun updateExpression(context: Context, mode: String?) {
            if (mode == null || !isVisible(context)) return
            val service = runningService ?: return
            val expression = BotFaceMode.entries.firstOrNull { it.name == mode } ?: return
            service.mainHandler.post { service.setExpression(expression) }
        }

        fun refreshBubbles(context: Context) {
            if (!isVisible(context)) return
            val service = runningService ?: return
            val actions = BotBubbleConfigManager.actions(context)
            service.mainHandler.post { service.faceView?.setBubbleActions(actions) }
        }

        private fun start(context: Context, action: String): Boolean {
            return try {
                context.startService(Intent(context, FloatingBotOverlayService::class.java).setAction(action)) != null
            } catch (failure: IllegalStateException) {
                Log.e(TAG, "Android would not start the floating bot service.", failure)
                false
            } catch (failure: SecurityException) {
                Log.e(TAG, "Android denied the floating bot service start.", failure)
                false
            }
        }

        fun requestOverlayPermission(context: Context) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(permissionIntent)
            } catch (failure: ActivityNotFoundException) {
                Log.e(TAG, "Android does not provide overlay permission settings.", failure)
                Toast.makeText(context, "Open Android Settings and allow ZERO to display over other apps.", Toast.LENGTH_LONG).show()
            } catch (failure: SecurityException) {
                Log.e(TAG, "Overlay permission settings could not be opened.", failure)
            }
        }

        private fun setVisible(context: Context, visible: Boolean) {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_VISIBLE, visible)
                .apply()
        }

        private fun isVisible(context: Context): Boolean =
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(KEY_VISIBLE, false)
    }
}
