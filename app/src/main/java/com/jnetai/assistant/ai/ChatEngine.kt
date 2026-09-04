package com.jnetai.assistant.ai

import com.jnetai.assistant.util.Err

/** Stream/engine events emitted to the UI layer. */
sealed class EngineEvent {
    data class TextDelta(val text: String) : EngineEvent()
    data class Done(
        val fullText: String,
        val promptTokens: Long,
        val completionTokens: Long,
        val sources: List<com.jnetai.assistant.data.model.ChatSource>
    ) : EngineEvent()
    data class Failed(val code: String, val message: String) : EngineEvent()
    data class ToolRequested(val calls: List<ToolCallStub>) : EngineEvent()
    data class AgentResult(val summary: String) : EngineEvent()
}

data class SendRequest(
    val provider: AIProvider,
    val messages: List<ChatMessageInput>,
    val systemPrompt: String = "",
    val maxTokens: Int = 2048,
    val stream: Boolean = true
)

/**
 * Thin engine wrapper around an [AIProvider]. Keeps provider details out of
 * the UI while supporting streaming, tool calls and graceful failure events.
 */
class ChatEngine {
    suspend fun run(request: SendRequest, onDelta: suspend (String) -> Unit): EngineEvent = try {
        if (request.stream) {
            val sb = StringBuilder()
            var pt = 0L; var ct = 0L
            val toolStubs = mutableListOf<ToolCallStub>()
            request.provider.chatStream(request.messages).collect { chunk ->
                if (chunk.text.isNotEmpty()) {
                    sb.append(chunk.text)
                    onDelta(chunk.text)
                }
                pt = maxOf(pt, chunk.promptTokens)
                ct = maxOf(ct, chunk.completionTokens)
                chunk.toolCalls?.let { toolStubs += it }
                if (chunk.done && toolStubs.isNotEmpty()) {
                    // tools requested mid-stream; caller inspects via event
                }
            }
            val full = sb.toString()
            if (toolStubs.isNotEmpty()) {
                EngineEvent.ToolRequested(toolStubs)
            } else {
                EngineEvent.Done(full, pt, ct, emptyList())
            }
        } else {
            val result = request.provider.chat(request.messages)
            onDelta(result.text)
            if (result.toolCalls.isNotEmpty()) {
                EngineEvent.ToolRequested(result.toolCalls)
            } else {
                EngineEvent.Done(result.text, result.promptTokens, result.completionTokens, emptyList())
            }
        }
    } catch (e: com.jnetai.assistant.network.ApiException) {
        Err.e(e.code, "Chat failed: ${e.userMessage}", e)
        EngineEvent.Failed(e.code, e.userMessage)
    } catch (e: LocalInferenceUnavailable) {
        Err.e(Err.LOCAL_MODEL_LOAD, e.message ?: "Local inference unavailable", e)
        EngineEvent.Failed(Err.LOCAL_MODEL_LOAD, e.message ?: "Local inference unavailable")
    } catch (t: Throwable) {
        Err.e(Err.API_STREAM_ERROR, "Unexpected chat failure", t)
        EngineEvent.Failed(Err.API_STREAM_ERROR, "Unexpected error: ${t.message ?: "unknown"}")
    }
}