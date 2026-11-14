package com.zero.assistant.data

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import android.util.Log
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import com.zero.assistant.core.MobileActionBrainState
import com.zero.assistant.core.MobileActionToolCall
import com.zero.assistant.core.VoiceModelCatalog
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MobileActionBrainEngine(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val modelSpec = VoiceModelCatalog.actionBrain
    private val modelStorageRoot =
        appContext.getExternalFilesDir("ai_models") ?: File(appContext.filesDir, "ai_models")
    private val modelDirectory = File(modelStorageRoot, modelSpec.id)
    private val modelFile = File(modelDirectory, "${modelSpec.id}.litertlm")
    private val partialFile = File(modelDirectory, "${modelSpec.id}.litertlm.part")
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val cancelled = AtomicBoolean(false)
    private val engineLock = Any()
    private val pauseLock = Object()
    @Volatile private var paused = false
    @Volatile private var activeConnection: HttpURLConnection? = null
    @Volatile private var closed = false
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    private val mutableState = MutableStateFlow(initialState())
    val state: StateFlow<MobileActionBrainState> = mutableState.asStateFlow()

    val isActive: Boolean
        get() = synchronized(engineLock) { conversation != null && !closed }

    val totalMemoryBytes: Long
        get() = appContext.getSystemService(ActivityManager::class.java)?.let { manager ->
            ActivityManager.MemoryInfo().also(manager::getMemoryInfo).totalMem
        } ?: 0L

    val meetsRecommendedMemory: Boolean
        get() = totalMemoryBytes >= MIN_RECOMMENDED_MEMORY_BYTES

    fun download(): Boolean {
        if (closed || !meetsRecommendedMemory || executor.isShutdown ||
            isInstalled() || state.value is MobileActionBrainState.Downloading ||
            state.value == MobileActionBrainState.Paused || state.value == MobileActionBrainState.Loading ||
            state.value == MobileActionBrainState.Removing
        ) return false
        cancelled.set(false)
        paused = false
        mutableState.value = MobileActionBrainState.Downloading(partialFile.length(), modelSpec.sizeBytes)
        return try {
            executor.execute(::downloadModel)
            true
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            mutableState.value = MobileActionBrainState.Failed("The action brain download worker is unavailable.")
            false
        }
    }

    fun pause() {
        if (state.value !is MobileActionBrainState.Downloading) return
        paused = true
        activeConnection?.disconnect()
        mutableState.value = MobileActionBrainState.Paused
    }

    fun resume(): Boolean {
        if (closed || state.value != MobileActionBrainState.Paused) return false
        paused = false
        synchronized(pauseLock) { pauseLock.notifyAll() }
        return true
    }

    fun cancel() {
        cancelled.set(true)
        paused = false
        activeConnection?.disconnect()
        synchronized(pauseLock) { pauseLock.notifyAll() }
    }

    suspend fun activate(): Result<Unit> = withContext(Dispatchers.IO) {
        mutableState.value = MobileActionBrainState.Loading
        try {
            synchronized(engineLock) {
                check(!closed) { "The action brain manager is closed." }
                check(meetsRecommendedMemory) {
                    "This model recommends a device with at least 4 GB of RAM."
                }
                check(isInstalled()) { "Download the action brain before activating it." }
                check(sha256(modelFile) == modelSpec.sha256) { "The model file failed its SHA-256 integrity check." }
                closeEngine()
                val loadedEngine = Engine(
                    EngineConfig(
                        modelPath = modelFile.absolutePath,
                        maxNumTokens = MAX_CONTEXT_TOKENS,
                        cacheDir = File(modelDirectory, "cache").apply {
                            check(isDirectory || mkdirs()) { "Unable to create the model cache directory." }
                        }.absolutePath,
                    ),
                )
                try {
                    loadedEngine.initialize()
                    val loadedConversation = loadedEngine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(
                                "You are a mobile action planner. Use only the provided tools. " +
                                    "Return tool calls for requested actions; do not claim an action is complete.",
                            ),
                            tools = actionTools(),
                            automaticToolCalling = false,
                            maxOutputToken = MAX_OUTPUT_TOKENS,
                        ),
                    )
                    engine = loadedEngine
                    conversation = loadedConversation
                } catch (failure: Exception) {
                    if (loadedEngine.isInitialized()) loadedEngine.close()
                    throw failure
                }
            }
            mutableState.value = MobileActionBrainState.Active
            Result.success(Unit)
        } catch (failure: Exception) {
            mutableState.value = MobileActionBrainState.Failed(
                failure.message ?: "The offline action brain could not be activated.",
            )
            Result.failure(failure)
        }
    }

    suspend fun plan(command: String): List<MobileActionToolCall> = withContext(Dispatchers.IO) {
        require(command.isNotBlank() && command.length <= MAX_COMMAND_LENGTH) { "Command is empty or too long." }
        synchronized(engineLock) {
            check(!closed) { "The action brain manager is closed." }
            val activeConversation = conversation ?: error("Activate the offline action brain first.")
            activeConversation.sendMessage(command, maxOutputToken = MAX_OUTPUT_TOKENS)
                .toolCalls
                .take(MAX_TOOL_CALLS + 1)
                .map { MobileActionToolCall(it.name, it.arguments) }
        }
    }

    suspend fun uninstall(): Boolean = withContext(Dispatchers.IO) {
        if (closed || !modelDirectory.exists() ||
            state.value == MobileActionBrainState.Removing ||
            state.value is MobileActionBrainState.Downloading ||
            state.value == MobileActionBrainState.Paused ||
            state.value == MobileActionBrainState.Loading
        ) return@withContext false
        mutableState.value = MobileActionBrainState.Removing
        val removed = try {
            synchronized(engineLock) { closeEngine() }
            deleteTree(modelDirectory)
        } catch (failure: Exception) {
            mutableState.value = MobileActionBrainState.RemoveFailed(
                failure.message ?: "The LiteRT-LM engine could not be unloaded.",
            )
            return@withContext false
        }
        mutableState.value = if (removed) {
            MobileActionBrainState.Idle
        } else {
            MobileActionBrainState.RemoveFailed("Some action brain files could not be removed.")
        }
        removed
    }

    fun isInstalled(): Boolean =
        modelFile.isFile && modelFile.length() == modelSpec.sizeBytes

    private fun initialState(): MobileActionBrainState =
        if (isInstalled()) MobileActionBrainState.Installed else MobileActionBrainState.Idle

    private fun downloadModel() {
        try {
            val storage = modelDirectory.parentFile ?: appContext.filesDir
            check(storage.exists() || storage.mkdirs()) { "Unable to access action brain storage." }
            check(modelDirectory.isDirectory || modelDirectory.mkdirs()) {
                "Unable to create action brain storage."
            }
            check(StatFs(storage.absolutePath).availableBytes >= modelSpec.sizeBytes + MIN_FREE_SPACE_BYTES) {
                "Not enough free storage. Free at least 352 MB and retry."
            }
            downloadFile()
            checkCancelled()
            if (partialFile.length() != modelSpec.sizeBytes) {
                partialFile.delete()
                error("Downloaded model size does not match the catalog.")
            }
            if (sha256(partialFile) != modelSpec.sha256) {
                partialFile.delete()
                error("Downloaded model failed its SHA-256 integrity check.")
            }
            check(modelFile.parentFile?.let { it.exists() || it.mkdirs() } == true) {
                "Unable to create action brain storage."
            }
            check(partialFile.renameTo(modelFile)) { "Unable to install the verified action brain file." }
            mutableState.value = MobileActionBrainState.Installed
        } catch (_: DownloadCancelledException) {
            partialFile.delete()
            mutableState.value = MobileActionBrainState.Cancelled
        } catch (failure: Exception) {
            if (cancelled.get() || closed) {
                partialFile.delete()
                mutableState.value = MobileActionBrainState.Cancelled
            } else {
                mutableState.value = MobileActionBrainState.Failed(
                    failure.message ?: "Action brain download failed.",
                )
            }
        } finally {
            activeConnection?.disconnect()
            activeConnection = null
        }
    }

    private fun downloadFile() {
        var received = partialFile.length()
        check(received <= modelSpec.sizeBytes) { "Partial download exceeds the catalog size." }
        while (received < modelSpec.sizeBytes) {
            waitIfPaused()
            checkCancelled()
            var connection: HttpURLConnection? = null
            try {
                connection = openConnection(received)
                activeConnection = connection
                val expectedStatus = if (received == 0L) HttpURLConnection.HTTP_OK else HttpURLConnection.HTTP_PARTIAL
                check(connection.responseCode == expectedStatus) {
                    "Download server did not support safe resume (HTTP ${connection.responseCode})."
                }
                if (received > 0) {
                    check(connection.getHeaderField("Content-Range")?.startsWith("bytes $received-") == true) {
                        "Download resume started at an unexpected byte."
                    }
                }
                val announcedLength = connection.contentLengthLong
                check(announcedLength < 0 || received + announcedLength <= modelSpec.sizeBytes) {
                    "Download exceeded the catalog size."
                }
                FileOutputStream(partialFile, received > 0).use { output ->
                    BufferedInputStream(connection.inputStream).use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            checkCancelled()
                            if (paused) break
                            val count = input.read(buffer)
                            if (count < 0) break
                            received += count
                            check(received <= modelSpec.sizeBytes) { "Download exceeded the catalog size." }
                            output.write(buffer, 0, count)
                            mutableState.value = MobileActionBrainState.Downloading(received, modelSpec.sizeBytes)
                        }
                        output.fd.sync()
                    }
                }
            } catch (failure: Exception) {
                if (!paused) throw failure
            } finally {
                connection?.disconnect()
                activeConnection = null
            }
        }
    }

    private fun openConnection(offset: Long): HttpURLConnection {
        var nextUrl = modelSpec.downloadUrl
        repeat(MAX_REDIRECTS + 1) { redirect ->
            val url = URL(nextUrl)
            check(url.protocol == "https") { "Action brain source must use HTTPS." }
            val connection = url.openConnection() as? HttpURLConnection
                ?: error("Action brain source did not return an HTTP connection.")
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
            val code = connection.responseCode
            if (code in REDIRECT_CODES) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                check(redirect < MAX_REDIRECTS && !location.isNullOrBlank()) {
                    "Too many or invalid download redirects."
                }
                nextUrl = URL(url, location).toString()
            } else {
                return connection
            }
        }
        error("Too many download redirects.")
    }

    private fun waitIfPaused() {
        if (!paused) return
        mutableState.value = MobileActionBrainState.Paused
        synchronized(pauseLock) {
            while (paused && !cancelled.get() && !closed) pauseLock.wait()
        }
        checkCancelled()
        mutableState.value = MobileActionBrainState.Downloading(partialFile.length(), modelSpec.sizeBytes)
    }

    private fun checkCancelled() {
        if (cancelled.get() || closed) throw DownloadCancelledException()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                checkCancelled()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun closeEngine() {
        val activeConversation = conversation
        conversation = null
        val activeEngine = engine
        engine = null
        var closeFailure: Exception? = null
        try {
            activeConversation?.close()
        } catch (failure: Exception) {
            closeFailure = failure
        }
        try {
            activeEngine?.let { loaded -> if (loaded.isInitialized()) loaded.close() }
        } catch (failure: Exception) {
            if (closeFailure == null) closeFailure = failure else closeFailure.addSuppressed(failure)
        }
        closeFailure?.let { throw it }
    }

    private fun deleteTree(file: File): Boolean {
        if (!file.exists()) return true
        return try {
            file.walkBottomUp().all { it.delete() }
        } catch (_: SecurityException) {
            false
        }
    }

    override fun close() {
        closed = true
        cancelled.set(true)
        paused = false
        activeConnection?.disconnect()
        synchronized(pauseLock) { pauseLock.notifyAll() }
        executor.shutdownNow()
        try {
            synchronized(engineLock) { closeEngine() }
        } catch (failure: Exception) {
            Log.e(TAG, "Failed to close the LiteRT-LM engine.", failure)
        }
    }

    private class DownloadCancelledException : Exception()

    companion object {
        private const val MIN_RECOMMENDED_MEMORY_BYTES = 4L * 1024 * 1024 * 1024
        private const val MIN_FREE_SPACE_BYTES = 64L * 1024 * 1024
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 5
        private const val MAX_COMMAND_LENGTH = 2_000
        private const val MAX_TOOL_CALLS = 8
        private const val MAX_CONTEXT_TOKENS = 1_024
        private const val MAX_OUTPUT_TOKENS = 256
        private const val TAG = "MobileActionBrain"
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        private fun actionTools(): List<ToolProvider> = listOf(
            noArgumentTool("turn_on_flashlight", "Turn the device flashlight on."),
            noArgumentTool("turn_off_flashlight", "Turn the device flashlight off."),
            objectTool(
                "make_call",
                "Prepare a reviewed phone call request.",
                """{"contact":{"type":"string"},"phone_number":{"type":"string"}}""",
            ),
            objectTool(
                "send_whatsapp_message",
                "Prepare a WhatsApp message draft for user review; never send it.",
                """{"contact":{"type":"string"},"message":{"type":"string"}}""",
                listOf("contact", "message"),
            ),
            objectTool(
                "open_app_and_search",
                "Open an app and optionally search for text.",
                """{"app":{"type":"string"},"query":{"type":"string"}}""",
                listOf("app"),
            ),
            objectTool(
                "control_active_call",
                "Control an active call after user authorization.",
                """{"action":{"type":"string","enum":["answer","end","mute","unmute","speaker on","speaker off"]}}""",
                listOf("action"),
            ),
            objectTool("take_call_note", "Request the call note-taking feature.", """{"command":{"type":"string"}}"""),
            objectTool(
                "create_reminder",
                "Prepare a reminder title for the planner.",
                """{"title":{"type":"string"},"date_time":{"type":"string"}}""",
                listOf("title"),
            ),
            objectTool("get_weather", "Get weather for a city or current location.", """{"city":{"type":"string"}}"""),
            noArgumentTool("read_active_screen", "Read or summarize the active screen on request."),
            objectTool(
                "perform_accessibility_click",
                "Request a visible screen click through the existing accessibility service.",
                """{"target":{"type":"string"}}""",
                listOf("target"),
            ),
        )

        private fun noArgumentTool(name: String, description: String): ToolProvider =
            objectTool(name, description, "{}", emptyList())

        private fun objectTool(
            name: String,
            description: String,
            properties: String,
            required: List<String> = emptyList(),
        ): ToolProvider {
            val requiredJson = required.joinToString(",") { "\"$it\"" }
            val requiredField = if (required.isEmpty()) "" else ""","required":[$requiredJson]"""
            val schema = """{"name":"$name","description":"$description","parameters":{"type":"object","properties":$properties$requiredField}}"""
            return tool(object : OpenApiTool {
                override fun getToolDescriptionJsonString(): String = schema
                override fun execute(paramsJsonString: String): String =
                    error("Model tool execution is disabled; calls are dispatched by ZERO.")
            })
        }
    }
}
