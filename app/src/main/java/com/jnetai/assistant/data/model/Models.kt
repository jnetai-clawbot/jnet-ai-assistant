package com.jnetai.assistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An AI connection profile. Each profile is fully independent: endpoint, port,
 * API key, model, headers, parameters, token limits, TLS settings etc.
 */
@Entity(tableName = "profiles")
data class ConnectionProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val providerType: ProviderType = ProviderType.OPENCODE,
    val endpoint: String = "",
    val port: Int = 0,
    val apiKeyRef: String = "",          // key into EncryptedKeyStore
    val model: String = "",
    val organization: String = "",
    val authType: AuthType = AuthType.BEARER,
    val timeoutMs: Long = 60000,
    val maxTokens: Int = 2048,
    val temperature: Double = 0.7,
    val topP: Double = 1.0,
    val streaming: Boolean = true,
    val tlsEnabled: Boolean = true,
    val customHeaders: String = "{}",    // JSON map
    val systemPrompt: String = "",
    val enabled: Boolean = true,
    // token usage / limits
    val dailyTokenLimit: Long = 0,
    val monthlyTokenLimit: Long = 0,
    val warningThreshold: Long = 0,
    val maxContextSize: Int = 8192,
    val maxRagChunks: Int = 8,
    val maxDocumentContext: Int = 4000,
    val maxHistory: Int = 20,
    /** OpenCode session id sent as the x-opencode-session header (auto-generated per profile). */
    val opencodeSession: String = ""
)

enum class ProviderType(val display: String) {
    OPENCODE("OpenCode"),
    OPENAI_COMPAT("OpenAI Compatible"),
    OLLAMA("Ollama"),
    LOCAL("Local Device"),
    CUSTOM("Custom Server"),
    OTHER("Other")
}

enum class AuthType(val display: String) {
    NONE("None"),
    BEARER("Bearer Token"),
    API_KEY("API Key"),
    BASIC("Basic Auth")
}

enum class ChatMode(val display: String) {
    NORMAL("Normal"),
    RAG("RAG"),
    HYBRID("Hybrid"),
    AGENT("Agent"),
    VOICE("Voice")
}

@Entity(tableName = "collections")
data class DocCollection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "documents")
data class IndexedDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val collectionId: Long = 0,
    val name: String = "",
    val mimeType: String = "",
    val uri: String = "",            // content uri/path reference
    val fileHash: String = "",
    val sizeBytes: Long = 0,
    val pageCount: Int = 0,
    val status: IndexStatus = IndexStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val embeddingModel: String = ""
)

enum class IndexStatus(val display: String) {
    PENDING("Pending"),
    INDEXING("Indexing"),
    READY("Ready"),
    FAILED("Failed")
}

@Entity(tableName = "chunks")
data class Chunk(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long = 0,
    val content: String = "",
    val page: Int = 0,
    val section: String = "",
    val startOffset: Int = 0,
    val embeddingRef: String = "",      // id of embedding record
    val hash: String = ""
)

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val profileId: Long = 0,
    val model: String = "",
    val mode: ChatMode = ChatMode.NORMAL,
    val collectionId: Long = 0,
    val totalTokens: Long = 0
)

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long = 0,
    val role: String = "user",          // user | assistant | system
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val sources: String = "[]",         // JSON array of ChatSource
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val providerName: String = ""
)

data class ChatSource(val documentName: String = "", val page: Int = 0, val section: String = "")

@Entity(tableName = "activity")
data class ActivityRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String = "",              // ai/rag/index/stt/tts/agent/error
    val summary: String = "",
    val detail: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val tokens: Long = 0
)

@Entity(tableName = "usage")
data class UsageRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long = 0,
    val model: String = "",
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val category: String = "",          // chat/rag/agent/voice
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "agent_actions")
data class AgentAction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tool: String = "",
    val action: String = "",
    val params: String = "{}",
    val authorised: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "models")
data class LocalModel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val fileUri: String = "",
    val sizeBytes: Long = 0,
    val contextLength: Int = 0,
    val quantisation: String = "",
    val backend: String = "llama.cpp",
    val threads: Int = 4,
    val gpuLayers: Int = 0,
    val active: Boolean = false,
    val loaded: Boolean = false,
    val memoryEstimateMb: Int = 0
)

@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val key: String = "",
    val value: String = ""
)
