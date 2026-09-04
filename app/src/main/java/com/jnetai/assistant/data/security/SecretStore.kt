package com.jnetai.assistant.data.security

import android.content.Context
import com.jnetai.assistant.util.Err
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Stores API keys / connection secrets encrypted at rest behind the Android
 * Keystore master key (see [CryptoManager]). Secret values are never written
 * to disk as plaintext and never logged. Callers interact via an opaque id.
 */
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("jnet_secure_secrets", Context.MODE_PRIVATE)
    private val cache = ConcurrentHashMap<String, String>()

    /** Stores a secret, returns an opaque reference id. Never returns plaintext. */
    fun put(secret: String): String {
        val id = UUID.randomUUID().toString()
        return try {
            prefs.edit().putString(id, CryptoManager.encrypt(secret)).apply()
            cache[id] = secret
            id
        } catch (t: Throwable) {
            Err.e(Err.CRYPTO_ERROR, "Failed to store secret", t)
            ""
        }
    }

    fun get(refId: String): String? {
        if (refId.isEmpty()) return null
        cache[refId]?.let { return it }
        return try {
            val stored = prefs.getString(refId, null) ?: return null
            val plain = CryptoManager.decrypt(stored)
            cache[refId] = plain
            plain
        } catch (t: Throwable) {
            Err.e(Err.CRYPTO_ERROR, "Failed to read secret", t)
            null
        }
    }

    fun delete(refId: String) {
        if (refId.isEmpty()) return
        prefs.edit().remove(refId).apply()
        cache.remove(refId)
    }
}
