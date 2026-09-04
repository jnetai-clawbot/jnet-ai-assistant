package com.jnetai.assistant.data.model

import kotlinx.coroutines.flow.Flow
import com.jnetai.assistant.data.db.SettingsDao

/**
 * Central settings store backed by the Room app_settings table.
 * Everything important is configurable; this manager exposes typed accessors
 * with sensible defaults so the app is usable out of the box.
 */
class SettingsManager(private val dao: SettingsDao) {
    suspend fun get(key: String, fallback: String = ""): String =
        dao.get(key)?.value ?: fallback

    suspend fun set(key: String, value: String) {
        dao.put(AppSetting(key, value))
    }

    suspend fun getBool(key: String, fallback: Boolean = false): Boolean =
        get(key, fallback.toString()) == "true"

    suspend fun setBool(key: String, v: Boolean) = set(key, v.toString())

    suspend fun getInt(key: String, fallback: Int = 0): Int =
        get(key, fallback.toString()).toIntOrNull() ?: fallback

    suspend fun setInt(key: String, v: Int) = set(key, v.toString())

    suspend fun getLong(key: String, fallback: Long = 0): Long =
        get(key, fallback.toString()).toLongOrNull() ?: fallback

    suspend fun setLong(key: String, v: Long) = set(key, v.toString())

    suspend fun getDouble(key: String, fallback: Double = 0.0): Double =
        get(key, fallback.toString()).toDoubleOrNull() ?: fallback

    suspend fun setDouble(key: String, v: Double) = set(key, v.toString())

    suspend fun all(): List<AppSetting> = dao.getAll()

    suspend fun hasCompletedOnboarding(): Boolean = getBool("onboarding_done")
    suspend fun setOnboardingDone() = setBool("onboarding_done", true)

    // Appearance
    suspend fun getTheme(): String = get("appearance.theme", "dark") // dark|light|system
    suspend fun setTheme(v: String) = set("appearance.theme", v)
    suspend fun getCompactMode(): Boolean = getBool("appearance.compact")
    suspend fun setCompactMode(v: Boolean) = setBool("appearance.compact", v)

    // RAG
    suspend fun getChunkSize(): Int = getInt("rag.chunk_size", 1000)
    suspend fun setChunkSize(v: Int) = setInt("rag.chunk_size", v)
    suspend fun getChunkOverlap(): Int = getInt("rag.chunk_overlap", 150)
    suspend fun setChunkOverlap(v: Int) = setInt("rag.chunk_overlap", v)
    suspend fun getRetrievalCount(): Int = getInt("rag.retrieval_count", 8)
    suspend fun setRetrievalCount(v: Int) = setInt("rag.retrieval_count", v)
    suspend fun getHybridSearch(): Boolean = getBool("rag.hybrid", true)
    suspend fun setHybridSearch(v: Boolean) = setBool("rag.hybrid", v)

    // Voice
    suspend fun getSttProvider(): String = get("voice.stt", "android")
    suspend fun setSttProvider(v: String) = set("voice.stt", v)
    suspend fun getTtsProvider(): String = get("voice.tts", "android")
    suspend fun setTtsProvider(v: String) = set("voice.tts", v)
    suspend fun getTtsRate(): Float = get("voice.tts_rate", "1.0").toFloatOrNull() ?: 1.0f
    suspend fun setTtsRate(v: Float) = set("voice.tts_rate", v.toString())
    suspend fun getTtsPitch(): Float = get("voice.tts_pitch", "1.0").toFloatOrNull() ?: 1.0f
    suspend fun setTtsPitch(v: Float) = set("voice.tts_pitch", v.toString())
    suspend fun getAutoSpeak(): Boolean = getBool("voice.auto_speak", true)
    suspend fun setAutoSpeak(v: Boolean) = setBool("voice.auto_speak", v)

    // Local AI
    suspend fun getCpuThreads(): Int = getInt("local.threads", 4)
    suspend fun setCpuThreads(v: Int) = setInt("local.threads", v)
    suspend fun getGpuLayers(): Int = getInt("local.gpu_layers", 0)
    suspend fun setGpuLayers(v: Int) = setInt("local.gpu_layers", v)

    // Agent
    suspend fun getTrustLevel(): String = get("agent.trust", "ask")
    suspend fun setTrustLevel(v: String) = set("agent.trust", v)
    suspend fun getEnabledTools(): Set<String> =
        get("agent.tools", "").split(",").filter { it.isNotEmpty() }.toSet()
    suspend fun setEnabledTools(tools: Set<String>) = set("agent.tools", tools.joinToString(","))

    // Usage
    suspend fun getPricingConfigured(): Boolean = getBool("usage.pricing_configured")
    suspend fun setPricingConfigured(v: Boolean) = setBool("usage.pricing_configured", v)

    companion object {
        const val TRUST_ASK = "ask"
        const val TRUST_DESTRUCTIVE = "destructive"
        const val TRUSTED = "trusted"
        const val DISABLED = "disabled"
    }
}
