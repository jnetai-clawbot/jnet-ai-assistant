package com.jnetai.assistant.agent

import android.content.Context
import com.jnetai.assistant.data.model.SettingsManager

/**
 * Agent permission manager. Each capability can be enabled/disabled. Actions
 * are classified HAMRLESS / PRIVACY_SENSITIVE / DESTRUCTIVE and gated by a
 * configurable trust level:
 *   ask        — sensitive/destructive actions require user confirmation
 *   destructive— only destructive actions require confirmation
 *   trusted    — no confirmations
 *   disabled   — agent tools are off
 * Harmless actions never require confirmation.
 */
class PermissionManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("jnet_agent_perms", Context.MODE_PRIVATE)

    // Per-command shell permissions: "allow forever" is a stored hash set. There
    // is intentionally NO persisted "allow once" — once-only grants live only in
    // memory for the current run and are never reused without asking again.
    private val shellPrefs = context.getSharedPreferences("jnet_shell_perm", Context.MODE_PRIVATE)

    fun permitKind(kind: PermissionKind): Boolean = prefs.getBoolean("perm_${kind.name}", defaultFor(kind))

    fun setPermit(kind: PermissionKind, allowed: Boolean) {
        prefs.edit().putBoolean("perm_${kind.name}", allowed).apply()
    }

    // ---- Shell command permissions ----

    fun shellToolsEnabled(): Boolean = permitKind(PermissionKind.SHELL)

    fun isShellForeverAllowed(command: String): Boolean =
        shellPrefs.contains(hash(command.trim()))

    fun allowShellForever(command: String) {
        shellPrefs.edit().putBoolean(hash(command.trim()), true).apply()
    }

    fun revokeShellPermissions() {
        shellPrefs.edit().clear().apply()
    }

    fun countShellForeverAllowed(): Int = shellPrefs.all.size

    private fun hash(command: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(command.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun trustLevel(): String =
        prefs.getString("trust_level", "ask") ?: "ask"

    fun setTrustLevel(level: String) {
        prefs.edit().putString("trust_level", level).apply()
    }

    fun toolsEnabled(): Boolean = trustLevel() != "disabled"

    private fun defaultFor(kind: PermissionKind) = when (kind) {
        PermissionKind.CALCULATION, PermissionKind.DATA_QUERY -> true
        PermissionKind.DOCUMENTS -> true
        else -> false
    }

    /**
     * Whether a given tool may execute without user confirmation.
     * Harmless tools always run when enabled; destructive/sensitive tools
     * respect the trust level (except 'disabled' which allows nothing).
     */
    fun isAllowed(tool: AgentTool): Boolean {
        if (trustLevel() == "disabled") return false
        if (!permitKind(tool.permission)) return false
        if (tool.safety == SafetyLevel.HARMLESS) return true
        return trustLevel() == "trusted" ||
            (trustLevel() == "destructive" && tool.safety == SafetyLevel.PRIVACY_SENSITIVE)
    }

    /** True when this action requires an explicit user confirmation before running. */
    fun requiresConfirmation(tool: AgentTool): Boolean {
        if (trustLevel() == "disabled") return false
        if (!permitKind(tool.permission)) return false
        if (tool.safety == SafetyLevel.HARMLESS) return false
        return when (trustLevel()) {
            "trusted" -> false
            "destructive" -> tool.safety == SafetyLevel.DESTRUCTIVE
            else -> true
        }
    }

    fun isAuthorisedPath(path: String): Boolean =
        context.getSharedPreferences("jnet_agent_files", Context.MODE_PRIVATE).all.values.contains(path)
}