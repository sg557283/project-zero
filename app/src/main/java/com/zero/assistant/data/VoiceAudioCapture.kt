package com.zero.assistant.data

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.tanh

internal object VoiceAudioProcessing {
    fun applyPreGain(sample: Short): Float = tanh(sample.toDouble() / PCM_SCALE * SOFTWARE_PRE_GAIN).toFloat()

    private const val SOFTWARE_PRE_GAIN = 1.6
    private const val PCM_SCALE = 32_768.0
}

internal class VoiceAudioCapture(
    context: Context,
    private val sensitivity: WakeWordSettings.Sensitivity,
    private val onKeyword: (String) -> Unit,
    private val onFailure: (Exception) -> Unit,
) {
    val audioSessionId: Int
        get() = audioRecord.audioSessionId

    val initializationWarnings: List<String>
        get() = warnings.toList()

    val hasKeywordSpotter: Boolean
        get() = keywordSpotter != null

    private val running = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private val warnings = mutableListOf<String>()
    private var keywordSpotter = createKeywordSpotter(context, sensitivity)
    private var keywordStream: OnlineStream? = createKeywordStream()
    private val audioRecord: AudioRecord
    private var worker: Thread? = null

    init {
        val minimumBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minimumBufferBytes <= 0) {
            releaseModels()
            throw IOException("Android could not determine a microphone buffer size.")
        }
        audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimumBufferBytes, FRAME_SAMPLES * BYTES_PER_SAMPLE * AUDIO_BUFFER_MULTIPLIER),
            )
        } catch (failure: IllegalArgumentException) {
            releaseModels()
            throw IOException("Android rejected the voice-recognition audio format.", failure)
        } catch (failure: IllegalStateException) {
            releaseModels()
            throw IOException("Android could not create the voice-recognition microphone.", failure)
        } catch (failure: SecurityException) {
            releaseModels()
            throw IOException("Android denied access to the voice-recognition microphone.", failure)
        }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            releaseModels()
            throw IOException("Android could not initialize the voice-recognition microphone.")
        }
    }

    fun start() {
        check(running.compareAndSet(false, true)) { "Microphone capture is already running." }
        try {
            audioRecord.startRecording()
            if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IOException("Android did not start microphone recording.")
            }
            worker = Thread(::captureLoop, "ZERO-WakeAudioCapture").apply {
                isDaemon = true
                start()
            }
        } catch (failure: Exception) {
            running.set(false)
            releaseResources()
            throw failure
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) {
            if (worker == null) releaseResources()
            return
        }
        try {
            if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) audioRecord.stop()
        } catch (failure: IllegalStateException) {
            Log.w(TAG, "Microphone stopped while audio capture was shutting down.", failure)
        }
        val currentWorker = worker
        if (currentWorker == null) releaseResources()
        else if (currentWorker !== Thread.currentThread()) {
            try {
                currentWorker.join(STOP_JOIN_TIMEOUT_MS)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            if (!currentWorker.isAlive) releaseResources()
        }
    }

    private fun captureLoop() {
        val samples = ShortArray(FRAME_SAMPLES)
        val boostedSamples = FloatArray(FRAME_SAMPLES)
        try {
            while (running.get()) {
                var captured = 0
                while (captured < samples.size && running.get()) {
                    val read = audioRecord.read(
                        samples,
                        captured,
                        samples.size - captured,
                        AudioRecord.READ_BLOCKING,
                    )
                    if (read <= 0) {
                        if (running.get()) throw IOException("Microphone audio ended unexpectedly (read=$read).")
                        break
                    }
                    captured += read
                }
                if (!running.get() || captured != samples.size) continue
                for (index in samples.indices) {
                    boostedSamples[index] = VoiceAudioProcessing.applyPreGain(samples[index])
                }
                val spotter = keywordSpotter ?: continue
                val stream = keywordStream ?: continue
                stream.acceptWaveform(boostedSamples, SAMPLE_RATE_HZ)
                while (spotter.isReady(stream)) {
                    spotter.decode(stream)
                    val keyword = spotter.getResult(stream).keyword
                    if (keyword.isNotBlank()) {
                        onKeyword(keyword)
                        spotter.reset(stream)
                    }
                }
            }
        } catch (failure: Exception) {
            if (running.getAndSet(false)) onFailure(failure)
        } catch (failure: LinkageError) {
            if (running.getAndSet(false)) {
                onFailure(IllegalStateException("The offline wake runtime could not process microphone audio.", failure))
            }
        } finally {
            releaseResources()
        }
    }

    private fun createKeywordSpotter(context: Context, sensitivity: WakeWordSettings.Sensitivity): KeywordSpotter? =
        try {
            KeywordSpotter(
                context.assets,
                KeywordSpotterConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE_HZ, featureDim = KWS_FEATURE_DIM),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = KWS_ENCODER_ASSET,
                            decoder = KWS_DECODER_ASSET,
                            joiner = KWS_JOINER_ASSET,
                        ),
                        tokens = KWS_TOKENS_ASSET,
                        numThreads = 1,
                        provider = CPU_PROVIDER,
                        modelingUnit = BPE_MODELING_UNIT,
                        bpeVocab = KWS_BPE_ASSET,
                    ),
                    maxActivePaths = KWS_MAX_ACTIVE_PATHS,
                    keywordsFile = KWS_KEYWORDS_ASSET,
                    keywordsScore = sensitivity.keywordBoost,
                    keywordsThreshold = sensitivity.keywordThreshold,
                    numTrailingBlanks = KWS_TRAILING_BLANKS,
                ),
            )
        } catch (failure: Exception) {
            warnings += "Sherpa keyword spotting unavailable: ${failure.message ?: "wake model could not be loaded"}"
            Log.e(TAG, "Sherpa keyword spotter could not be initialized.", failure)
            null
        } catch (failure: LinkageError) {
            warnings += "Sherpa keyword spotting unavailable: offline runtime could not be loaded"
            Log.e(TAG, "Sherpa keyword spotter native runtime could not be loaded.", failure)
            null
        }

    private fun createKeywordStream(): OnlineStream? {
        val spotter = keywordSpotter ?: return null
        return try {
            spotter.createStream()
        } catch (failure: Exception) {
            warnings += "Sherpa keyword stream unavailable: ${failure.message ?: "wake phrases could not be initialized"}"
            Log.e(TAG, "Sherpa keyword stream could not be initialized.", failure)
            spotter.release()
            null
        } catch (failure: LinkageError) {
            warnings += "Sherpa keyword stream unavailable: offline runtime could not be loaded"
            Log.e(TAG, "Sherpa keyword stream native runtime could not be loaded.", failure)
            spotter.release()
            null
        }
    }

    private fun releaseModels() {
        keywordStream?.release()
        keywordStream = null
        keywordSpotter?.release()
        keywordSpotter = null
    }

    private fun releaseResources() {
        if (!released.compareAndSet(false, true)) return
        releaseModels()
        audioRecord.release()
    }

    private companion object {
        const val TAG = "ZERO-Wake"
        const val SAMPLE_RATE_HZ = 16_000
        const val FRAME_SAMPLES = 512
        const val BYTES_PER_SAMPLE = 2
        const val AUDIO_BUFFER_MULTIPLIER = 4
        const val STOP_JOIN_TIMEOUT_MS = 1_000L
        const val KWS_FEATURE_DIM = 80
        const val KWS_MAX_ACTIVE_PATHS = 4
        const val KWS_TRAILING_BLANKS = 1
        const val CPU_PROVIDER = "cpu"
        const val BPE_MODELING_UNIT = "bpe"
        const val KWS_ENCODER_ASSET = "voice-models/sherpa-kws/encoder.int8.onnx"
        const val KWS_DECODER_ASSET = "voice-models/sherpa-kws/decoder.onnx"
        const val KWS_JOINER_ASSET = "voice-models/sherpa-kws/joiner.int8.onnx"
        const val KWS_TOKENS_ASSET = "voice-models/sherpa-kws/tokens.txt"
        const val KWS_BPE_ASSET = "voice-models/sherpa-kws/bpe.model"
        const val KWS_KEYWORDS_ASSET = "voice-models/sherpa-kws/keywords.txt"
    }
}
