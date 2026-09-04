package com.jnetai.assistant.network.dto

import com.google.gson.annotations.SerializedName

// ---- OpenAI-compatible chat completions ----
data class ChatMessageRole(val role: String, val content: String)

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageRole>,
    val stream: Boolean = false,
    val temperature: Double? = null,
    @SerializedName("top_p") val topP: Double? = null,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    @SerializedName("tools") val tools: List<Map<String, Any>>? = null,
    @SerializedName("tool_choice") val toolChoice: String? = null
)

data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null
)

data class Choice(
    val index: Int = 0,
    val message: ResponseMessage? = null,
    val delta: Delta? = null,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class ResponseMessage(
    val role: String = "",
    val content: String? = null,
    @SerializedName("tool_calls") val toolCalls: List<ToolCall>? = null
)

data class Delta(
    val role: String? = null,
    val content: String? = null,
    @SerializedName("tool_calls") val toolCalls: List<DeltaToolCall>? = null
)

data class ToolCall(
    val id: String? = null,
    val type: String? = null,
    val function: ToolFunction? = null
)

data class DeltaToolCall(
    val id: String? = null,
    val index: Int? = null,
    val function: ToolFunction? = null
)

data class ToolFunction(
    val name: String? = null,
    val arguments: String? = null
)

data class Usage(
    @SerializedName("prompt_tokens") val promptTokens: Int = 0,
    @SerializedName("completion_tokens") val completionTokens: Int = 0,
    @SerializedName("total_tokens") val totalTokens: Int = 0
)

// ---- Models list ----
data class ModelListResponse(
    val data: List<ModelInfo> = emptyList(),
    val models: List<ModelInfo> = emptyList()
)

data class ModelInfo(
    val id: String = "",
    val name: String? = null,
    val size: Long? = null
)

// ---- Embeddings ----
data class EmbeddingRequest(val model: String, val input: List<String>)

data class EmbeddingResponse(
    val data: List<EmbeddingData> = emptyList(),
    val model: String = ""
)

data class EmbeddingData(
    val embedding: List<Double> = emptyList(),
    val index: Int = 0
)

// ---- Audio transcription (OpenAI-compatible) ----
data class TranscriptionResponse(val text: String = "")