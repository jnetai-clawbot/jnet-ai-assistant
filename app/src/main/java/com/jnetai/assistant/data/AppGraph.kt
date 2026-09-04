package com.jnetai.assistant.data

import android.content.Context
import com.jnetai.assistant.agent.PermissionManager
import com.jnetai.assistant.ai.ChatEngine
import com.jnetai.assistant.ai.DefaultProviderFactory
import com.jnetai.assistant.ai.ProviderFactory
import com.jnetai.assistant.data.db.AppDatabase
import com.jnetai.assistant.data.model.SettingsManager
import com.jnetai.assistant.data.security.AppLockManager
import com.jnetai.assistant.data.security.SecretStore
import com.jnetai.assistant.rag.RagEngine
import com.jnetai.assistant.usage.UsageManager
import com.jnetai.assistant.voice.AndroidSttProvider
import com.jnetai.assistant.voice.AndroidTtsProvider
import com.jnetai.assistant.voice.VoiceSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Simple service locator / composition root. Keeps module wiring in one place
 * so feature layers stay decoupled and testable.
 */
class AppGraph private constructor(val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val db: AppDatabase by lazy { AppDatabase.get(context) }
    val settings: SettingsManager by lazy { SettingsManager(db.settingsDao()) }
    val secrets: SecretStore by lazy { SecretStore(context) }
    val lock: AppLockManager by lazy { AppLockManager(context) }
    val usage: UsageManager by lazy { UsageManager(db.usageDao(), db.activityDao()) }
    val chatRepository: ChatRepository by lazy { ChatRepository(db) }
    val providerFactory: ProviderFactory by lazy { DefaultProviderFactory() }
    val chatEngine: ChatEngine by lazy { ChatEngine() }
    val permissions: PermissionManager by lazy { PermissionManager(context) }

    val rag: RagEngine by lazy {
        RagEngine(
            context = context,
            db = db,
            embeddingProvider = { texts -> runRagEmbedding(texts) },
            embeddingModelName = { currentEmbeddingModelName() }
        )
    }

    /** Non-suspend holders so VoiceSessionManager can be created lazily. */
    val stt by lazy { AndroidSttProvider(context) }
    val tts by lazy { AndroidTtsProvider(context) }
    val voice: VoiceSessionManager by lazy {
        VoiceSessionManager(scope, stt, tts, chatEngine)
    }

    private suspend fun runRagEmbedding(texts: List<String>): List<List<Double>> {
        val p = db.profileDao().getDefault() ?: return emptyList()
        val provider = providerFactory.create(p) { resolveApiKey(p.apiKeyRef) }
        return provider.embed(texts)
    }

    private fun currentEmbeddingModelName(): String = "profile-default"

    fun resolveApiKey(ref: String): String? = secrets.get(ref)

    companion object {
        @Volatile private var INSTANCE: AppGraph? = null
        fun get(context: Context): AppGraph =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppGraph(context.applicationContext).also { INSTANCE = it }
            }
    }
}