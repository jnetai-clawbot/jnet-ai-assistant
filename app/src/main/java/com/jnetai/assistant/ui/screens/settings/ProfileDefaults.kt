package com.jnetai.assistant.ui.screens.settings

import com.jnetai.assistant.data.model.ProviderType

/** Example/default endpoint + model used when selecting a provider type. Always editable. */
data class ProviderDefaults(val endpoint: String, val model: String)

object ProfileDefaults {
    fun defaultsFor(type: ProviderType): ProviderDefaults = when (type) {
        // Endpoint prefills always end with a trailing / — the providers strip it
        // before appending their path segment (/chat/completions, /api/chat etc.)
        // so a base URL like https://opencode.ai/zen/go/v1/ works without a custom
        // port. The separate Port field stays blank when https is specified.
        ProviderType.OPENCODE -> ProviderDefaults("https://opencode.ai/zen/go/v1/", "opencode-go")
        ProviderType.OLLAMA -> ProviderDefaults("http://localhost:11434/v1/", "")
        ProviderType.OPENAI_COMPAT -> ProviderDefaults("https://api.example.com/v1/", "")
        ProviderType.CUSTOM -> ProviderDefaults("http://SERVER_IP:PORT/v1/", "")
        ProviderType.LOCAL -> ProviderDefaults("", "")
        ProviderType.OTHER -> ProviderDefaults("", "")
    }
}