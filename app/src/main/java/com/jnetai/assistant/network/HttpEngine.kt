package com.jnetai.assistant.network

import com.google.gson.Gson
import com.jnetai.assistant.data.model.ConnectionProfile
import com.jnetai.assistant.util.Err
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * HTTP core with timeout handling, cancellation, HTTP/HTTPS and per-request
 * custom headers. Converts network failures into stable Err codes with
 * user-friendly messages. Never exposes secrets in errors.
 */
class HttpEngine {
    private val gson = Gson()

    @Throws(ApiException::class)
    fun execute(
        url: String,
        method: String = "GET",
        body: String? = null,
        apiKey: String? = null,
        authHeader: String = "Authorization",
        keepAlive: Boolean = true,
        timeoutMs: Long = 60000,
        headers: Map<String, String> = emptyMap()
    ): Response {
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
        if (!keepAlive) clientBuilder.connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.MILLISECONDS))

        val reqBuilder = Request.Builder().url(url)
        if (method != "GET") {
            val media = "application/json; charset=utf-8".toMediaType()
            reqBuilder.method(method, (body ?: "{}").toRequestBody(media))
        }
        apiKey?.let { reqBuilder.header(authHeader, it) }
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }

        try {
            return clientBuilder.build().newCall(reqBuilder.build()).execute()
        } catch (e: SocketTimeoutException) {
            Err.e(Err.NETWORK_TIMEOUT, "Timeout calling $url", e)
            throw ApiException(Err.NETWORK_TIMEOUT, "Request timed out")
        } catch (e: UnknownHostException) {
            Err.e(Err.NETWORK_UNREACHABLE, "Host unreachable: $url", e)
            throw ApiException(Err.NETWORK_UNREACHABLE, "Server not reachable — check address and connectivity")
        } catch (e: SSLException) {
            Err.e(Err.NETWORK_SSL, "TLS failure: $url", e)
            throw ApiException(Err.NETWORK_SSL, "TLS/HTTPS negotiation failed")
        } catch (_: IOException) {
            Err.e(Err.NETWORK_UNREACHABLE, "IO failure: $url")
            throw ApiException(Err.NETWORK_UNREACHABLE, "Connection failed")
        }
    }

    /** Runs a request and returns the body string, checking status / error body. */
    fun request(
        url: String,
        method: String = "GET",
        body: String? = null,
        apiKey: String? = null,
        authHeader: String = "Authorization",
        timeoutMs: Long = 60000,
        headers: Map<String, String> = emptyMap()
    ): String {
        execute(url, method, body, apiKey, authHeader, timeoutMs = timeoutMs, headers = headers).use { resp ->
            val text = resp.body?.string() ?: ""
            return when {
                resp.isSuccessful -> text
                resp.code == 401 || resp.code == 403 -> {
                    Err.e(Err.API_UNAUTHORIZED, "Auth failed (${resp.code}) for $url")
                    throw ApiException(Err.API_UNAUTHORIZED, "Authentication failed — check your API key")
                }
                resp.code == 429 -> {
                    Err.e(Err.API_RATE_LIMIT, "Rate limited (${resp.code})")
                    throw ApiException(Err.API_RATE_LIMIT, "Rate limit reached on the server")
                }
                resp.code >= 500 -> {
                    Err.e(Err.API_SERVER_ERROR, "Server error (${resp.code}) for $url")
                    throw ApiException(Err.API_SERVER_ERROR, "Server error (${resp.code})")
                }
                resp.code == 404 -> {
                    Err.e(Err.API_MALFORMED, "Not found (404): $url")
                    throw ApiException(Err.API_MALFORMED, "Endpoint not found — check the URL")
                }
                else -> {
                    Err.e(Err.NETWORK_HTTP, "HTTP ${resp.code} for $url. Body: ${safePreview(text)}")
                    throw ApiException(Err.NETWORK_HTTP, "Request failed (${resp.code})")
                }
            }
        }
    }

    /** Safe preview strips potential secrets before logging. */
    private fun safePreview(body: String): String =
        body.replace(Regex("(?i)(api[_-]?key|authorization|token)[\"']?\\s*[:=]\\s*[\"']?[\\w-]+"), "$1:****")

    fun streamRaw(
        url: String,
        body: String?,
        apiKey: String?,
        authHeader: String,
        timeoutMs: Long,
        headers: Map<String, String>
    ): Response = execute(url, "POST", body, apiKey, authHeader, keepAlive = false, timeoutMs = timeoutMs, headers = headers)
}

class ApiException(val code: String, val userMessage: String) : Exception(userMessage) {
    override val message: String get() = userMessage
}

object Json {
    val gson: Gson = Gson()
}