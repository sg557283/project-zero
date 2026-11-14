package com.zero.assistant.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.zero.assistant.core.CallControlAction

class CallAssistantManager(context: Context) {
    private val appContext = context.applicationContext

    fun execute(action: CallControlAction): String {
        val audio = appContext.getSystemService(AudioManager::class.java)
            ?: return "Call audio controls are unavailable."
        return when (action) {
            CallControlAction.Answer -> {
                if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
                    return "Grant the Answer phone calls permission in Android app settings to answer calls by voice."
                }
                val manager = appContext.getSystemService(TelecomManager::class.java)
                    ?: return "Android call controls are unavailable."
                try {
                    manager.acceptRingingCall()
                    "Answered the incoming call."
                } catch (failure: SecurityException) {
                    "Android denied call answering: ${failure.message ?: "permission unavailable"}."
                }
            }
            CallControlAction.End -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return "Ending calls is unavailable on this Android version."
                if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
                    return "Grant the Answer phone calls permission in Android app settings to end calls by voice."
                }
                val manager = appContext.getSystemService(TelecomManager::class.java)
                    ?: return "Android call controls are unavailable."
                val ended = try {
                    manager.endCall()
                } catch (failure: SecurityException) {
                    return "Android denied call ending: ${failure.message ?: "permission unavailable"}."
                }
                if (ended) "Call ended." else "Android could not end the active call."
            }
            CallControlAction.Mute, CallControlAction.Unmute -> {
                val mute = action == CallControlAction.Mute
                runCatching { audio.isMicrophoneMute = mute }
                    .fold(
                        { if (mute) "Call microphone muted." else "Call microphone unmuted." },
                        { "Android could not change call mute: ${it.message ?: "audio unavailable"}." },
                    )
            }
            CallControlAction.SpeakerOn, CallControlAction.SpeakerOff -> {
                val enabled = action == CallControlAction.SpeakerOn
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (enabled) {
                            val speaker = audio.availableCommunicationDevices.firstOrNull {
                                it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                            } ?: return "Android has no built-in speaker route."
                            check(audio.setCommunicationDevice(speaker)) { "Android rejected the speaker route." }
                        } else {
                            audio.clearCommunicationDevice()
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        audio.isSpeakerphoneOn = enabled
                    }
                }
                    .fold(
                        { if (enabled) "Call speaker enabled." else "Call speaker disabled." },
                        { "Android could not change call audio: ${it.message ?: "audio unavailable"}." },
                    )
            }
        }
    }

    fun hasPhoneStatePermission(): Boolean = hasPermission(Manifest.permission.READ_PHONE_STATE)

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
}
