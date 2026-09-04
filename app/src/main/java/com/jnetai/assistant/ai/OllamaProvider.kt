package com.jnetai.assistant.ai

import com.google.gson.Gson
import com.jnetai.assistant.data.model.ConnectionProfile
import com.jnetai.assistant.network.ApiException
import com.jnetai.assistant.network.HttpEngine
import com.jnetai.assistant.network.dto.OllamaChatMessage
import com.jnetai.assistant.network.dto.OllamaChatRequest
import com.jnetai.assistant.network.dto.OllamaChatResponse
import com.jnetai.assistant.network.dto.OllamaEmbedRequest
import com.jnetai.assistant.network.dto.OllamaEmbedResponse
import com.jnetai.assistant.network.dto.OllamaModelsResponse
import com.jnetai.assistant.util.Err
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader

/**
 * Ollama provider. Works whether Ollama runs on the phone itself, another
 * LAN machine, a Raspberry Pi, or any reachable host — URL/port are fully
 * configurable. Provides chat, streaming, model discovery and embeddings.
 */
class OllamaProvider(
    private val profile: ConnectionProfile,
    private val apiKeyResolver: () -> String?
) : AIProvider {

    private val http = HttpEngine()
    private val gson = Gson()

    override val capabilities = ProviderCapabilities(
        chat = true, streaming = true, modelList = true, embeddings = true
    )

    private fun baseUrl(): String {
        val raw = profile.endpoint.trim().trimEnd('/')
        val scheme = if (profile.tlsEnabled) "https" else "http"
        val effective = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "$scheme://$raw"
        return effective + (if (profile.port > 0) ":${profile.port}" else "")
    }

    private fun headers(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val parsed = gson.fromJson(profile.customHeaders, Map::class.java) as? Map<*, *>
            parsed?.forEach { (k, v) -> if (k != null && v != null) map[k.toString()] = v.toString() }
        } catch (_: Throwable) {}
        apiKeyResolver()?.let { map["Authorization"] = "Bearer $it" }
        return map
    }

    override suspend fun chat(messages: List<ChatMessageInput>): ChatResult = withContext(Dispatchers.IO) {
        val req = OllamaChatRequest(
            model = profile.model,
            messages = messages.map { OllamaChatMessage(it.role, it.content) },
            stream = false,
            options = mapOf(
                "temperature" to profile.temperature,
                "top_p" to profile.topP,
                "num_predict" to profile.maxTokens
            )
        )
        val url = "${baseUrl()}/api/chat"
        val body = try {
            http.request(url = url, method = "POST", body = gson.toJson(req), timeoutMs = profile.timeoutMs, headers = headers())
        } catch (_: ApiException) {
            // Ollama sometimes returns 200 with an error field; fall back to /api/generate for older versions
            val genUrl = "${baseUrl()}/api/generate"
            http.request(
                url = genUrl, method = "POST",
                body = gson.toJson(mapOf("model" to profile.model, "prompt" to (messages.map{it.content}.joinToString("\n")), "options" to mapOf("num_predict" to profile.maxTokens))),
                timeoutMs = profile.timeoutMs, headers = headers()
            )
        }
        val parsed = try {
            gson.fromJson(body, OllamaChatResponse::class.java)
        } catch (_: Throwable) {
            Err.e(Err.API_MALFORMED, "Malformed Ollama response")
            throw ApiException(Err.API_MALFORMED, "Ollama returned an unexpected response")
        }
        val text = parsed.message?.content ?: parsed.response ?: ""
        ChatResult(text = text, promptTokens = estimate(text), completionTokens = estimate(text))
    }

    private fun estimate(t: String): Long = (t.length / 4).toLong().coerceAtLeast(1)

    override suspend fun chatStream(messages: List<ChatMessageInput>): Flow<ChatChunk> = flow {
        val req = OllamaChatRequest(
            model = profile.model,
            messages = messages.map { OllamaChatMessage(it.role, it.content) },
            stream = true,
            options = mapOf(
                "temperature" to profile.temperature,
                "top_p" to profile.topP,
                "num_predict" to profile.maxTokens
            )
        )
        val url = "${baseUrl()}/api/chat"
        val resp = withContext(Dispatchers.IO) {
            http.execute(url = url, method = "POST", body = gson.toJson(req), keepAlive = false, timeoutMs = profile.timeoutMs, headers = headers())
        }
        resp.use { r ->
            if (!r.isSuccessful) {
                throw ApiException(Err.API_MALFORMED, "Ollama streaming failed (${r.code})")
            }
            val reader: BufferedReader = r.body?.charStream()?.buffered()
                ?: throw ApiException(Err.API_MALFORMED, "Empty Ollama stream")
            var line: String?
            val sb = StringBuilder()
            while (true) {
                line = reader.readLine() ?: break
                if (line.isBlank()) continue
                try {
                    val chunk = gson.fromJson(line, OllamaChatResponse::class.java)
                    when {
                        chunk.message?.content?.isNotEmpty() == true -> {
                            sb.append(chunk.message.content)
                            emit(ChatChunk(text = chunk.message.content, done = chunk.done))
                        }
                        chunk.response?.isNotEmpty() == true -> {
                            sb.append(chunk.response)
                            emit(ChatChunk(text = chunk.response, done = chunk.done))
                        }
                        chunk.done -> emit(ChatChunk(text = "", done = true))
                    }
                } catch (_: Throwable) {
                    // tolerate malformed interim events
                }
            }
            emit(ChatChunk(text = "", done = true, completionTokens = estimate(sb.toString())))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(): List<ModelInfoLite> = withContext(Dispatchers.IO) {
        val url = "${baseUrl()}/api/tags"
        try {
            val body = http.request(url = url, timeoutMs = profile.timeoutMs, headers = headers())
            gson.fromJson(body, OllamaModelsResponse::class.java).models.map { ModelInfoLite(it.name) }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    override suspend fun embed(texts: List<String>): List<List<Double>> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()
        val url = "${baseUrl()}/api/embeddings"
        val results = mutableListOf<List<Double>>()
        for (text in texts) {
            try {
                val body = http.request(
                    url = url, method = "POST",
                    body = gson.toJson(OllamaEmbedRequest(model = profile.model, input = text)),
                    timeoutMs = profile.timeoutMs, headers = headers()
                )
                results += gson.fromJson(body, OllamaEmbedResponse::class.java).embedding
            } catch (_: Throwable) {
                Err.w("Ollama embed failed for one input")
            }
        }
        results
    }

    override suspend fun testConnection(): ConnectionTestResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val url = "${baseUrl()}/api/tags"
        try {
            val body = http.request(url = url, timeoutMs = profile.timeoutMs, headers = headers())
            val latency = System.currentTimeMillis() - start
            val models = try {
                gson.fromJson(body, OllamaModelsResponse::class.java).models.map { it.name }
            } catch (_: Throwable) { emptyList() }
            ConnectionTestResult(
                ok = true, latencyMs = latency, message = "Ollama connected",
                authOk = true,
                modelOk = profile.model.isBlank() || models.isEmpty() || models.contains(profile.model),
                models = models
            )
        } catch (e: ApiException) {
            ConnectionTestResult(ok = false, latencyMs = System.currentTimeMillis() - start, message = e.userMessage)
        }
    }
}