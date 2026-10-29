package com.zero.assistant

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.SystemClock


internal class OrganicOrbView(context: Context) : GLSurfaceView(context) {
    private val organicRenderer = OrganicOrbRenderer(context)
    private var renderResume = false

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(organicRenderer)
        setRenderMode(RENDERMODE_CONTINUOUSLY)
    }

    companion object {
        fun supportsOpenGl(context: Context): Boolean =
            context.packageManager?.systemAvailableFeatures?.any {
                it.name == android.content.pm.PackageManager.FEATURE_OPENGLES_EXTENSION_PACK ||
                    it.name == "android.hardware.opengles.version"
            } == true || android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VERSION) != null
    }

    fun setOrbState(activity: OrbActivity, reducedMotion: Boolean) {
        val strength = when (activity) {
            OrbActivity.Idle -> 0.8f
            OrbActivity.Listening -> 1.15f
            OrbActivity.Processing -> 1.35f
            OrbActivity.Speaking -> 1.05f
        }
        organicRenderer.setState(activity.ordinal, strength, reducedMotion, SystemClock.elapsedRealtimeNanos())
        if (reducedMotion) requestRender() else renderResume = true
        renderMode = if (reducedMotion) RENDERMODE_WHEN_DIRTY else RENDERMODE_CONTINUOUSLY
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (renderResume) onResume()
    }

    fun safePause() {
        renderResume = true
        onPause()
    }

    fun safeResume() {
        onResume()
    }

    override fun onDetachedFromWindow() {
        renderResume = false
        super.onDetachedFromWindow()
    }
}
