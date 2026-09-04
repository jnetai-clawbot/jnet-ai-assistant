package com.jnetai.assistant.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.jnetai.assistant.util.Err
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encryption utility built on the Android Keystore. A single AES-256 master
 * key is generated inside the hardware-backed Keystore and never leaves it.
 * All application secrets (API keys, connection credentials) are encrypted
 * at rest with this key. Plaintext is never stored.
 */
object CryptoManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY = "jnet_ai_master_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(MASTER_KEY, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                MASTER_KEY,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /** Encrypts plaintext. Returns "iv:Base64Ciphertext" — never plaintext. */
    fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val ivB64 = android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP)
            val ctB64 = android.util.Base64.encodeToString(cipherText, android.util.Base64.NO_WRAP)
            "$ivB64:$ctB64"
        } catch (t: Throwable) {
            Err.e(Err.CRYPTO_ERROR, "Encryption failed", t)
            throw t
        }
    }

    /** Decrypts a value previously produced by [encrypt]. */
    fun decrypt(stored: String): String {
        return try {
            val parts = stored.split(":", limit = 2)
            require(parts.size == 2) { "Malformed ciphertext" }
            val iv = android.util.Base64.decode(parts[0], android.util.Base64.NO_WRAP)
            val ct = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (t: Throwable) {
            Err.e(Err.CRYPTO_ERROR, "Decryption failed", t)
            throw t
        }
    }
}
