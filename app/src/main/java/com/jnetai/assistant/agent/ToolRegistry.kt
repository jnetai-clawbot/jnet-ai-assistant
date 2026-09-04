package com.jnetai.assistant.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.jnetai.assistant.data.model.AgentAction
import com.jnetai.assistant.rag.RagEngine
import com.jnetai.assistant.util.Err
import java.io.File

/**
 * Registry of agent tools with strict argument validation and permission gating.
 *
 * Security model: the agent can ONLY call tools listed here. There is no raw
 * shell/terminal tool. File access is limited to explicitly chosen paths /
 * SAF URIs. Sensitive tools are gated by [PermissionManager].
 */
class ToolRegistry(
    private val context: Context,
    private val permission: PermissionManager,
    private val onToolExecuted: (AgentAction) -> Unit,
    private val rag: RagEngine? = null
) {
    private val gson = com.google.gson.Gson()

    fun allTools(): List<AgentTool> = listOf(
        AgentTool(
            "calculate", "Perform a safe arithmetic calculation and return the numeric result.",
            listOf(ToolParam("expression", "string", "Arithmetic expression, digits and + - * / ( ) . ^ only")),
            PermissionKind.CALCULATION, SafetyLevel.HARMLESS
        ),
        AgentTool(
            "open_url", "Open a URL in the browser. Only http/https.",
            listOf(ToolParam("url", "string", "http(s) URL")),
            PermissionKind.NETWORK, SafetyLevel.PRIVACY_SENSITIVE
        ),
        AgentTool(
            "clipboard_read", "Read the current clipboard text.",
            emptyList(), PermissionKind.CLIPBOARD, SafetyLevel.PRIVACY_SENSITIVE
        ),
        AgentTool(
            "clipboard_write", "Write text to the clipboard.",
            listOf(ToolParam("text", "string", "text to copy")),
            PermissionKind.CLIPBOARD, SafetyLevel.PRIVACY_SENSITIVE
        ),
        AgentTool(
            "search_rag", "Search the local indexed documents and return relevant passages.",
            listOf(
                ToolParam("query", "string", "search query"),
                ToolParam("collection", "string", "collection name (optional)", required = false),
                ToolParam("limit", "integer", "max results (optional)", required = false)
            ),
            PermissionKind.DOCUMENTS, SafetyLevel.HARMLESS
        ),
        AgentTool(
            "open_settings", "Open an Android settings screen.",
            listOf(ToolParam("screen", "string", "wifi|bluetooth|storage|location|app_details")),
            PermissionKind.DEVICE_ACTIONS, SafetyLevel.HARMLESS
        ),
        AgentTool(
            "read_file", "Read a text file that the user explicitly selected for the agent. Path must be a stored authorised path.",
            listOf(ToolParam("path", "string", "authorised file path")),
            PermissionKind.FILES, SafetyLevel.PRIVACY_SENSITIVE
        ),
        AgentTool(
            "get_time", "Return the current date and time.",
            emptyList(), PermissionKind.DATA_QUERY, SafetyLevel.HARMLESS
        ),
        AgentTool(
            "device_info", "Return basic device information (model, Android version, storage hint).",
            emptyList(), PermissionKind.DATA_QUERY, SafetyLevel.HARMLESS
        )
    )

    /** Validates + executes a tool call. Returns a human-readable result string. */
    suspend fun execute(toolName: String, argsJson: String): String {
        val tool = allTools().find { it.name == toolName }
            ?: throw AgentException.ToolFailed("Unknown tool '$toolName'")
        val args = ToolArgs.validate(tool, argsJson)

        // Permission gating
        if (!permission.isAllowed(tool)) {
            throw AgentException.PermissionDenied("Tool '${tool.name}' requires permission '${tool.permission.name}'. Enable it in Settings → Agent.")
        }

        val action = AgentAction(tool = toolName, action = toolName, params = argsJson, authorised = true)
        onToolExecuted(action)

        return when (toolName) {
            "calculate" -> {
                val expr = (args["expression"] as? String) ?: throw AgentException.ToolFailed("expression required")
                evaluateExpression(expr)
            }
            "open_url" -> {
                val url = (args["url"] as? String) ?: throw AgentException.ToolFailed("url required")
                val uri = Uri.parse(url)
                if (uri.scheme != "http" && uri.scheme != "https") {
                    throw AgentException.ToolFailed("Only http/https URLs are allowed")
                }
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                "Opened URL $url"
            }
            "clipboard_read" -> {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: "(empty)"
                "Clipboard: $text"
            }
            "clipboard_write" -> {
                val text = (args["text"] as? String) ?: throw AgentException.ToolFailed("text required")
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("agent", text))
                "Clipboard updated"
            }
            "search_rag" -> {
                val rag = this.rag ?: throw AgentException.ToolFailed("RAG is not initialised")
                val query = (args["query"] as? String) ?: throw AgentException.ToolFailed("query required")
                val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 10) ?: 4
                val coll = (args["collection"] as? String)
                val collIds = if (coll != null) {
                    val allCols = com.jnetai.assistant.rag.flowsToList(rag.collections())
                    allCols.filter { it.name.contains(coll, true) }.map { it.id }
                } else null
                val result = rag.searchRag(query, collIds, null, limit = limit, hybrid = true)
                if (result.chunks.isEmpty()) "No matching documents found for: $query"
                else result.contextText.take(3000)
            }
            "open_settings" -> {
                val screen = (args["screen"] as? String) ?: "storage"
                val intent = when (screen) {
                    "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
                    "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    "storage" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                    "app_details" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    else -> Intent(Settings.ACTION_SETTINGS)
                }
                context.startActivity(intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                "Opened settings: $screen"
            }
            "read_file" -> {
                val path = (args["path"] as? String) ?: throw AgentException.ToolFailed("path required")
                val authorised = permission.isAuthorisedPath(path)
                    || context.getSharedPreferences("jnet_agent_files", Context.MODE_PRIVATE).all.values.contains(path)
                if (!authorised) throw AgentException.PermissionDenied("File '$path' is not an authorised agent path")
                val file = File(path)
                if (!file.exists() || !file.canRead()) throw AgentException.ToolFailed("File not readable")
                if (file.length() > 512_000) throw AgentException.ToolFailed("File too large to read (max 500KB)")
                file.readText().take(4000)
            }
            "get_time" -> java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())
            "device_info" -> {
                "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
            }
            else -> throw AgentException.ToolFailed("Unknown tool '${toolName}'")
        }
    }

    /** Find available agent files: paths the user has explicitly authorised. */
    fun authorisedFiles(): List<File> {
        val prefs = context.getSharedPreferences("jnet_agent_files", Context.MODE_PRIVATE)
        return prefs.all.values.filterIsInstance<String>().filter { File(it).exists() }.map { File(it) }
    }

    fun authoriseFile(path: String) {
        context.getSharedPreferences("jnet_agent_files", Context.MODE_PRIVATE).edit().putString(path, path).apply()
    }

    /** Safe expression evaluator — digits, ops, parentheses, decimal point, ^. */
    private fun evaluateExpression(expr: String): String {
        val cleaned = expr.trim()
        if (cleaned.isEmpty()) throw AgentException.ToolFailed("Expression is empty")
        if (!Regex("^[0-9+\\-*/().^\\s]+$").matches(cleaned)) {
            throw AgentException.ToolFailed("Expression may only contain numbers and + - * / ( ) . ^")
        }
        return try {
            val result = EvalShuntingYard.eval(cleaned)
            if (result == result.toLong().toDouble()) result.toLong().toString()
            else String.format(java.util.Locale.ROOT, "%.6f", result).trimEnd('0').trimEnd('.')
        } catch (_: Throwable) {
            throw AgentException.ToolFailed("Expression could not be evaluated")
        }
    }
}

/** Simple shunting-yard evaluator (no external deps). */
object EvalShuntingYard {
    fun eval(expr: String): Double {
        val tokens = tokenize(expr)
        val values = java.util.ArrayDeque<Double>()
        val ops = java.util.ArrayDeque<String>()
        fun applyTop() {
            val b = values.pollLast() ?: throw IllegalArgumentException()
            val a = values.pollLast() ?: throw IllegalArgumentException()
            val op = ops.pollLast() ?: throw IllegalArgumentException()
            values.addLast(when (op) {
                "+" -> a + b; "-" -> a - b; "*" -> a * b; "/" -> if (b == 0.0) throw IllegalArgumentException() else a / b
                "^" -> Math.pow(a, b)
                else -> throw IllegalArgumentException()
            })
        }
        for (t in tokens) {
            when {
                t == "(" -> ops.addLast(t)
                t == ")" -> {
                    while (ops.isNotEmpty() && ops.peekLast() != "(") applyTop()
                    ops.pollLast()
                }
                t in setOf("+", "-", "*", "/", "^") -> {
                    while (ops.isNotEmpty() && ops.peekLast() != "(" && precedence(ops.peekLast()!!) >= precedence(t)) applyTop()
                    ops.addLast(t)
                }
                else -> values.addLast(t.toDouble())
            }
        }
        while (ops.isNotEmpty()) applyTop()
        return values.pollLast() ?: throw IllegalArgumentException()
    }
    private fun precedence(op: String): Int = when (op) { "+", "-" -> 1; "*", "/" -> 2; "^" -> 3; else -> 0 }

    /** Normalises unary minus/plus by inserting a leading 0 where required. */
    private fun tokenize(e: String): List<String> {
        val cleaned = e.filterNot { it == ' ' }
        val out = java.util.ArrayDeque<String>()
        val num = StringBuilder()
        var prev: Char? = null
        for (c in cleaned) {
            if (c.isDigit() || c == '.') {
                num.append(c)
            } else {
                if (num.isNotEmpty()) { out.add(num.toString()); num.setLength(0) }
                if (c == '-' || c == '+') {
                    val unary = prev == null || prev in "(" || prev in "+-*/^"
                    if (unary) {
                        out.add("0")
                        out.add(c.toString())
                    } else {
                        out.add(c.toString())
                    }
                } else {
                    out.add(c.toString())
                }
                prev = c
            }
        }
        if (num.isNotEmpty()) out.add(num.toString())
        return out.toList()
    }
}