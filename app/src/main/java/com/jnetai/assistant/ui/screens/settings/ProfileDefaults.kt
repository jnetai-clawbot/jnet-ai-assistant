package com.jnetai.assistant.ui.screens.settings

import com.jnetai.assistant.data.model.ProviderType

/** Example/default endpoint + model used when selecting a provider type. Always editable. */
data class ProviderDefaults(val endpoint: String, val model: String)

object ProfileDefaults {
    fun defaultsFor(type: ProviderType): ProviderDefaults = when (type) {
        ProviderType.OPENCODE -> ProviderDefaults("https://opencode.ai/zen/go/v1", "opencode-go")
        ProviderType.OLLAMA -> ProviderDefaults("http://localhost:11434/v1", "")
        ProviderType.OPENAI_COMPAT -> ProviderDefaults("https://api.example.com/v1", "")
        ProviderType.CUSTOM -> ProviderDefaults("http://SERVER_IP:PORT/v1", "")
        ProviderType.LOCAL -> ProviderDefaults("", "")
        ProviderType.OTHER -> ProviderDefaults("", "")
    }
}