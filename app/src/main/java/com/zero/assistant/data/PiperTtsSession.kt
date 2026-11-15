package com.zero.assistant.data

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

class PiperTtsSession(directory: File) : AutoCloseable {
    private val engine = OfflineTts(
        null,
        OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = directory.listFiles()
                        .orEmpty()
                        .firstOrNull { it.isFile && it.extension.equals("onnx", ignoreCase = true) }
                        ?.absolutePath
                        ?: error("Piper ONNX model file is missing."),
                    tokens = File(directory, "tokens.txt").absolutePath,
                    dataDir = File(directory, "espeak-ng-data").absolutePath,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
        ),
    )

    fun generate(text: String) = engine.generate(text, 0, 1.0f)

    override fun close() {
        engine.release()
    }
}
