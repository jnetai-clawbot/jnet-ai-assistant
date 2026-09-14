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

/** Full skill payload (file content + meta) used for backup/restore. */
data class SkillData(
    val name: String,
    val content: String,
    val enabled: Boolean,
    val addedAt: Long
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

    /**
     * Built-in skill shipped with the app and enabled by default. It documents
     * the free internet-search capability so the AI knows to lean on fresh web
     * results in the voice assistant, chat and chat RAG modes (and everywhere
     * else) whenever the Settings → "Enable Internet Search" permission allows.
     */
    companion object {
        const val DEFAULT_SKILL_NAME = "Internet Search"
        private const val PREFS_DEFAULT_SEEDED = "default_seeded"
    }

    /**
     * Creates the default "Internet Search" skill once (guarded by a prefs flag
     * so removing it later is respected). Runs automatically on the first list.
     */
    fun ensureDefaultSkill() {
        if (prefs.getBoolean(PREFS_DEFAULT_SEEDED, false)) return
        prefs.edit().putBoolean(PREFS_DEFAULT_SEEDED, true).apply()
        if (File(dir, "$DEFAULT_SKILL_NAME.md").exists()) return
        runCatching { add(DEFAULT_SKILL_NAME, defaultSkillContent()) }
            .onFailure { t -> Err.e(Err.SKILLS_ERROR, "Could not seed default Internet Search skill", t) }
    }

    private fun defaultSkillContent(): String =
        "# Internet Search\n\n" +
            "You have access to a built-in free internet search (no API key needed). Fresh web results are " +
            "injected into your context as a system message whenever the user asks something that current " +
            "information could help with.\n\n" +
            "When the user asks a question:\n" +
            "1. If fresh internet search results were provided, use them as the primary reference for current " +
            "facts and cite the source links (numbered URLs) where helpful.\n" +
            "2. If no search results were provided (search disabled in Settings or nothing found), rely on your " +
            "training knowledge and say so when the topic needs up-to-date information.\n" +
            "3. Never invent URLs or claim facts you cannot verify from the provided results."

    fun list(): List<SkillInfo> {
        ensureDefaultSkill()
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

    /** Reads a single skill's raw Markdown content (empty string if missing). */
    fun readContent(name: String): String =
        runCatching { File(dir, "$name.md").readText(Charsets.UTF_8) }.getOrDefault("")

    /**
     * Edits a skill — name and/or instructions. Renames the stored file and
     * carries the enable/addedAt meta across when the name changes. Returns the
     * stored (sanitised) name.
     */
    fun update(oldName: String, newName: String, content: String): String {
        val target = newName.trim().usernameSafe()
        if (target.isEmpty()) throw IllegalArgumentException("Skill name cannot be empty")
        if (content.isBlank()) throw IllegalArgumentException("Skill content is empty")
        val meta = loadMeta()
        val oldMeta = meta.remove(oldName)
        File(dir, "$target.md").writeText(content)
        meta[target] = if (target == oldName) {
            oldMeta ?: SkillMeta()
        } else {
            SkillMeta(enabled = oldMeta?.enabled ?: true, addedAt = System.currentTimeMillis())
        }
        saveMeta(meta)
        if (target != oldName) File(dir, "$oldName.md").delete()
        Err.i("Skill '$oldName' updated to '$target' (${content.length} chars)")
        return target
    }

    /** Snapshot of every skill (file content + meta) for backup/restore. */
    fun exportAll(): List<SkillData> =
        list().map { s -> SkillData(s.name, readContent(s.name), s.enabled, s.addedAt) }

    /**
     * Restores a complete skill set from a backup, replacing whatever is
     * currently stored. Re-seeds the default Internet Search skill afterwards
     * so a backup made before this version can never remove it.
     */
    fun importAll(data: List<SkillData>) {
        try {
            dir.listFiles { f -> f.isFile && f.extension.equals("md", true) }.orEmpty().forEach { it.delete() }
            val meta = HashMap<String, SkillMeta>()
            data.forEach { d ->
                if (d.name.isNotBlank() && d.content.isNotBlank()) {
                    File(dir, "${d.name}.md").writeText(d.content)
                    meta[d.name] = SkillMeta(enabled = d.enabled, addedAt = d.addedAt)
                }
            }
            saveMeta(meta)
            ensureDefaultSkill()
            Err.i("Restored ${data.size} skill(s) from backup")
        } catch (t: Throwable) {
            Err.e(Err.SKILLS_ERROR, "Skill restore failed", t)
            throw t
        }
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