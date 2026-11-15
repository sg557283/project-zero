package com.zero.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal class DottedBotFaceView(
    context: Context,
    private val onExpandedChanged: (Boolean) -> Unit,
    private val onBubbleSelected: (BotBubbleAction) -> Unit,
    private val onTap: () -> Unit,
    private val onDragBy: (Float) -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val camera = Camera()
    private val cameraMatrix = Matrix()
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 10f * density
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
    }
    private val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var actionList = BotBubbleConfigManager.actions(context)
    private var expanded = false
    private var selectedActionIndex = -1
    private var trackingTouch = false
    private var movedTouch = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawY = 0f
    private var entrance = 1f
    private var exit = 0f
    private var winkStartedAt = 0L
    private var expression = BotFaceMode.Idle
    private var animation: ValueAnimator? = null
    private val longPressRunnable = Runnable {
        if (trackingTouch && !movedTouch && !expanded) {
            setExpanded(true)
            onExpandedChanged(true)
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun setExpanded(value: Boolean) {
        expanded = value
        selectedActionIndex = -1
        invalidate()
    }

    fun setBubbleActions(actions: List<BotBubbleAction>) {
        actionList = actions
        selectedActionIndex = -1
        invalidate()
    }

    fun setExpression(mode: BotFaceMode) {
        expression = mode
        if (mode == BotFaceMode.Success) playWinkAnimation()
        invalidate()
    }

    fun playWinkAnimation() {
        winkStartedAt = SystemClock.elapsedRealtime()
        invalidate()
    }

    fun animateEntrance() {
        animation?.cancel()
        entrance = 0.72f
        exit = 0f
        animation = ValueAnimator.ofFloat(0.72f, 1f).apply {
            duration = 520L
            interpolator = OvershootInterpolator(1.25f)
            addUpdateListener {
                entrance = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun animateExit(onEnd: () -> Unit) {
        animation?.cancel()
        animation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                exit = it.animatedValue as Float
                invalidate()
            }
            doOnEnd(onEnd)
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.elapsedRealtime()
        val pulse = 1f + sin(now / 1_000f * 1.7f) * 0.025f
        val alpha = ((1f - exit).coerceIn(0f, 1f) * 255).toInt()
        val saveCount = canvas.save()
        canvas.scale(entrance * pulse, entrance * pulse, width / 2f, height / 2f)
        if (expanded) drawActionBubbles(canvas, now, alpha)
        drawFace(canvas, now, alpha)
        canvas.restoreToCount(saveCount)
        if (isAttachedToWindow) postInvalidateDelayed(38L)
    }

    private fun drawFace(canvas: Canvas, now: Long, alpha: Int) {
        val centerX = if (expanded) width - dp(45f) else width / 2f
        val centerY = height / 2f
        val radius = dp(33f)
        val phase = now / 1_000f
        val tiltY = sin(phase * 0.47f) * 5.5f
        val tiltX = cos(phase * 0.39f) * 3.2f
        val orbitColor = if (isNightMode()) Color.rgb(97, 222, 238) else Color.BLACK
        val featureColor = if (isNightMode()) Color.WHITE else Color.BLACK

        drawOrbit(canvas, centerX, centerY, radius, phase, orbitColor, alpha)
        canvas.save()
        camera.save()
        camera.rotateY(tiltY)
        camera.rotateX(tiltX)
        camera.getMatrix(cameraMatrix)
        camera.restore()
        cameraMatrix.preTranslate(-centerX, -centerY)
        cameraMatrix.postTranslate(centerX, centerY)
        canvas.concat(cameraMatrix)

        val breathing = 1f + sin(phase * 1.5f) * 0.025f
        val parallaxX = sin(Math.toRadians(tiltY.toDouble())).toFloat() * dp(2f)
        val parallaxY = sin(Math.toRadians(tiltX.toDouble())).toFloat() * dp(2f)
        canvas.save()
        canvas.scale(breathing, breathing, centerX, centerY)
        drawFaceFeatures(canvas, centerX + parallaxX, centerY + parallaxY, featureColor, alpha, now)
        canvas.restore()
        canvas.restore()
    }

    private fun drawOrbit(canvas: Canvas, cx: Float, cy: Float, radius: Float, phase: Float, color: Int, alpha: Int) {
        for (index in 0 until 36) {
            val angle = (index * (Math.PI * 2.0 / 36.0) + phase * 0.24).toFloat()
            val x = cx + cos(angle) * radius
            val y = cy + sin(angle) * radius * 0.79f
            val depth = 0.55f + ((sin(angle) + 1f) * 0.22f)
            drawDot(canvas, x, y, dp(1.55f) * depth, color, (alpha * depth).toInt())
        }
    }

    private fun drawFaceFeatures(canvas: Canvas, cx: Float, cy: Float, color: Int, alpha: Int, now: Long) {
        val elapsed = now - winkStartedAt
        if (winkStartedAt > 0L && elapsed > 900L) winkStartedAt = 0L
        val winking = winkStartedAt > 0L && elapsed in 0..900L
        val blinkCycle = now % 3_600L
        val blink = if (!winking && blinkCycle < 140L) {
            0.12f + (blinkCycle / 140f) * 0.88f
        } else 1f
        val eyeY = cy - dp(6f)
        val eyeOffset = dp(14f)
        if (winking) {
            drawWink(canvas, cx - eyeOffset, eyeY, color, alpha)
            drawEye(canvas, cx + eyeOffset, eyeY, color, alpha, 1f)
        } else {
            drawEye(canvas, cx - eyeOffset, eyeY, color, alpha, blink)
            drawEye(canvas, cx + eyeOffset, eyeY, color, alpha, blink)
        }
        val dot = dp(1.55f)
        for (row in 0..2) {
            drawDot(canvas, cx, cy + dp(1.5f + row * 4f), dot, color, (alpha * 0.95f).toInt())
        }
        drawMouth(canvas, cx, cy + dp(15f), color, alpha, now, winking)
    }

    private fun drawEye(canvas: Canvas, cx: Float, cy: Float, color: Int, alpha: Int, blink: Float) {
        val spacing = dp(4.2f)
        val radius = dp(1.8f)
        for (row in -1..1) {
            for (column in -1..1) {
                val x = cx + column * spacing
                val y = cy + row * spacing * blink
                drawDot(canvas, x, y, radius, color, alpha)
            }
        }
    }

    private fun drawWink(canvas: Canvas, cx: Float, cy: Float, color: Int, alpha: Int) {
        for (index in -2..2) {
            val x = cx + index * dp(3f)
            val y = cy + abs(index) * dp(1.2f)
            drawDot(canvas, x, y, dp(1.6f), color, alpha)
        }
    }

    private fun drawMouth(canvas: Canvas, cx: Float, cy: Float, color: Int, alpha: Int, now: Long, smile: Boolean) {
        val speaking = expression == BotFaceMode.Listening || expression == BotFaceMode.Speaking ||
            expression == BotFaceMode.ActingOnScreen
        for (index in -3..3) {
            val x = cx + index * dp(4f)
            val wave = if (speaking) sin(now / 125f + index * 0.8f) * dp(3.2f) else 0f
            val curve = if (smile) abs(index) * dp(0.62f) else abs(index) * dp(0.48f)
            drawDot(canvas, x, cy + curve + wave, dp(1.55f), color, alpha)
        }
    }

    private fun drawActionBubbles(canvas: Canvas, now: Long, alpha: Int) {
        if (actionList.isEmpty()) return
        val centerX = width - dp(45f)
        val centerY = height / 2f
        val orbitRadius = dp(104f)
        val bubbleRadius = dp(22f)
        val textColor = if (isNightMode()) Color.WHITE else Color.BLACK
        actionList.forEachIndexed { index, action ->
            val angle = if (actionList.size == 1) 0f else Math.toRadians(
                -90.0 + index * 180.0 / (actionList.size - 1),
            ).toFloat()
            val x = centerX - cos(angle) * orbitRadius
            val y = centerY + sin(angle) * orbitRadius
            val selected = index == selectedActionIndex
            val scale = if (selected) 1.25f else 1f + sin(now / 400f + index) * 0.025f
            val radius = bubbleRadius * scale
            val accent = if (isNightMode()) Color.rgb(49, 176, 195) else Color.rgb(223, 229, 232)
            bubblePaint.color = if (selected) {
                if (isNightMode()) Color.rgb(39, 120, 139) else Color.rgb(190, 218, 223)
            } else accent
            bubblePaint.alpha = alpha
            canvas.drawCircle(x, y, radius, bubblePaint)
            bubbleTextPaint.color = textColor
            bubbleTextPaint.alpha = alpha
            val metrics = bubbleTextPaint.fontMetrics
            canvas.drawText(action.label, x, y - (metrics.ascent + metrics.descent) / 2f, bubbleTextPaint)
        }
    }

    private fun drawDot(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int, alpha: Int) {
        glowPaint.color = color
        glowPaint.alpha = (alpha * if (isNightMode()) 0.18f else 0.07f).toInt().coerceIn(0, 255)
        canvas.drawCircle(x, y, radius * 2.5f, glowPaint)
        facePaint.color = color
        facePaint.alpha = alpha.coerceIn(0, 255)
        canvas.drawCircle(x, y, radius, facePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val local = toLocal(event.rawX, event.rawY)
                if (!expanded && !insideFace(local.first, local.second)) return false
                trackingTouch = true
                movedTouch = false
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawY = event.rawY
                longPressHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!trackingTouch) return false
                val totalDistance = max(abs(event.rawX - downRawX), abs(event.rawY - downRawY))
                if (!expanded && totalDistance > touchSlop) {
                    movedTouch = true
                    longPressHandler.removeCallbacks(longPressRunnable)
                }
                if (expanded) {
                    updateSelectedBubble(hitTestBubble(toLocal(event.rawX, event.rawY)))
                } else if (movedTouch) {
                    onDragBy(event.rawY - lastRawY)
                }
                lastRawY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!trackingTouch) return false
                longPressHandler.removeCallbacks(longPressRunnable)
                val wasExpanded = expanded
                val selected = if (wasExpanded && event.actionMasked == MotionEvent.ACTION_UP) {
                    hitTestBubble(toLocal(event.rawX, event.rawY))
                } else -1
                trackingTouch = false
                if (wasExpanded) {
                    setExpanded(false)
                    onExpandedChanged(false)
                    if (selected >= 0) onBubbleSelected(actionList[selected])
                } else if (!movedTouch && event.actionMasked == MotionEvent.ACTION_UP) {
                    onTap()
                }
                return true
            }
        }
        return false
    }

    private fun updateSelectedBubble(index: Int) {
        if (index == selectedActionIndex) return
        selectedActionIndex = index
        if (index >= 0) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        invalidate()
    }

    private fun hitTestBubble(point: Pair<Float, Float>): Int {
        val centerX = width - dp(45f)
        val centerY = height / 2f
        val radius = dp(104f)
        val hitRadius = dp(30f)
        return actionList.indices.firstOrNull { index ->
            val angle = if (actionList.size == 1) 0f else Math.toRadians(
                -90.0 + index * 180.0 / (actionList.size - 1),
            ).toFloat()
            val x = centerX - cos(angle) * radius
            val y = centerY + sin(angle) * radius
            val dx = point.first - x
            val dy = point.second - y
            dx * dx + dy * dy <= hitRadius * hitRadius
        } ?: -1
    }

    private fun insideFace(x: Float, y: Float): Boolean {
        val cx = width / 2f
        val cy = height / 2f
        val r = dp(42f)
        val dx = x - cx
        val dy = y - cy
        return dx * dx + dy * dy <= r * r
    }

    private fun toLocal(rawX: Float, rawY: Float): Pair<Float, Float> {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return rawX - location[0] to rawY - location[1]
    }

    private fun isNightMode(): Boolean =
        resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun dp(value: Float): Float = value * density

    override fun onDetachedFromWindow() {
        longPressHandler.removeCallbacksAndMessages(null)
        animation?.cancel()
        animation = null
        super.onDetachedFromWindow()
    }

    private fun ValueAnimator.doOnEnd(block: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = block()
        })
    }
}
