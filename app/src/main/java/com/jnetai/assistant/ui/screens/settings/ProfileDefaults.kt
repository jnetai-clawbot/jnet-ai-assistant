package com.jnetai.assistant.ui.screens.settings

import com.jnetai.assistant.data.model.ProviderType

/** Example/default endpoint + model (+ streaming default) used when selecting a provider type. Always editable. */
data class ProviderDefaults(val endpoint: String, val model: String, val streaming: Boolean = true)

object ProfileDefaults {
    fun defaultsFor(type: ProviderType): ProviderDefaults = when (type) {
        // Endpoint prefills always end with a trailing / — the providers strip it
        // before appending their path segment (/chat/completions, /api/chat etc.)
        // so a base URL like https://opencode.ai/zen/go/v1/ works without a custom
        // port. The separate Port field stays blank when https is specified.
        // deepseek-v4-flash is the default OpenCode model and streaming is OFF by
        // default for stability (transient mid-stream errors no longer interrupt).
        ProviderType.OPENCODE -> ProviderDefaults("https://opencode.ai/zen/go/v1/", "deepseek-v4-flash", streaming = false)
        ProviderType.OLLAMA -> ProviderDefaults("http://localhost:11434/v1/", "", streaming = true)
        ProviderType.OPENAI_COMPAT -> ProviderDefaults("https://api.example.com/v1/", "", streaming = true)
        ProviderType.CUSTOM -> ProviderDefaults("http://SERVER_IP:PORT/v1/", "", streaming = true)
        ProviderType.LOCAL -> ProviderDefaults("", "", streaming = true)
        ProviderType.OTHER -> ProviderDefaults("", "", streaming = true)
    }
}