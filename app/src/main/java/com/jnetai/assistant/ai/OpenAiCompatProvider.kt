package com.jnetai.assistant.ai

import com.google.gson.Gson
import com.jnetai.assistant.data.model.AuthType
import com.jnetai.assistant.data.model.ConnectionProfile
import com.jnetai.assistant.network.ApiException
import com.jnetai.assistant.network.HttpEngine
import com.jnetai.assistant.network.dto.ChatCompletionRequest
import com.jnetai.assistant.network.dto.ChatCompletionResponse
import com.jnetai.assistant.network.dto.ChatMessageRole
import com.jnetai.assistant.network.dto.EmbeddingRequest
import com.jnetai.assistant.network.dto.EmbeddingResponse
import com.jnetai.assistant.network.dto.ModelListResponse
import com.jnetai.assistant.util.Err
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader

/**
 * OpenAI-compatible provider. Supports OpenCode, generic OpenAI-compatible
 * servers, and any custom endpoint that implements /v1/chat/completions and
 * (optionally) /v1/models and /v1/embeddings. The base URL is fully
 * user-configurable — no provider is assumed to be OpenAI.
 */
class OpenAiCompatProvider(
    private val profile: ConnectionProfile,
    private val apiKeyResolver: () -> String?
) : AIProvider {

    private val http = HttpEngine()
    private val gson = Gson()

    override val capabilities = ProviderCapabilities(
        chat = true,
        streaming = true,
        modelList = true,
        embeddings = true
    )

    /** Builds e.g. https://example.com:8443/v1 — never swallows malformed input. */
    private fun baseUrl(): String {
        val raw = profile.endpoint.trim().trimEnd('/')
        val scheme = if (profile.tlsEnabled) "https" else "http"
        val portPart = if (profile.port > 0) ":${profile.port}" else ""
        // If the user already typed a scheme, respect it exactly (never silently downgrade).
        val effective = if (raw.startsWith("http://") || raw.startsWith("https://")) {
            raw
        } else {
            "$scheme://$raw"
        }
        return effective + portPart
    }

    private fun headersMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val parsed = gson.fromJson(profile.customHeaders, Map::class.java) as? Map<*, *>
            parsed?.forEach { (k, v) -> if (k != null && v != null) map[k.toString()] = v.toString() }
        } catch (t: Throwable) {
            Err.e(Err.PROFILE_INVALID_URL, "Invalid custom headers JSON", t)
        }
        return map
    }

    private fun authHeaderValue(): Pair<String, String>? {
        val key = apiKeyResolver() ?: return null
        return when (profile.authType) {
            AuthType.NONE -> null
            AuthType.BEARER -> "Authorization" to "Bearer $key"
            AuthType.API_KEY -> "api-key" to key
            AuthType.BASIC -> "Authorization" to "Basic ${key}"
        }
    }

    private fun buildMessages(messages: List<ChatMessageInput>): List<ChatMessageRole> =
        messages.map { ChatMessageRole(it.role, it.content) }

    private fun requestBody(stream: Boolean): String {
        val req = ChatCompletionRequest(
            model = profile.model,
            messages = emptyList(), // filled by callers
            stream = stream,
            temperature = profile.temperature,
            topP = profile.topP,
            maxTokens = profile.maxTokens
        )
        return gson.toJson(req)
    }

    override suspend fun chat(messages: List<ChatMessageInput>): ChatResult = withContext(Dispatchers.IO) {
        val req = ChatCompletionRequest(
            model = profile.model,
            messages = buildMessages(messages),
            stream = false,
            temperature = profile.temperature,
            topP = profile.topP,
            maxTokens = profile.maxTokens
        )
        val auth = authHeaderValue()
        val url = "${baseUrl()}/chat/completions"
        val body = http.request(
            url = url, method = "POST", body = gson.toJson(req),
            apiKey = null, authHeader = "Authorization",
            timeoutMs = profile.timeoutMs, headers = (auth?.let { mapOf(it.first to it.second) } ?: emptyMap()) + headersMap()
        )
        try {
            val parsed = gson.fromJson(body, ChatCompletionResponse::class.java)
            val text = parsed.choices.firstOrNull()?.message?.content ?: ""
            if (text.isEmpty()) Err.w("Empty completion text from $url")
            ChatResult(
                text = text,
                promptTokens = parsed.usage?.promptTokens?.toLong() ?: estimateTokens(messages.joinToString("") { it.content }),
                completionTokens = parsed.usage?.completionTokens?.toLong() ?: estimateTokens(text),
                toolCalls = parsed.choices.firstOrNull()?.message?.toolCalls?.map { it.toStub() } ?: emptyList()
            )
        } catch (t: Throwable) {
            Err.e(Err.API_MALFORMED, "Malformed chat response", t)
            throw ApiException(Err.API_MALFORMED, "Server returned an unexpected response")
        }
    }

    /** Rough token estimate for accounting when the provider doesn't report usage. */
    private fun estimateTokens(text: String): Long {
        if (text.isEmpty()) return 0
        // heuristic: ~4 chars per token for latin text
        return (text.length / 4).toLong().coerceAtLeast(1)
    }

    override suspend fun chatStream(messages: List<ChatMessageInput>): Flow<ChatChunk> = flow {
        val req = ChatCompletionRequest(
            model = profile.model,
            messages = buildMessages(messages),
            stream = true,
            temperature = profile.temperature,
            topP = profile.topP,
            maxTokens = profile.maxTokens
        )
        val auth = authHeaderValue()
        val url = "${baseUrl()}/chat/completions"
        val headers = (auth?.let { mapOf(it.first to it.second) } ?: emptyMap()) + headersMap()
        val resp = withContext(Dispatchers.IO) {
            http.execute(
                url = url, method = "POST", body = gson.toJson(req),
                apiKey = null, authHeader = "Authorization",
                keepAlive = false, timeoutMs = profile.timeoutMs, headers = headers
            )
        }
        resp.use { r ->
            if (!r.isSuccessful) {
                val text = r.body?.string() ?: ""
                Err.e(Err.API_STREAM_ERROR, "Stream HTTP ${r.code}")
                throw ApiException(
                    if (r.code == 401) Err.API_UNAUTHORIZED else if (r.code == 429) Err.API_RATE_LIMIT else Err.API_MALFORMED,
                    "Streaming request failed (${r.code})"
                )
            }
            val reader: BufferedReader = r.body?.charStream()?.buffered() ?: throw ApiException(Err.API_MALFORMED, "Empty stream")
            var line: String?
            val sb = StringBuilder()
            var promptTokens = 0L
            var completionTokens = 0L
            while (true) {
                line = reader.readLine() ?: break
                if (line.isEmpty() || line == "\r") continue
                if (line.startsWith("data:")) {
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    try {
                        val chunk = gson.fromJson(data, ChatCompletionResponse::class.java)
                        val choice = chunk.choices.firstOrNull()
                        val deltaText = choice?.delta?.content
                        if (deltaText != null) {
                            sb.append(deltaText)
                            emit(ChatChunk(text = deltaText, done = false))
                        }
                        completionTokens = chunk.usage?.completionTokens?.toLong() ?: completionTokens
                        promptTokens = chunk.usage?.promptTokens?.toLong() ?: promptTokens
                        choice?.delta?.toolCalls?.let { calls ->
                            emit(ChatChunk(text = "", done = false, toolCalls = calls.mapNotNull { c ->
                                c.function?.name?.let { name ->
                                    ToolCallStub(id = c.id ?: "", name = name, arguments = c.function?.arguments ?: "{}")
                                }
                            }))
                        }
                    } catch (_: Throwable) {
                        // ignore malformed interim chunks; the stream continues
                    }
                }
            }
            val full = sb.toString()
            emit(
                ChatChunk(
                    text = "", done = true,
                    promptTokens = promptTokens, completionTokens = if (completionTokens > 0) completionTokens else estimateTokens(full)
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(): List<ModelInfoLite> = withContext(Dispatchers.IO) {
        // only call when profile.model would still resolve; graceful fallback on 404
        val auth = authHeaderValue()
        val url = "${baseUrl()}/models"
        val headers = (auth?.let { mapOf(it.first to it.second) } ?: emptyMap()) + headersMap()
        val body = try {
            http.request(url = url, apiKey = null, authHeader = "Authorization", timeoutMs = profile.timeoutMs, headers = headers)
        } catch (e: ApiException) {
            Err.w("Model discovery unsupported: ${e.userMessage}")
            return@withContext emptyList()
        }
        try {
            gson.fromJson(body, ModelListResponse::class.java).let { resp ->
                (resp.data + resp.models).distinctBy { it.id }.map { ModelInfoLite(it.id) }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    override suspend fun embed(texts: List<String>): List<List<Double>> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()
        val auth = authHeaderValue()
        val url = "${baseUrl()}/embeddings"
        val req = EmbeddingRequest(model = profile.model, input = texts)
        val headers = (auth?.let { mapOf(it.first to it.second) } ?: emptyMap()) + headersMap()
        val body = try {
            http.request(url = url, method = "POST", body = gson.toJson(req), apiKey = null, authHeader = "Authorization", timeoutMs = profile.timeoutMs, headers = headers)
        } catch (e: ApiException) {
            Err.w("Embedding endpoint unavailable: ${e.userMessage}")
            return@withContext emptyList()
        }
        try {
            gson.fromJson(body, EmbeddingResponse::class.java).data.sortedBy { it.index }.map { it.embedding }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    override suspend fun testConnection(): ConnectionTestResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val target = "${baseUrl()}/models"
        val auth = authHeaderValue()
        val headers = (auth?.let { mapOf(it.first to it.second) } ?: emptyMap()) + headersMap()
        try {
            val body = http.request(url = target, apiKey = null, authHeader = "Authorization", timeoutMs = profile.timeoutMs, headers = headers)
            val latency = System.currentTimeMillis() - start
            val models = try {
                gson.fromJson(body, ModelListResponse::class.java).let { (it.data + it.models).distinctBy { m -> m.id }.map { m -> m.id } }
            } catch (_: Throwable) { emptyList() }
            ConnectionTestResult(
                ok = true, latencyMs = latency,
                message = "Authenticated", authOk = true,
                modelOk = profile.model.isBlank() || models.isEmpty() || models.contains(profile.model),
                models = models
            )
        } catch (e: ApiException) {
            ConnectionTestResult(ok = false, latencyMs = System.currentTimeMillis() - start, message = e.userMessage)
        }
    }
}

fun com.jnetai.assistant.network.dto.ToolCall.toStub() =
    ToolCallStub(id = id ?: "", name = function?.name ?: "", arguments = function?.arguments ?: "{}")

fun com.jnetai.assistant.network.dto.ToolFunction.asStub() = ToolCallStub(name = name ?: "", arguments = arguments ?: "{}")