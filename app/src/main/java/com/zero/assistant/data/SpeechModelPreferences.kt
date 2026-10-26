package com.zero.assistant.data

import android.content.Context
import android.annotation.SuppressLint
import com.zero.assistant.core.SpeechModelMetadata

@SuppressLint("UseKtx")
class SpeechModelPreferences(context: Context) : SpeechModelMetadata {
    private val preferences = context.getSharedPreferences("speech_models", Context.MODE_PRIVATE)

    override fun installedIds(): Set<String> = preferences.getStringSet(KEY_INSTALLED, emptySet()).orEmpty().toSet()

    override fun saveInstalledIds(ids: Set<String>) {
        check(preferences.edit().putStringSet(KEY_INSTALLED, ids.toSet()).commit()) {
            "Installed speech model metadata could not be saved"
        }
    }

    override fun activeModelId(): String? = preferences.getString(KEY_ACTIVE, null)

    override fun saveActiveModelId(id: String?) {
        check(preferences.edit().putString(KEY_ACTIVE, id).commit()) {
            "Active speech model could not be saved"
        }
    }

    private companion object {
        const val KEY_INSTALLED = "installed_ids"
        const val KEY_ACTIVE = "active_id"
    }
}