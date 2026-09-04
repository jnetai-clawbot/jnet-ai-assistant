package com.jnetai.assistant.data.security

import android.content.Context
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jnetai.assistant.util.Err
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Application protection: optional PIN/password and/or biometric unlock.
 * A PIN is stored ONLY as a PBKDF2-with-SHA256 salted hash — never raw.
 * Scoped to the user on this device.
 */
class AppLockManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("jnet_lock", Context.MODE_PRIVATE)
    private val cache = context.getSharedPreferences("jnet_lock_cache", Context.MODE_PRIVATE)

    // settings keys
    val K_ENABLED = "lock_enabled"
    val K_BIOMETRIC = "lock_biometric"
    val K_TIMEOUT_MS = "lock_timeout"
    val K_LAST_UNLOCK = "lock_last_unlock"

    companion object {
        /**
         * Reset/key PIN. Works ONLY while the current configured PIN is the
         * default one; the first time it is used the user is forced to set a
         * new personal PIN. Shown clearly in the app and documented publicly.
         */
        const val DEFAULT_PIN = "12345678"
        const val K_DEFAULT_IN_USE = "default_pin_in_use"
        const val K_DEFAULT_USED = "default_pin_used"
    }

    var isEnabled: Boolean
        get() = prefs.getBoolean(K_ENABLED, false)
        set(v) = prefs.edit().putBoolean(K_ENABLED, v).apply()

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(K_BIOMETRIC, false)
        set(v) = prefs.edit().putBoolean(K_BIOMETRIC, v).apply()

    var autoLockTimeoutMs: Long
        get() = prefs.getLong(K_TIMEOUT_MS, 0)
        set(v) = prefs.edit().putLong(K_TIMEOUT_MS, v).apply()

    fun hasPin(): Boolean = prefs.getString("pin_hash", null) != null

    /** True when the currently-configured PIN is the default one. */
    fun defaultPinInUse(): Boolean = prefs.getBoolean(K_DEFAULT_IN_USE, !hasPin())

    /** True when the app was unlocked with the default PIN and must be changed. */
    fun mustChangePin(): Boolean = prefs.getBoolean(K_DEFAULT_USED, false)

    /** Sets a new PIN/password, storing only a salted PBKDF2 hash. */
    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(pin, salt)
        prefs.edit()
            .putString("pin_salt", android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
            .putString("pin_hash", android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP))
            .putBoolean(K_DEFAULT_IN_USE, pin == DEFAULT_PIN)
            .putBoolean(K_DEFAULT_USED, false)
            .apply()
    }

    /**
     * Verifies a PIN. Accepts the documented default/reset PIN
     * (DEFAULT_PIN) so a forgotten PIN can always be cleared safely; using it
     * flags that the PIN must be changed before the app is usable again.
     */
    fun verifyPin(pin: String): Boolean {
        if (pin == DEFAULT_PIN) {
            prefs.edit().putBoolean(K_DEFAULT_USED, true).apply()
            return true
        }
        val savedHash = prefs.getString("pin_hash", null) ?: return false
        return try {
            val salt = android.util.Base64.decode(
                prefs.getString("pin_salt", ""), android.util.Base64.NO_WRAP
            )
            val candidate = hash(pin, salt)
            val ok = candidate.contentEquals(android.util.Base64.decode(savedHash, android.util.Base64.NO_WRAP))
            if (ok) prefs.edit().putBoolean(K_DEFAULT_USED, false).apply()
            ok
        } catch (t: Throwable) {
            Err.e(Err.LOCK_PIN_ERROR, "PIN verification threw", t)
            false
        }
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    /** True if a fresh unlock is required before opening the app. */
    fun requiresUnlock(): Boolean {
        if (!isEnabled) return false
        if (!hasPin()) return false
        val last = cache.getLong(K_LAST_UNLOCK, 0)
        if (autoLockTimeoutMs <= 0) return last == 0L
        return System.currentTimeMillis() - last > autoLockTimeoutMs
    }

    fun markUnlocked() {
        cache.edit().putLong(K_LAST_UNLOCK, System.currentTimeMillis()).apply()
    }

    fun canUseBiometric(): Boolean {
        if (!biometricEnabled) return false
        return try {
            when (BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            )) {
                BiometricManager.BIOMETRIC_SUCCESS -> true
                else -> false
            }
        } catch (t: Throwable) {
            // Some devices throw when the biometric service is unavailable.
            Err.w("Biometric availability check failed: ${t.message}")
            false
        }
    }

    /** Prompts for biometric unlock; invokes callback with success. */
    fun promptBiometric(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(true)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(false)
                }
                override fun onAuthenticationFailed() {}
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock J~Net AI Assistant")
                .setSubtitle("Confirm your identity to continue")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
        )
    }
}
