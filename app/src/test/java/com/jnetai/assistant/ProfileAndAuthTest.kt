package com.jnetai.assistant

import com.jnetai.assistant.network.ApiException
import com.jnetai.assistant.ai.OpenAiCompatProvider
import com.jnetai.assistant.data.model.AuthType
import com.jnetai.assistant.data.model.ConnectionProfile
import com.jnetai.assistant.data.model.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Unit tests for connection-profile validation logic and OpenAI-compatible
 * request serialisation. These run on the JVM (no Android framework).
 */
class ProfileAndAuthTest {

    @Test
    fun `auth enum maps to bearer`() {
        assertEquals(AuthType.BEARER, AuthType.valueOf("BEARER"))
        val req = AuthType.BEARER
        assertEquals("Bearer Token", req.display)
    }

    @Test
    fun `profile keeps independent fields`() {
        val p = ConnectionProfile(
            name = "Ollama Pi", providerType = ProviderType.OLLAMA,
            endpoint = "http://192.168.1.50", port = 11434,
            model = "llama3.1", temperature = 0.3, streaming = false
        )
        assertEquals(11434, p.port)
        assertEquals("http://192.168.1.50", p.endpoint)
        assertEquals("llama3.1", p.model)
        assertEquals(false, p.streaming)
        assertTrue(p.id == 0L)
    }

    @Test
    fun `malformed numeric limits fall back to zero in UI logic`() {
        fun parse(v: String) = v.toLongOrNull() ?: 0L
        assertEquals(0L, parse("abc"))
        assertEquals(5000L, parse("5000"))
    }

    @Test
    fun `error codes are stable and usable for user messaging`() {
        val e = ApiException("E0201", "Authentication failed — check your API key")
        assertEquals("E0201", e.code)
        assertTrue(!e.userMessage.contains("secret"))
    }

    @Test
    fun `custom headers serialise as a JSON map`() {
        val gson = Gson()
        val headers = mapOf("X-Tenant" to "acme")
        val json = gson.toJson(headers)
        val parsed: Map<String, String> = gson.fromJson(json, object : TypeToken<Map<String, String>>() {}.type)
        assertEquals("acme", parsed["X-Tenant"])
    }
}