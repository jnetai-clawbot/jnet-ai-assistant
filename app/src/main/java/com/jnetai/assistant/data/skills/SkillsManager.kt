package com.jnetai.assistant.data.skills

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jnetai.assistant.util.Err
import java.io.File

/** A single uploaded skill (instructions the AI should follow in every mode). */
data class SkillInfo(
    val name: String,
    val enabled: Boolean,
    val addedAt: Long,
    val sizeBytes: Int
)

/**
 * Local skills store. Skills are Markdown-format instruction files stored in
 * the app's private files dir (filesDir/skills, one .md file per skill). They
 * can be uploaded from a file, created from pasted text, enabled/disabled and
 * removed. Enabled skills are injected as a system message into EVERY mode
 * (chat, RAG, hybrid, agent and voice). The UI state (enabled flag, timestamp)
 * lives in a private SharedPreferences file keyed by skill name.
 */
class SkillsManager(private val context: Context) {

    private val dir = File(context.filesDir, "skills").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("jnet_skills", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val metaType = object : TypeToken<HashMap<String, SkillMeta>>() {}.type

    private data class SkillMeta(val enabled: Boolean = true, val addedAt: Long = System.currentTimeMillis())

    fun list(): List<SkillInfo> {
        val files = dir.listFiles { f -> f.isFile && f.extension.equals("md", true) }.orEmpty()
        val meta = loadMeta()
        return files.sortedByDescending { it.lastModified() }.map { f ->
            val m = meta[f.nameWithoutExtension]
            SkillInfo(
                name = f.nameWithoutExtension,
                enabled = m?.enabled ?: true,
                addedAt = m?.addedAt ?: f.lastModified(),
                sizeBytes = f.length().toInt()
            )
        }
    }

    /** Uploads a skill from a picked file (text/markdown). Name = file name. */
    fun uploadFromUri(uri: Uri): String {
        val resolver = context.contentResolver
        val name = displayName(uri).usernameSafe()
        if (name.isBlank()) throw IllegalStateException("Could not read the skill file name")
        val content = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("Could not read the skill file")
        return add(name, content)
    }

    /** Creates/overwrites a skill from raw text. Returns the stored name. */
    fun add(rawName: String, content: String): String {
        val name = rawName.trim().usernameSafe()
        if (name.isEmpty()) throw IllegalArgumentException("Skill name cannot be empty")
        if (content.isBlank()) throw IllegalArgumentException("Skill content is empty")
        val file = File(dir, "$name.md")
        file.writeText(content)
        val meta = loadMeta()
        meta[name] = SkillMeta(enabled = meta[name]?.enabled ?: true, addedAt = System.currentTimeMillis())
        saveMeta(meta)
        Err.i("Skill '$name' saved (${content.length} chars)")
        return name
    }

    fun setEnabled(name: String, enabled: Boolean) {
        val meta = loadMeta()
        val existing = meta[name] ?: SkillMeta()
        meta[name] = existing.copy(enabled = enabled)
        saveMeta(meta)
        Err.i("Skill '$name' ${if (enabled) "enabled" else "disabled"}")
    }

    fun remove(name: String) {
        File(dir, "$name.md").delete()
        val meta = loadMeta()
        meta.remove(name)
        saveMeta(meta)
        Err.i("Skill '$name' removed")
    }

    /**
     * Concatenates every ENABLED skill into one block for injection into the
     * model context. Capped so a large library can't blow up a prompt.
     */
    fun enabledContent(limit: Int = 60_000): String {
        val files = dir.listFiles { f -> f.isFile && f.extension.equals("md", true) }.orEmpty()
            .sortedBy { it.name }
        val meta = loadMeta()
        val sb = StringBuilder()
        var total = 0
        for (f in files) {
            val m = meta[f.nameWithoutExtension]
            if (m?.enabled == false) continue
            val text = runCatching { f.readText(Charsets.UTF_8) }.getOrElse { "" }.trim()
            if (text.isEmpty()) continue
            sb.append("## Skill: ").append(f.nameWithoutExtension).append("\n\n").append(text).append("\n\n")
            total += text.length
            if (total >= limit) break
        }
        return sb.toString().trim()
    }

    fun countEnabled(): Int = list().count { it.enabled }

    private fun loadMeta(): HashMap<String, SkillMeta> =
        try {
            gson.fromJson<HashMap<String, SkillMeta>>(prefs.getString("meta", null), metaType) ?: HashMap()
        } catch (t: Throwable) {
            Err.e(Err.SKILLS_ERROR, "Skill meta corrupt; resetting", t)
            HashMap()
        }

    private fun saveMeta(meta: Map<String, SkillMeta>) {
        prefs.edit().putString("meta", gson.toJson(meta)).apply()
    }

    private fun displayName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "skill"
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx)?.let { name = it }
                }
            }
        } catch (_: Throwable) {}
        return name.removeSuffix(".md").removeSuffix(".txt")
    }

    private fun String.usernameSafe(): String =
        replace(Regex("[^A-Za-z0-9 _\\-]"), "_").trim().take(60)
}