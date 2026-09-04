package com.jnetai.assistant.agent

import com.google.gson.Gson

/** Safety classification for agent tools. */
enum class SafetyLevel { HARMLESS, PRIVACY_SENSITIVE, DESTRUCTIVE }

enum class PermissionKind {
    DOCUMENTS, MICROPHONE, NOTIFICATIONS, CLIPBOARD, ACCESSIBILITY,
    NETWORK, FILES, DEVICE_ACTIONS, CALCULATION, DATA_QUERY, AI_ENDPOINT
}

data class ToolParam(val name: String, val type: String, val description: String, val required: Boolean = true)

data class AgentTool(
    val name: String,
    val description: String,
    val parameters: List<ToolParam>,
    val permission: PermissionKind,
    val safety: SafetyLevel
) {
    fun toOpenAiSpec(): Map<String, Any> = mapOf(
        "type" to "function",
        "function" to mapOf(
            "name" to name,
            "description" to description,
            "parameters" to mapOf(
                "type" to "object",
                "properties" to parameters.associate { p ->
                    p.name to mapOf("type" to p.type, "description" to p.description)
                },
                "required" to parameters.filter { it.required }.map { it.name }
            )
        )
    )
}

/** Validates tool arguments strictly before execution. */
object ToolArgs {
    private val gsonType = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type

    fun validate(tool: AgentTool, argsJson: String): Map<String, Any> {
        val gson = Gson()
        val args: Map<String, Any> = try {
            gson.fromJson(argsJson, gsonType) ?: emptyMap()
        } catch (_: Throwable) {
            throw AgentException.AgentArgs("Args for ${tool.name} are not valid JSON")
        }
        for (p in tool.parameters) {
            if (p.required && !args.containsKey(p.name)) {
                throw AgentException.AgentArgs("Missing required parameter '${p.name}' for ${tool.name}")
            }
            args[p.name]?.let { value ->
                when (p.type) {
                    "string" -> require(value is String) { "${p.name} must be a string" }
                    "number" -> require(value is Number) { "${p.name} must be a number" }
                    "integer" -> require(value is Number) { "${p.name} must be an integer" }
                    "boolean" -> require(value is Boolean) { "${p.name} must be a boolean" }
                }
            }
        }
        return args
    }
}

sealed class AgentException(message: String) : Exception(message) {
    class AgentArgs(message: String) : AgentException(message)
    class PermissionDenied(message: String) : AgentException(message)
    class ToolFailed(message: String) : AgentException(message)
}