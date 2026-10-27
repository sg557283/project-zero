package com.zero.assistant.data

import com.zero.assistant.core.AiProviderContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

interface AssistantAiProvider {
    suspend fun answer(prompt: String): String
}

class OpenAiCompatibleProvider(
    private val endpoint: String,
    private val model: String,
    private val apiKey: String,
) : AssistantAiProvider {
    override suspend fun answer(prompt: String): String {
        require(AiProviderContract.validHttpsEndpoint(endpoint)) { "The configured provider endpoint is not a valid HTTPS URL." }
        require(model.isNotBlank() && apiKey.isNotBlank() && prompt.isNotBlank()) { "Online AI configuration is incomplete." }
        val connectionRef = AtomicReference<HttpURLConnection?>()
        val job = currentCoroutineContext()[kotlinx.coroutines.Job]
        val cancellationHandle = job?.invokeOnCompletion { cause ->
            if (cause is CancellationException) connectionRef.getAndSet(null)?.disconnect()
        }
        return try {
            withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = false
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                connectionRef.set(connection)
                try {
                    val payload = JSONObject()
                        .put("model", model)
                        .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                        .toString()
                    connection.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }
                    currentCoroutineContext().ensureActive()
                    val status = connection.responseCode
                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    val responseText = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                    if (status !in 200..299) throw providerError(status)
                    val answer = JSONObject(responseText)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim()
                    if (answer.isBlank()) throw IOException("The provider returned an empty response.")
                    answer
                } finally {
                    connectionRef.compareAndSet(connection, null)
                    connection.disconnect()
                }
            }
        } finally {
            cancellationHandle?.dispose()
            connectionRef.getAndSet(null)?.disconnect()
        }
    }

    private fun providerError(status: Int): IOException = when (status) {
        401, 403 -> IOException("The provider rejected the API key. Check its permissions in Settings.")
        429 -> IOException("The provider rate limit was reached. Try again later.")
        in 500..599 -> IOException("The provider is temporarily unavailable (HTTP $status).")
        else -> IOException("The provider request failed (HTTP $status).")
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 25_000
    }
}