package com.jnetai.assistant.ai

import com.jnetai.assistant.data.model.ConnectionProfile
import com.jnetai.assistant.data.model.ProviderType

/**
 * Creates the correct [AIProvider] for a connection profile. Adding a new
 * backend only requires a new branch here — the chat system is untouched.
 */
class DefaultProviderFactory : ProviderFactory {
    override fun create(profile: ConnectionProfile, apiKeyResolver: () -> String?): AIProvider =
        when (profile.providerType) {
            ProviderType.OLLAMA -> OllamaProvider(profile, apiKeyResolver)
            ProviderType.LOCAL -> LocalModelProvider(profile)
            else -> OpenAiCompatProvider(profile, apiKeyResolver)
        }
}