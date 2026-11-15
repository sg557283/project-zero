package com.zero.assistant.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.zero.assistant.core.ConversationalVoiceEngine
import com.zero.assistant.core.HumanoidVoiceCatalog
import com.zero.assistant.core.KokoroSpeakerCatalog
import com.zero.assistant.core.OfflineTtsVoice
import com.zero.assistant.core.SpeechOutputEngine
import com.zero.assistant.core.SpeechLanguage
import com.zero.assistant.core.VoiceModelCatalog
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

class KokoroSpeechOutputEngine(
    private val fallback: SpeechOutputEngine,
    private val modelManager: ModelDownloadManager,
    private val humanoidManager: HumanoidTtsManager,
) : SpeechOutputEngine {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val lock = Any()
    @Volatile private var closed = false
    @Volatile private var generation = 0L
    @Volatile private var activeTrack: AudioTrack? = null
    private var selectedSpeakerId: Int? = null
    private var selectedPiperVoiceId: String? = null
    private var sessionDirectory: String? = null
    private var sessionLanguageTag: String? = null
    private var ttsSession: KokoroTtsSession? = null
    private var piperSessionDirectory: String? = null
    private var piperSession: PiperTtsSession? = null
    private var completionListener: (() -> Unit)? = null
    private var errorListener: ((String) -> Unit)? = null

    override fun initialize(onInitialized: (Boolean) -> Unit) = fallback.initialize(onInitialized)

    override fun selectOfflineVoice(languageTag: String): Boolean {
        selectedSpeakerId = null
        selectedPiperVoiceId = null
        humanoidManager.deactivate()
        return fallback.selectOfflineVoice(languageTag)
    }

    override fun availableOfflineVoices(): List<OfflineTtsVoice> {
        val piperVoices = humanoidManager.installedVoices().map { spec ->
            OfflineTtsVoice(
                id = spec.voiceId,
                displayName = "${spec.name} · ${spec.style}",
                languageTag = spec.languageTag,
                languageLabel = spec.languageLabel,
                quality = if (spec.style.contains("HD", ignoreCase = true)) 600 else 550,
            )
        }
        val modelVoices = modelManager.availableVoices().map { (name, _) ->
            val speaker = requireNotNull(KokoroSpeakerCatalog.speaker(name))
            OfflineTtsVoice(
                id = "$KOKORO_VOICE_PREFIX$name",
                displayName = "${speaker.displayName} · ${speaker.gender.label}",
                languageTag = speaker.languageTag,
                languageLabel = speaker.languageLabel,
                quality = 500,
            )
        }
        return piperVoices + modelVoices
    }

    override fun selectVoice(voiceId: String): Boolean {
        val piperVoice = HumanoidVoiceCatalog.voice(voiceId)
        if (piperVoice != null) {
            if (!humanoidManager.activate(piperVoice.voiceId)) return false
            selectedSpeakerId = null
            selectedPiperVoiceId = piperVoice.voiceId
            return true
        }
        if (voiceId.startsWith(KOKORO_VOICE_PREFIX)) {
            val speaker = modelManager.availableVoices().firstOrNull {
                "$KOKORO_VOICE_PREFIX${it.first}" == voiceId
            }?.second ?: return false
            humanoidManager.deactivate()
            selectedSpeakerId = speaker
            selectedPiperVoiceId = null
            return true
        }
        selectedSpeakerId = null
        selectedPiperVoiceId = null
        humanoidManager.deactivate()
        return fallback.selectVoice(voiceId)
    }

    override fun setOnSpeechComplete(listener: () -> Unit) {
        completionListener = listener
        fallback.setOnSpeechComplete(listener)
    }

    override fun setOnSpeechError(listener: (String) -> Unit) {
        errorListener = listener
        fallback.setOnSpeechError(listener)
    }

    override fun prepareForVoiceModelRemoval(onReady: () -> Unit) {
        selectedSpeakerId = null
        selectedPiperVoiceId = null
        humanoidManager.deactivate()
        stop()
        try {
            executor.execute {
                synchronized(lock) {
                    ttsSession?.close()
                    ttsSession = null
                    sessionDirectory = null
                }
                onReady()
            }
        } catch (_: RejectedExecutionException) {
            onReady()
        }
    }

    override fun prepareForVoiceModelRemoval(voiceId: String, onReady: () -> Unit) {
        stop()
        try {
            executor.execute {
                synchronized(lock) {
                    if (voiceId.startsWith(PIPER_VOICE_PREFIX)) {
                        piperSession?.close()
                        piperSession = null
                        piperSessionDirectory = null
                        if (selectedPiperVoiceId == voiceId) selectedPiperVoiceId = null
                    }
                    if (voiceId.startsWith(KOKORO_VOICE_PREFIX)) {
                        ttsSession?.close()
                        ttsSession = null
                        sessionDirectory = null
                        selectedSpeakerId = null
                    }
                }
                onReady()
            }
        } catch (_: RejectedExecutionException) {
            onReady()
        }
    }

    override fun speak(text: String): Boolean {
        if (closed) return false
        val target = ConversationalVoiceEngine.languageFor(text)
        val piperVoiceId = selectedPiperVoiceId
        val piperVoice = piperVoiceId?.let(HumanoidVoiceCatalog::voice)
        if (piperVoice != null &&
            target.tag.substringBefore('-').equals(piperVoice.languageTag.substringBefore('-'), ignoreCase = true) &&
            humanoidManager.activeVoiceId.value == piperVoice.voiceId &&
            humanoidManager.isInstalled(piperVoice.voiceId)
        ) {
            val requestId = ++generation
            return try {
                executor.execute { synthesizeAndPlayPiper(text, piperVoice, requestId) }
                true
            } catch (_: RejectedExecutionException) {
                false
            }
        }
        val speaker = selectedSpeakerId
        if (speaker != null && target == SpeechLanguage.English &&
            modelManager.activeModelId.value == VoiceModelCatalog.KOKORO_ID &&
            modelManager.isInstalled()
        ) {
            val requestId = ++generation
            return try {
                executor.execute { synthesizeAndPlay(text, speaker, requestId) }
                true
            } catch (_: RejectedExecutionException) {
                false
            }
        }
        fallback.selectOfflineVoice(target.tag)
        return fallback.speak(text)
    }

    override fun stop() {
        generation += 1
        fallback.stop()
        synchronized(lock) {
            runCatching { activeTrack?.pause() }
            runCatching { activeTrack?.flush() }
            runCatching { activeTrack?.stop() }
        }
    }

    override fun shutdown() {
        if (closed) return
        closed = true
        stop()
        fallback.shutdown()
        try {
            executor.execute {
                synchronized(lock) {
                    ttsSession?.close()
                    ttsSession = null
                    sessionDirectory = null
                    piperSession?.close()
                    piperSession = null
                    piperSessionDirectory = null
                }
            }
        } catch (_: RejectedExecutionException) {
            Unit
        }
        executor.shutdown()
    }

    private fun synthesizeAndPlay(text: String, speakerId: Int, requestId: Long) {
        if (closed || requestId != generation) return
        try {
            val directory = modelManager.modelDirectory()
                ?: error("The active Kokoro model is no longer installed.")
            val session = sessionFor(directory, speakerId)
            val audio = session.generate(text, speakerId)
            check(audio.samples.isNotEmpty()) { "Kokoro returned no audio." }
            if (requestId != generation || closed) return
            play(audio.samples, audio.sampleRate, requestId)
        } catch (failure: Exception) {
            if (requestId == generation && !closed) {
                errorListener?.invoke(failure.message ?: "Kokoro speech synthesis failed.")
            }
        } catch (failure: LinkageError) {
            if (requestId == generation && !closed) {
                errorListener?.invoke(failure.message ?: "Kokoro runtime could not be loaded.")
            }
        }
    }

    private fun synthesizeAndPlayPiper(
        text: String,
        voice: com.zero.assistant.core.HumanoidVoiceSpec,
        requestId: Long,
    ) {
        if (closed || requestId != generation) return
        try {
            val directory = humanoidManager.modelDirectory(voice.voiceId)
                ?: error("The selected neural voice is no longer installed.")
            val session = piperSessionFor(directory)
            val audio = session.generate(text)
            check(audio.samples.isNotEmpty()) { "The neural voice returned no audio." }
            if (requestId != generation || closed) return
            play(audio.samples, audio.sampleRate, requestId)
        } catch (failure: Exception) {
            if (requestId == generation && !closed) {
                errorListener?.invoke(failure.message ?: "Neural speech synthesis failed.")
            }
        } catch (failure: LinkageError) {
            if (requestId == generation && !closed) {
                errorListener?.invoke(failure.message ?: "Sherpa-ONNX runtime could not be loaded.")
            }
        }
    }

    private fun sessionFor(directory: File, speakerId: Int): KokoroTtsSession {
        val path = directory.canonicalPath
        val languageTag = requireNotNull(KokoroSpeakerCatalog.speaker(speakerId)).languageTag
        if (sessionDirectory != path || sessionLanguageTag != languageTag) {
            ttsSession?.close()
            ttsSession = null
            sessionDirectory = path
            sessionLanguageTag = languageTag
        }
        return ttsSession ?: KokoroTtsSession(directory, languageTag).also { ttsSession = it }
    }

    private fun piperSessionFor(directory: File): PiperTtsSession {
        val path = directory.canonicalPath
        if (piperSessionDirectory != path) {
            piperSession?.close()
            piperSession = null
            piperSessionDirectory = path
        }
        return piperSession ?: PiperTtsSession(directory).also { piperSession = it }
    }

    private fun play(samples: FloatArray, sampleRate: Int, requestId: Long) {
        val pcm = ShortArray(samples.size) { index ->
            (samples[index].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBuffer > 0) { "Android could not configure audio playback for the Kokoro voice." }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuffer, 8_192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            synchronized(lock) {
                if (requestId != generation || closed) return
                activeTrack = track
            }
            track.play()
            var offset = 0
            while (offset < pcm.size && requestId == generation && !closed) {
                val count = track.write(
                    pcm,
                    offset,
                    minOf(4_096, pcm.size - offset),
                    AudioTrack.WRITE_BLOCKING,
                )
                check(count > 0) { "Android audio playback stopped unexpectedly." }
                offset += count
            }
            while (requestId == generation && !closed &&
                track.playState == AudioTrack.PLAYSTATE_PLAYING &&
                track.playbackHeadPosition < pcm.size
            ) {
                Thread.sleep(12)
            }
            if (requestId == generation && !closed) completionListener?.invoke()
        } finally {
            synchronized(lock) {
                if (activeTrack === track) activeTrack = null
                runCatching { track.stop() }
                track.release()
            }
        }
    }

    companion object {
        private const val KOKORO_VOICE_PREFIX = "kokoro:"
        private const val PIPER_VOICE_PREFIX = "piper:"
    }
}
