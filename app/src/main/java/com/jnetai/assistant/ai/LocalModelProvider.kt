package com.jnetai.assistant.ai

import com.jnetai.assistant.data.model.ConnectionProfile
import com.jnetai.assistant.util.Err
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.single

/**
 * Local on-device inference engine interface. The abstraction is designed so a
 * real llama.cpp / GGUF / MediaPipe native runtime can be plugged in later.
 * Currently the app ships without bundled native inference binaries (the APK
 * cannot reasonably bundle multi-GB GGUF engines); [LocalModelProvider]
 * therefore performs correct capability detection and returns a graceful,
 * informative error when local inference is requested — exactly the behaviour
 * required for platform-level limitations.
 */
interface LocalInferenceEngine {
    val available: Boolean
    val backendName: String
    suspend fun isModelLoaded(modelPath: String?): Boolean
    suspend fun chat(modelPath: String?, messages: List<ChatMessageInput>): ChatResult
}

class NativeLocalEngineNotPresent : LocalInferenceEngine {
    override val available: Boolean = false
    override val backendName: String = "llama.cpp (not bundled)"
    override suspend fun isModelLoaded(modelPath: String?): Boolean = false
    override suspend fun chat(modelPath: String?, messages: List<ChatMessageInput>): ChatResult {
        Err.w("Local inference requested but no native engine is bundled")
        throw LocalInferenceUnavailable(
            "No on-device inference engine is bundled in this build. " +
                "Use a LAN/remote Ollama or OpenAI-compatible profile instead."
        )
    }
}

class LocalInferenceUnavailable(message: String) : Exception(message)

class LocalModelProvider(
    private val profile: ConnectionProfile,
    private val engine: LocalInferenceEngine = NativeLocalEngineNotPresent()
) : AIProvider {
    override val capabilities = ProviderCapabilities(
        chat = engine.available,
        streaming = true,
        modelList = true
    )

    private fun notAvailable(): Nothing {
        Err.e(Err.LOCAL_MODEL_LOAD, "Local inference unavailable (${engine.backendName})")
        throw LocalInferenceUnavailable(
            "No on-device inference engine is bundled in this build. " +
                "Use a LAN/remote Ollama or OpenAI-compatible profile instead."
        )
    }

    override suspend fun chat(messages: List<ChatMessageInput>): ChatResult {
        if (!engine.available) notAvailable()
        return engine.chat(profile.model, messages)
    }

    override suspend fun chatStream(messages: List<ChatMessageInput>): Flow<ChatChunk> {
        if (!engine.available) notAvailable()
        val r = engine.chat(profile.model, messages)
        return flowOf(ChatChunk(r.text, done = true, completionTokens = r.completionTokens))
    }

    override suspend fun listModels(): List<ModelInfoLite> =
        listOf(ModelInfoLite(profile.model.ifBlank { "local-device" }))

    override suspend fun embed(texts: List<String>): List<List<Double>> = emptyList()

    override suspend fun testConnection(): ConnectionTestResult =
        if (engine.available) {
            ConnectionTestResult(true, 0, "Local engine ready")
        } else {
            ConnectionTestResult(false, 0, "No local inference engine bundled; use a remote profile.")
        }
}