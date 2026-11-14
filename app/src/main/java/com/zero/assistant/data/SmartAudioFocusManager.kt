package com.zero.assistant.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

class SmartAudioFocusManager(
    context: Context,
    private val onCaptureAvailabilityChanged: (Boolean) -> Unit,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val telephonyManager = appContext.getSystemService(TelephonyManager::class.java)
    private var phoneIdle = true
    private var externalRecording = false
    private var ownAudioSessionId = 0
    private var focusRequest: AudioFocusRequest? = null
    private var closed = false

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            externalRecording = configs.orEmpty().any { configuration ->
                configuration.clientAudioSessionId != ownAudioSessionId
            }
            publishAvailability()
        }
    }

    private val phoneCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                phoneIdle = state == TelephonyManager.CALL_STATE_IDLE
                publishAvailability()
            }
        }
    } else null

    @Suppress("DEPRECATION")
    private val legacyPhoneListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            phoneIdle = state == TelephonyManager.CALL_STATE_IDLE
            publishAvailability()
        }
    }

    fun start() {
        if (closed) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            audioManager?.registerAudioRecordingCallback(recordingCallback, handler)
        }
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            phoneIdle = runCatching { telephonyManager?.callState == TelephonyManager.CALL_STATE_IDLE }
                .getOrElse { false }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                phoneCallback?.let { telephonyManager?.registerTelephonyCallback(appContext.mainExecutor, it) }
            } else {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(legacyPhoneListener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        }
        publishAvailability()
    }

    fun requestCommandFocus(): Boolean {
        val manager = audioManager ?: return false
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener({})
                .build()
            focusRequest = request
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }

        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun setOwnAudioSessionId(sessionId: Int) {
        ownAudioSessionId = sessionId
    }

    fun releaseCommandFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(manager::abandonAudioFocusRequest)
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(null)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            audioManager?.unregisterAudioRecordingCallback(recordingCallback)
        }
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                phoneCallback?.let { telephonyManager?.unregisterTelephonyCallback(it) }
            } else {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(legacyPhoneListener, PhoneStateListener.LISTEN_NONE)
            }
        }
        releaseCommandFocus()
    }

    private fun publishAvailability() {
        if (!closed) onCaptureAvailabilityChanged(phoneIdle && !externalRecording)
    }
}
