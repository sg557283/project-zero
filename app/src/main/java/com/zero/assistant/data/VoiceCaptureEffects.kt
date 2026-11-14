package com.zero.assistant.data

import android.media.audiofx.AutomaticGainControl
import android.util.Log

internal class VoiceCaptureEffects {
    private var automaticGainControl: AutomaticGainControl? = null

    fun attach(audioSessionId: Int, enabled: Boolean): Boolean {
        release()
        if (!enabled) return false
        if (!AutomaticGainControl.isAvailable()) {
            Log.i(TAG, "Automatic microphone gain control is not available on this device.")
            return false
        }
        val effect = try {
            AutomaticGainControl.create(audioSessionId)
        } catch (failure: IllegalArgumentException) {
            Log.w(TAG, "Automatic microphone gain control rejected the recording session.", failure)
            return false
        } catch (failure: IllegalStateException) {
            Log.w(TAG, "Automatic microphone gain control could not be initialized.", failure)
            return false
        } catch (failure: SecurityException) {
            Log.w(TAG, "Android denied automatic microphone gain control.", failure)
            return false
        } catch (failure: UnsupportedOperationException) {
            Log.i(TAG, "Automatic microphone gain control is unsupported on this device.", failure)
            return false
        }
        if (effect == null) {
            Log.w(TAG, "Automatic microphone gain control could not be attached to the recording session.")
            return false
        }
        try {
            effect.enabled = true
        } catch (failure: IllegalStateException) {
            Log.w(TAG, "Automatic microphone gain control could not be enabled.", failure)
            effect.release()
            return false
        } catch (failure: UnsupportedOperationException) {
            Log.i(TAG, "Automatic microphone gain control could not be enabled on this device.", failure)
            effect.release()
            return false
        }
        if (!effect.enabled) {
            Log.w(TAG, "Automatic microphone gain control remained disabled on this device.")
            effect.release()
            return false
        }
        automaticGainControl = effect
        return true
    }

    fun release() {
        automaticGainControl?.release()
        automaticGainControl = null
    }

    private companion object {
        const val TAG = "ZERO-Voice"
    }
}
