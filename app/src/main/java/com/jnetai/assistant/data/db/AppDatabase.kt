package com.jnetai.assistant.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jnetai.assistant.data.model.ActivityRecord
import com.jnetai.assistant.data.model.AgentAction
import com.jnetai.assistant.data.model.AppSetting
import com.jnetai.assistant.data.model.ChatMode
import com.jnetai.assistant.data.model.Chunk
import com.jnetai.assistant.data.model.ConnectionProfile
import com.jnetai.assistant.data.model.Conversation
import com.jnetai.assistant.data.model.DocCollection
import com.jnetai.assistant.data.model.IndexedDocument
import com.jnetai.assistant.data.model.LocalModel
import com.jnetai.assistant.data.model.Message
import com.jnetai.assistant.data.model.ProviderType
import com.jnetai.assistant.data.model.UsageRecord

class Converters {
    @androidx.room.TypeConverter
    fun fromChatMode(m: ChatMode): String = m.name
    @androidx.room.TypeConverter
    fun toChatMode(s: String): ChatMode = ChatMode.valueOf(s)
    @androidx.room.TypeConverter
    fun fromProviderType(p: ProviderType): String = p.name
    @androidx.room.TypeConverter
    fun toProviderType(s: String): ProviderType = ProviderType.valueOf(s)
}

@Database(
    entities = [
        ConnectionProfile::class, DocCollection::class, IndexedDocument::class,
        Chunk::class, Conversation::class, Message::class, ActivityRecord::class,
        UsageRecord::class, AgentAction::class, LocalModel::class, AppSetting::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun collectionDao(): CollectionDao
    abstract fun documentDao(): DocumentDao
    abstract fun chunkDao(): ChunkDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun activityDao(): ActivityDao
    abstract fun usageDao(): UsageDao
    abstract fun agentDao(): AgentDao
    abstract fun modelDao(): ModelDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /** v1 → v2: profiles gained the opencodeSession column (x-opencode-session header). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN opencodeSession TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jnet_ai.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
    }
}
