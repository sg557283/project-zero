package com.zero.assistant

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView

internal class OrganicOrbView(context: Context) : GLSurfaceView(context) {
    private val organicRenderer = OrganicOrbRenderer()
    private var resumed = true

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(false)
        preserveEGLContextOnPause = true
        setRenderer(organicRenderer)
        setRenderMode(RENDERMODE_CONTINUOUSLY)
    }

    fun setOrbState(activity: OrbActivity, audioAmplitude: Float, reducedMotion: Boolean) {
        organicRenderer.setState(activity, audioAmplitude.coerceIn(0f, 1f), reducedMotion)
        renderMode = if (reducedMotion) RENDERMODE_WHEN_DIRTY else RENDERMODE_CONTINUOUSLY
        requestRender()
    }

    fun safePause() {
        if (!resumed) return
        resumed = false
        onPause()
    }

    fun safeResume() {
        if (resumed) return
        resumed = true
        onResume()
    }
}
