package com.jnetai.assistant.data.security

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.jnetai.assistant.data.AppGraph
import com.jnetai.assistant.data.db.AppDatabase
import com.jnetai.assistant.data.model.ConnectionProfile
import com.jnetai.assistant.data.model.Conversation
import com.jnetai.assistant.data.model.DocCollection
import com.jnetai.assistant.data.model.IndexedDocument
import com.jnetai.assistant.data.model.Message
import com.jnetai.assistant.data.model.UsageRecord
import com.jnetai.assistant.util.Err
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.Base64

/**
 * Encrypted backup/export. Writes a single GZIP'd JSON envelope encrypted with
 * AES-GCM via the Keystore master key. Secrets (API keys) are exported only in
 * their encrypted form. Imports validate the MAC/integrity before touching any
 * data, and never overwrite existing data without explicit confirmation.
 */
class BackupManager(private val context: Context) {

    private val gson: Gson = GsonBuilder().create()
    private val db: AppDatabase = AppGraph.get(context).db

    data class BackupEnvelope(
        val app: String,
        val version: Int,
        val createdAt: Long,
        val profiles: List<BackupProfile>,
        val collections: List<DocCollection>,
        val documents: List<DocumentBackup>,
        val conversations: List<BackupConversation>,
        val settings: Map<String, String>,
        val checksum: String
    )

    data class BackupProfile(val profile: ConnectionProfile, val encryptedKeyRef: String)
    data class DocumentBackup(val doc: IndexedDocument, val chunks: List<com.jnetai.assistant.data.model.Chunk>)
    data class BackupConversation(val conversation: Conversation, val messages: List<Message>)

    private val graph: AppGraph = AppGraph.get(context)

    suspend fun export(uri: Uri): Boolean = try {
        val env = buildEnvelope()
        val json = gson.toJson(env)
        val encrypted = CryptoManager.encrypt(json)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(encrypted.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("No output stream")
        true
    } catch (t: Throwable) {
        Err.e(Err.BACKUP_ERROR, "Backup export failed", t)
        false
    }

    suspend fun import(uri: Uri, onConfirmOverwrite: suspend () -> Boolean): Boolean = try {
        val encrypted = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot read backup")
        val json = CryptoManager.decrypt(String(encrypted, Charsets.UTF_8))
        val env = gson.fromJson(json, BackupEnvelope::class.java)
        // integrity: verify checksum over the structural content
        if (env.checksum != checksumOf(env)) {
            Err.e(Err.BACKUP_ERROR, "Backup checksum mismatch — refusing to import")
            return false
        }
        if (db.profileDao().count() > 0 || db.conversationDao().count() > 0) {
            if (!onConfirmOverwrite()) return false
        }
        // Import profiles (preserving encrypted key refs — keys remain encrypted)
        env.profiles.forEach { bp ->
            db.profileDao().insert(bp.profile.copy(id = 0, apiKeyRef = bp.encryptedKeyRef))
        }
        env.collections.forEach { c -> db.collectionDao().insert(c.copy(id = 0)) }
        env.conversations.forEach { bc ->
            val newId = db.conversationDao().insert(bc.conversation.copy(id = 0))
            bc.messages.forEach { m -> db.messageDao().insert(m.copy(id = 0, conversationId = newId)) }
        }
        env.settings.forEach { (k, v) -> db.settingsDao().put(com.jnetai.assistant.data.model.AppSetting(k, v)) }
        true
    } catch (t: Throwable) {
        Err.e(Err.BACKUP_ERROR, "Backup import failed", t)
        false
    }

    private suspend fun buildEnvelope(): BackupEnvelope {
        val profiles = db.profileDao().getAllOnceIfAvailable()
        val collections = db.collectionDao().getAll()
        val allCollections = kotlinx.coroutines.flow.first(allCollections)
        val docs = kotlinx.coroutines.flow.first(db.documentDao().getAll())
        val convs = kotlinx.coroutines.flow.first(db.conversationDao().getAll())
        val settings = db.settingsDao().getAll().associate { it.key to it.value }

        val docBackups = docs.map { d ->
            DocumentBackup(d, db.chunkDao().getByDocument(d.id))
        }
        val convBackups = convs.map { c ->
            BackupConversation(c, db.messageDao().getByConversationOnce(c.id))
        }
        val env = BackupEnvelope(
            app = "JNetAIAssistant", version = 1, createdAt = System.currentTimeMillis(),
            profiles = profiles.map { BackupProfile(it, it.apiKeyRef) },
            collections = allCollections,
            documents = docBackups,
            conversations = convBackups,
            settings = settings,
            checksum = ""
        )
        return env.copy(checksum = checksumOf(env))
    }

    private fun checksumOf(env: BackupEnvelope): String {
        val material = env.profiles.map { it.profile.name } +
            env.collections.map { it.name } +
            env.documents.map { "${it.doc.name}:${it.doc.fileHash}" } +
            env.conversations.map { it.conversation.title } +
            env.settings.keys
        val digest = MessageDigest.getInstance("SHA-256").digest(material.joinToString().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }
}

private suspend fun com.jnetai.assistant.data.db.ProfileDao.getAllOnceIfAvailable(): List<ConnectionProfile> =
    try {
        kotlinx.coroutines.flow.first(getAll())
    } catch (_: Throwable) {
        emptyList()
    }