package com.jnetai.assistant.ai

import com.jnetai.assistant.data.model.ConnectionProfile
import kotlinx.coroutines.flow.Flow

/** Result of a streaming chat completion. */
data class ChatChunk(
    val text: String,
    val done: Boolean = false,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val toolCalls: List<ToolCallStub> = emptyList()
)

data class ToolCallStub(val id: String = "", val name: String = "", val arguments: String = "{}")

data class ChatResult(
    val text: String,
    val promptTokens: Long,
    val completionTokens: Long,
    val toolCalls: List<ToolCallStub> = emptyList()
)

data class ProviderCapabilities(
    val chat: Boolean = true,
    val streaming: Boolean = true,
    val modelList: Boolean = false,
    val embeddings: Boolean = false,
    val transcription: Boolean = false
)

data class ModelInfoLite(val id: String)

data class ConnectionTestResult(
    val ok: Boolean,
    val latencyMs: Long,
    val message: String,
    val models: List<String> = emptyList(),
    val authOk: Boolean = false,
    val modelOk: Boolean = false
)

/**
 * Common interface for every AI backend. Providers expose their capabilities
 * dynamically so the app adapts accordingly. Adding a new provider means
 * implementing this interface — the chat/system never changes.
 */
interface AIProvider {
    val capabilities: ProviderCapabilities
    suspend fun chat(messages: List<ChatMessageInput>): ChatResult
    suspend fun chatStream(messages: List<ChatMessageInput>): Flow<ChatChunk>
    suspend fun listModels(): List<ModelInfoLite>
    suspend fun embed(texts: List<String>): List<List<Double>>
    suspend fun testConnection(): ConnectionTestResult
}

data class ChatMessageInput(val role: String, val content: String)

/** Creates a concrete provider for a given profile. */
interface ProviderFactory {
    fun create(profile: ConnectionProfile, apiKeyResolver: () -> String?): AIProvider
}