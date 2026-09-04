package com.jnetai.assistant.data

import com.google.gson.Gson
import com.jnetai.assistant.data.db.AppDatabase
import com.jnetai.assistant.data.model.ChatMode
import com.jnetai.assistant.data.model.ChatSource
import com.jnetai.assistant.data.model.Conversation
import com.jnetai.assistant.data.model.Message
import com.jnetai.assistant.usage.UsageManager
import com.jnetai.assistant.util.Err
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Chat history repository. Conversations + messages are stored locally,
 * including mode, profile/model used and per-message token usage and sources.
 */
class ChatRepository(private val db: AppDatabase, private val gson: Gson = Gson()) {

    fun conversations(): Flow<List<Conversation>> = db.conversationDao().getAll()
    fun messagesFor(conversationId: Long): Flow<List<Message>> = db.messageDao().getByConversation(conversationId)

    suspend fun createConversation(
        profileId: Long, model: String, mode: ChatMode, collectionId: Long = 0
    ): Long {
        val title = "New conversation"
        return db.conversationDao().insert(
            Conversation(
                title = title, profileId = profileId, model = model,
                mode = mode, collectionId = collectionId
            )
        )
    }

    suspend fun getConversation(id: Long): Conversation? = db.conversationDao().getById(id)

    suspend fun rename(id: Long, title: String) {
        db.conversationDao().getById(id)?.let {
            db.conversationDao().update(it.copy(title = title))
        }
    }

    suspend fun deleteConversation(id: Long) {
        db.messageDao().deleteByConversation(id)
        db.conversationDao().delete(id)
    }

    suspend fun duplicate(id: Long): Long {
        val src = db.conversationDao().getById(id) ?: return -1
        val newId = db.conversationDao().insert(
            src.copy(id = 0, title = src.title + " (copy)", createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
        )
        val msgs = db.messageDao().getByConversationOnce(id)
        msgs.forEach { m -> db.messageDao().insert(m.copy(id = 0, conversationId = newId)) }
        return newId
    }

    suspend fun saveMessage(conversationId: Long, m: Message): Long = db.messageDao().insert(m)

    suspend fun historyFor(conversationId: Long): List<Message> = db.messageDao().getByConversationOnce(conversationId)

    suspend fun autotitle(conversationId: Long, firstUserText: String) {
        val title = firstUserText.trim().lineSequence().firstOrNull()?.take(48) ?: "Conversation"
        db.conversationDao().getById(conversationId)?.let {
            db.conversationDao().update(it.copy(title = title, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun touch(conversationId: Long) {
        db.conversationDao().getById(conversationId)?.let {
            db.conversationDao().update(it.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun decodeSources(json: String): List<ChatSource> = try {
        gson.fromJson(json, Array<ChatSource>::class.java)?.toList() ?: emptyList()
    } catch (_: Throwable) { emptyList() }

    fun encodeSources(sources: List<ChatSource>): String = gson.toJson(sources)
}