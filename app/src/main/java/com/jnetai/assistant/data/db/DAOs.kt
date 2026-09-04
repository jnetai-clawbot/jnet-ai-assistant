package com.jnetai.assistant.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jnetai.assistant.data.model.ActivityRecord
import com.jnetai.assistant.data.model.AgentAction
import com.jnetai.assistant.data.model.Chunk
import com.jnetai.assistant.data.model.ConnectionProfile
import com.jnetai.assistant.data.model.Conversation
import com.jnetai.assistant.data.model.DocCollection
import com.jnetai.assistant.data.model.IndexedDocument
import com.jnetai.assistant.data.model.LocalModel
import com.jnetai.assistant.data.model.Message
import com.jnetai.assistant.data.model.UsageRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY id")
    fun getAll(): Flow<List<ConnectionProfile>>
    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Long): ConnectionProfile?
    @Query("SELECT * FROM profiles WHERE enabled = 1 ORDER BY id LIMIT 1")
    suspend fun getDefault(): ConnectionProfile?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(p: ConnectionProfile): Long
    @Update suspend fun update(p: ConnectionProfile)
    @Delete suspend fun delete(p: ConnectionProfile)
    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY name")
    fun getAll(): Flow<List<DocCollection>>
    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getById(id: Long): DocCollection?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(c: DocCollection): Long
    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun getAll(): Flow<List<IndexedDocument>>
    @Query("SELECT * FROM documents WHERE collectionId = :cid ORDER BY createdAt DESC")
    fun getByCollection(cid: Long): Flow<List<IndexedDocument>>
    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: Long): IndexedDocument?
    @Query("SELECT * FROM documents WHERE fileHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): IndexedDocument?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(d: IndexedDocument): Long
    @Update suspend fun update(d: IndexedDocument)
    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)
    @Query("DELETE FROM chunk WHERE documentId = :docId")
    suspend fun deleteChunks(docId: Long)
}

@Dao
interface ChunkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<Chunk>)
    @Query("SELECT * FROM chunk WHERE documentId = :docId")
    suspend fun getByDocument(docId: Long): List<Chunk>
    @Query("SELECT * FROM chunk")
    suspend fun getAll(): List<Chunk>
    @Query("DELETE FROM chunk WHERE documentId = :docId")
    suspend fun deleteByDocument(docId: Long)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<Conversation>>
    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): Conversation?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(c: Conversation): Long
    @Update suspend fun update(c: Conversation)
    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long)
    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun count(): Int
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :cid ORDER BY id")
    fun getByConversation(cid: Long): Flow<List<Message>>
    @Query("SELECT * FROM messages WHERE conversationId = :cid ORDER BY id")
    suspend fun getByConversationOnce(cid: Long): List<Message>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(m: Message): Long
    @Query("DELETE FROM messages WHERE conversationId = :cid")
    suspend fun deleteByConversation(cid: Long)
}

@Dao
interface ActivityDao {
    @Insert suspend fun insert(a: ActivityRecord): Long
    @Query("SELECT * FROM activity ORDER BY createdAt DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<ActivityRecord>>
    @Query("SELECT * FROM activity ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<ActivityRecord>
    @Query("DELETE FROM activity")
    suspend fun clear()
}

@Dao
interface UsageDao {
    @Insert suspend fun insert(u: UsageRecord): Long
    @Query("SELECT COALESCE(SUM(promptTokens + completionTokens),0) FROM usage WHERE createdAt >= :since")
    suspend fun totalSince(since: Long): Long
    @Query("SELECT COALESCE(SUM(promptTokens + completionTokens),0) FROM usage WHERE profileId = :pid AND createdAt >= :since")
    suspend fun totalForProfileSince(pid: Long, since: Long): Long
    @Query("SELECT * FROM usage ORDER BY createdAt DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<UsageRecord>>
    @Query("SELECT COALESCE(SUM(promptTokens),0) + COALESCE(SUM(completionTokens),0) FROM usage")
    suspend fun totalAll(): Long
    @Query("SELECT COALESCE(COUNT(*),0) FROM usage WHERE createdAt >= :since")
    suspend fun countSince(since: Long): Long
    @Query("DELETE FROM usage")
    suspend fun clear()
}

@Dao
interface AgentDao {
    @Insert suspend fun insert(a: AgentAction): Long
    @Query("SELECT * FROM agent_actions ORDER BY createdAt DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<AgentAction>>
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM models")
    fun getAll(): Flow<List<LocalModel>>
    @Query("SELECT * FROM models")
    suspend fun getAllOnce(): List<LocalModel>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(m: LocalModel): Long
    @Update suspend fun update(m: LocalModel)
    @Query("DELETE FROM models WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(s: AppSetting)
    @Query("SELECT * FROM app_settings WHERE key = :key")
    suspend fun get(key: String): AppSetting?
    @Query("SELECT * FROM app_settings")
    suspend fun getAll(): List<AppSetting>
}
