package com.jnetai.assistant.util

import android.util.Log

/**
 * Persistent, always-on debug/diagnostic logger used throughout the app.
 * Every failure produces a stable error code (e.g. E0001) plus a detailed
 * diagnostic trail so future bugs can be traced quickly. This system is
 * intentionally permanent — do not remove it.
 */
object Err {
    private const val TAG = "JNetAI"

    // Central error-code registry. Every error codepath has a unique code.
    const val PROFILE_INVALID_URL = "E0001"
    const val PROFILE_BAD_PORT = "E0002"
    const val NETWORK_TIMEOUT = "E0101"
    const val NETWORK_UNREACHABLE = "E0102"
    const val NETWORK_HTTP = "E0103"
    const val NETWORK_SSL = "E0104"
    const val API_UNAUTHORIZED = "E0201"
    const val API_RATE_LIMIT = "E0202"
    const val API_SERVER_ERROR = "E0203"
    const val API_MALFORMED = "E0204"
    const val API_STREAM_ERROR = "E0205"
    const val DB_ERROR = "E0301"
    const val CRYPTO_ERROR = "E0401"
    const val KEYSTORE_ERROR = "E0402"
    const val LOCK_PIN_ERROR = "E0403"
    const val DOC_PARSE_ERROR = "E0501"
    const val DOC_UNSUPPORTED = "E0502"
    const val RAG_EMBED_ERROR = "E0503"
    const val RAG_INDEX_ERROR = "E0504"
    const val STT_UNAVAILABLE = "E0601"
    const val STT_PERMISSION = "E0602"
    const val TTS_UNAVAILABLE = "E0603"
    const val AGENT_INVALID_ARGS = "E0701"
    const val AGENT_PERMISSION = "E0702"
    const val LOCAL_MODEL_LOAD = "E0801"
    const val LOCAL_MODEL_MEM = "E0802"
    const val BACKUP_ERROR = "E0901"
    const val USAGE_LIMIT = "E1001"

    private val codes = setOf(
        PROFILE_INVALID_URL, PROFILE_BAD_PORT, NETWORK_TIMEOUT, NETWORK_UNREACHABLE,
        NETWORK_HTTP, NETWORK_SSL, API_UNAUTHORIZED, API_RATE_LIMIT, API_SERVER_ERROR,
        API_MALFORMED, API_STREAM_ERROR, DB_ERROR, CRYPTO_ERROR, KEYSTORE_ERROR,
        LOCK_PIN_ERROR, DOC_PARSE_ERROR, DOC_UNSUPPORTED, RAG_EMBED_ERROR, RAG_INDEX_ERROR,
        STT_UNAVAILABLE, STT_PERMISSION, TTS_UNAVAILABLE, AGENT_INVALID_ARGS,
        AGENT_PERMISSION, LOCAL_MODEL_LOAD, LOCAL_MODEL_MEM, BACKUP_ERROR, USAGE_LIMIT
    )

    fun assertValid(code: String) {
        require(code in codes) { "Unknown error code: $code — register it in Err.kt" }
    }

    fun d(message: String) = Log.d(TAG, message)
    fun i(message: String) = Log.i(TAG, message)
    fun w(message: String) = Log.w(TAG, message)

    fun e(code: String, message: String, t: Throwable? = null) {
        assertValid(code)
        Log.e(TAG, "[$code] $message", t)
    }

    fun isDebug(): Boolean = Log.isLoggable(TAG, Log.DEBUG)
}
