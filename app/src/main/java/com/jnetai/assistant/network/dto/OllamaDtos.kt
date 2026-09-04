package com.jnetai.assistant.network.dto

import com.google.gson.annotations.SerializedName

// ---- Ollama API ----
data class OllamaChatMessage(val role: String, val content: String)

data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatMessage>,
    val stream: Boolean = false,
    val options: Map<String, Any>? = null,
    @SerializedName("keep_alive") val keepAlive: Int? = null
)

data class OllamaChatResponse(
    val model: String = "",
    val message: OllamaResponseMessage? = null,
    val response: String? = null,
    val done: Boolean = true
)

data class OllamaResponseMessage(val role: String = "", val content: String = "")

data class OllamaModelsResponse(val models: List<OllamaModel> = emptyList())

data class OllamaModel(
    val name: String = "",
    val model: String = "",
    @SerializedName("modified_at") val modifiedAt: String = "",
    val size: Long = 0,
    val digest: String = ""
)

data class OllamaEmbedRequest(
    val model: String,
    val input: String,
    val prompt: String? = null
)

data class OllamaEmbedResponse(val embedding: List<Double> = emptyList())

// ---- Health/status ----
data class StatusResponse(val status: String = "", val message: String = "")