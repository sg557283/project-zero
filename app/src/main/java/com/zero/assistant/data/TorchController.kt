package com.zero.assistant.data

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

object TorchController {
    private val callbacks = mutableListOf<(Boolean) -> Unit>()
    private val states = mutableMapOf<String, Boolean>()
    private val handler = Handler(Looper.getMainLooper())
    private var callbackRegisteredContext: Context? = null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            states[cameraId] = enabled
            handler.post { callbacks.forEach { it(enabled) } }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            states.remove(cameraId)
        }
    }

    fun cameraId(context: Context): String? = runCatching {
        val camera = context.getSystemService(CameraManager::class.java) ?: return null
        camera.cameraIdList.firstOrNull { id ->
            camera.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    fun state(context: Context): Boolean? {
        ensureCallback(context)
        val id = cameraId(context) ?: return null
        return states[id]
    }

    fun toggle(context: Context): Result<Boolean> = runCatching {
        val camera = context.getSystemService(CameraManager::class.java) ?: throw IllegalStateException("Camera service unavailable")
        val id = cameraId(context) ?: throw IllegalStateException("This device has no flashlight")
        val target = com.zero.assistant.core.UniversalCommandActions.toggleFlashlight(state(context))
            ?: throw IllegalStateException("Flashlight state unavailable")
        camera.setTorchMode(id, target)
        states[id] = target
        handler.post { callbacks.forEach { it(target) } }
        target
    }

    fun set(context: Context, enabled: Boolean): Result<Boolean> = runCatching {
        val camera = context.getSystemService(CameraManager::class.java) ?: throw IllegalStateException("Camera service unavailable")
        val id = cameraId(context) ?: throw IllegalStateException("This device has no flashlight")
        camera.setTorchMode(id, enabled)
        states[id] = enabled
        handler.post { callbacks.forEach { it(enabled) } }
        enabled
    }

    private fun ensureCallback(context: Context) {
        if (callbackRegisteredContext != null) return
        val camera = context.getSystemService(CameraManager::class.java) ?: return
        callbackRegisteredContext = context.applicationContext
        camera.registerTorchCallback(torchCallback, handler)
    }

    fun observe(callback: (Boolean) -> Unit): () -> Unit {
        callbacks.add(callback)
        return { callbacks.remove(callback) }
    }
}
