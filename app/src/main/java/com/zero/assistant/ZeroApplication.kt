package com.zero.assistant

import android.app.Application
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ZeroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            runCatching {
                val log = File(filesDir, "diagnostics/crashes.log")
                log.parentFile?.mkdirs()
                log.appendText(
                    "\n${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date())} " +
                        "thread=${thread.name}\n${Log.getStackTraceString(failure)}\n",
                )
            }.onFailure { Log.e(TAG, "Could not persist crash diagnostics.", it) }
            if (previous != null) previous.uncaughtException(thread, failure)
            else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
        Thread({
            LegacyModelCleanup.remove(appContext = this)
        }, "ZERO-LegacyModelCleanup").apply {
            isDaemon = true
            start()
        }
    }

    companion object {
        private const val TAG = "ZERO-CrashLog"
    }

    private object LegacyModelCleanup {
        fun remove(appContext: Application) {
            val legacyModelDirectories = listOf(
                File(appContext.filesDir, "speech-models"),
                File(appContext.filesDir, "voice_models/kokoro"),
                File(appContext.cacheDir, "kokoro-int8-multi-lang-v1-0.tar.bz2.part"),
                File(appContext.getExternalFilesDir("voice_models"), "kokoro"),
            )
            legacyModelDirectories.forEach { path ->
                if (path.exists() && !path.deleteRecursively()) {
                    Log.w(TAG, "Could not remove legacy model data at ${path.absolutePath}.")
                }
            }
            listOf(appContext.filesDir, appContext.cacheDir, appContext.getExternalFilesDir(null))
                .filterNotNull()
                .forEach { root ->
                    root.listFiles()
                        .orEmpty()
                        .filter { it.name.startsWith("vosk-", ignoreCase = true) }
                        .forEach { path ->
                            if (!path.deleteRecursively()) Log.w(TAG, "Could not remove legacy Vosk data at ${path.absolutePath}.")
                        }
                }
        }

        private const val TAG = "ZERO-LegacyCleanup"
    }
}
