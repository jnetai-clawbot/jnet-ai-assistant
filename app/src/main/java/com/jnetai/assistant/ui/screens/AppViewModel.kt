package com.jnetai.assistant.ui.screens

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.jnetai.assistant.ai.ChatMessageInput
import com.jnetai.assistant.ai.EngineEvent
import com.jnetai.assistant.ai.SendRequest
import com.jnetai.assistant.data.AppGraph
import com.jnetai.assistant.data.model.ChatMode
import com.jnetai.assistant.data.model.ChatSource
import com.jnetai.assistant.data.model.ConnectionProfile
import com.jnetai.assistant.data.model.Conversation
import com.jnetai.assistant.data.model.IndexedDocument
import com.jnetai.assistant.data.model.Message
import com.jnetai.assistant.data.model.ProviderType
import com.jnetai.assistant.util.Err
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Result of a lock-screen PIN attempt (kept for API compatibility). */
data class LockDecision(val ok: Boolean, val mustChangePin: Boolean = false)

/**
 * Root application ViewModel. Holds profile list, chat session state, RAG,
 * agent, usage and settings state. Kept intentionally single for practical
 * wiring between screens while still delegating heavy work to the engine layer.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = AppGraph.get(app)
    val db = graph.db
    val settings = graph.settings
    val usage = graph.usage
    val chatRepo = graph.chatRepository
    val rag = graph.rag
    val secrets = graph.secrets
    val lock = graph.lock
    val permissions = graph.permissions
    val voice = graph.voice
    val gson = Gson()

    // ---- Profiles ----
    private val _profiles = MutableStateFlow<List<ConnectionProfile>>(emptyList())
    val profiles: StateFlow<List<ConnectionProfile>> = _profiles.asStateFlow()

    private val _selectedProfileId = MutableStateFlow(0L)
    val selectedProfileId: StateFlow<Long> = _selectedProfileId.asStateFlow()

    // ---- Chat ----
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _activeConversationId = MutableStateFlow(0L)
    val activeConversationId: StateFlow<Long> = _activeConversationId.asStateFlow()

    val inputText = MutableStateFlow("")
    val streamingText = MutableStateFlow("")
    val isStreaming = MutableStateFlow(false)
    val chatBusy = MutableStateFlow(false)

    private val _chatMode = MutableStateFlow(ChatMode.NORMAL)
    val chatMode: StateFlow<ChatMode> = _chatMode.asStateFlow()

    private val _selectedCollections = MutableStateFlow<Set<Long>>(emptySet())
    val selectedCollections: StateFlow<Set<Long>> = _selectedCollections.asStateFlow()

    // ---- Documents ----
    private val _documents = MutableStateFlow<List<IndexedDocument>>(emptyList())
    val documents: StateFlow<List<IndexedDocument>> = _documents.asStateFlow()

    val collections = MutableStateFlow<List<com.jnetai.assistant.data.model.DocCollection>>(emptyList())

    // ---- Status ----
    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()
    private val _statusTone = MutableStateFlow(com.jnetai.assistant.ui.components.Tone.INFO)
    val statusTone: StateFlow<com.jnetai.assistant.ui.components.Tone> = _statusTone.asStateFlow()

    // ---- Test connection ----
    val testResult = MutableStateFlow<com.jnetai.assistant.ai.ConnectionTestResult?>(null)

    // ---- Onboarding / lock ----
    val needsOnboarding = MutableStateFlow(false)
    val appLocked = MutableStateFlow(true)

    private var streamJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            try {
                _profiles.value = withContext(Dispatchers.IO) { firstOf(graph.db.profileDao().getAll()) }
                migrateOldOpenCodeDefaults()
                db.profileDao().getAll().collectLatest { _profiles.value = it }
                settings.hasCompletedOnboarding().let { needsOnboarding.value = !it }
                if (needsOnboarding.value) appLocked.value = false // don't gate onboarding behind lock
                else appLocked.value = lock.requiresUnlock()
                loadData()
            } catch (t: Throwable) {
                // Never let a startup failure close the app — open unlocked with the error logged.
                Err.e(Err.DB_ERROR, "Startup initialisation failed", t)
                needsOnboarding.value = false
                appLocked.value = false
                setStatus("Startup issue detected — details in diagnostics log", com.jnetai.assistant.ui.components.Tone.ERROR)
            }
        }
        // Issue #5 — persist every completed Voice Assistant turn into history
        // so voice conversations are visible alongside chat history.
        viewModelScope.launch {
            voice.currentTurn.collect { turn ->
                if (turn != null && turn.transcript.isNotBlank() && turn.response.isNotBlank()) {
                    persistVoiceTurn(turn)
                }
            }
        }
    }

    private suspend fun <T> firstOf(flow: Flow<T>): T = flow.first()

    suspend fun loadData() {
        try {
            withContext(Dispatchers.IO) {
                _conversations.value = firstOf(db.conversationDao().getAll())
                _documents.value = firstOf(db.documentDao().getAll())
                collections.value = firstOf(db.collectionDao().getAll())
                if (_selectedProfileId.value == 0L && _profiles.value.isNotEmpty()) {
                    _selectedProfileId.value = _profiles.value.first().id
                }
            }
        } catch (t: Throwable) {
            Err.e(Err.DB_ERROR, "loadData failed", t)
        }
    }

    fun refreshAll() { viewModelScope.launch { loadData() } }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val ok = com.jnetai.assistant.data.security.BackupManager(getApplication()).export(uri)
            setStatus(if (ok) "Backup exported (encrypted)" else "Backup failed", if (ok) com.jnetai.assistant.ui.components.Tone.SUCCESS else com.jnetai.assistant.ui.components.Tone.ERROR)
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val ok = com.jnetai.assistant.data.security.BackupManager(getApplication()).import(uri) { true }
            setStatus(if (ok) "Backup imported" else "Import failed — invalid or corrupted backup", if (ok) com.jnetai.assistant.ui.components.Tone.SUCCESS else com.jnetai.assistant.ui.components.Tone.ERROR)
            refreshAll()
        }
    }

    fun setStatus(msg: String, tone: com.jnetai.assistant.ui.components.Tone = com.jnetai.assistant.ui.components.Tone.INFO) {
        _statusMessage.value = msg
        _statusTone.value = tone
    }

    fun selectedProfile(): ConnectionProfile? =
        _profiles.value.find { it.id == _selectedProfileId.value }
            ?: _profiles.value.firstOrNull()

    fun selectProfile(id: Long) {
        _selectedProfileId.value = id
        viewModelScope.launch {
            usage.logActivity("AI", "Switched to profile '${profileName(id)}'")
        }
    }

    private suspend fun profileName(id: Long): String =
        _profiles.value.find { it.id == id }?.name ?: "?"

    /** Active local model name from the Models tab (empty if none active). */
    suspend fun activeLocalModelName(): String =
        withContext(Dispatchers.IO) {
            db.modelDao().getAllOnce().find { it.active }?.name ?: ""
        }

    /**
     * Uses the profile's own Model ID when it is filled in; otherwise falls back
     * to the active local model configured in the Models tab.
     */
    suspend fun resolveProfileModel(p: ConnectionProfile): ConnectionProfile =
        if (p.model.isBlank()) {
            val local = activeLocalModelName()
            if (local.isNotBlank()) p.copy(model = local) else p
        } else {
            p
        }

    // ---------- PROFILE CRUD ----------
    /**
     * v1.0.10 in-place migration: any OpenCode profile still holding the old
     * prefill (model "opencode-go" or blank) is bumped to the new default
     * model deepseek-v4-flash with streaming OFF (stability). Profiles that
     * already use another model are left untouched.
     */
    private suspend fun migrateOldOpenCodeDefaults() {
        try {
            val changed = _profiles.value
                .filter { p -> p.providerType == ProviderType.OPENCODE && (p.model == "opencode-go" || p.model.isBlank()) }
                .map { p -> p.copy(model = "deepseek-v4-flash", streaming = false) }
            if (changed.isNotEmpty()) {
                changed.forEach { if (it.id != 0L) db.profileDao().update(it) }
                Err.i("Migrated ${changed.size} OpenCode profile(s) to deepseek-v4-flash / streaming off")
            }
        } catch (t: Throwable) {
            Err.e(Err.DB_ERROR, "OpenCode defaults migration failed", t)
        }
    }

    fun saveProfile(p: ConnectionProfile, rawApiKey: String? = null) {
        viewModelScope.launch {
            val oldRef = p.apiKeyRef
            // If a new key was typed, store it encrypted; otherwise keep existing reference
            val apiKeyRef = if (rawApiKey != null && rawApiKey.isNotBlank()) {
                val newRef = secrets.put(rawApiKey)
                if (oldRef.isNotEmpty()) secrets.delete(oldRef)
                newRef
            } else {
                oldRef
            }
            val toSave = p.copy(apiKeyRef = apiKeyRef)
            if (toSave.id == 0L) db.profileDao().insert(toSave) else db.profileDao().update(toSave)
            refreshAll()
            setStatus("Profile '${p.name}' saved", com.jnetai.assistant.ui.components.Tone.SUCCESS)
        }
    }

    fun deleteProfile(p: ConnectionProfile) {
        viewModelScope.launch {
            if (p.apiKeyRef.isNotEmpty()) secrets.delete(p.apiKeyRef)
            db.profileDao().delete(p)
            if (_selectedProfileId.value == p.id) _selectedProfileId.value = 0
            refreshAll()
            setStatus("Profile deleted", com.jnetai.assistant.ui.components.Tone.INFO)
        }
    }

    fun testProfile(p: ConnectionProfile) {
        testResult.value = null
        viewModelScope.launch {
            val provider = graph.providerFactory.create(p) { secrets.get(p.apiKeyRef) }
            testResult.value = withContext(Dispatchers.IO) { provider.testConnection() }
            usage.logActivity("AI", "Connection test: ${p.name}", "")
        }
    }

    fun listModelsForProfile(p: ConnectionProfile, onResult: (List<String>) -> Unit) {
        viewModelScope.launch {
            val provider = graph.providerFactory.create(p) { secrets.get(p.apiKeyRef) }
            val models = withContext(Dispatchers.IO) { provider.listModels() }
            onResult(models.map { it.id })
        }
    }

    // ---------- CHAT ----------
    fun newConversation() {
        viewModelScope.launch {
            val profile = selectedProfile() ?: run { setStatus("Create a connection profile first", com.jnetai.assistant.ui.components.Tone.ERROR); return@launch }
            val cid = chatRepo.createConversation(profile.id, profile.model, _chatMode.value, _selectedCollections.value.firstOrNull() ?: 0)
            _activeConversationId.value = cid
            _messages.value = emptyList()
            inputText.value = ""
            loadData()
        }
    }

    fun openConversation(id: Long) {
        viewModelScope.launch {
            _activeConversationId.value = id
            _messages.value = withContext(Dispatchers.IO) { chatRepo.historyFor(id) }
        }
    }

    fun setMode(mode: ChatMode) {
        _chatMode.value = mode
        if (_activeConversationId.value != 0L) viewModelScope.launch {
            chatRepo.getConversation(_activeConversationId.value)?.let {
                db.conversationDao().update(it.copy(mode = mode))
            }
        }
    }

    fun toggleCollection(id: Long) {
        val current = _selectedCollections.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedCollections.value = current
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || chatBusy.value) return
        viewModelScope.launch {
            val profile0 = selectedProfile() ?: run { setStatus("No profile selected", com.jnetai.assistant.ui.components.Tone.ERROR); return@launch }
            // Use the profile's Model ID when set; otherwise fall back to the active local model from the Models tab.
            val profile = resolveProfileModel(profile0)
            if (profile.model.isBlank()) {
                setStatus("Set a model for this profile (or activate one in the Models tab)", com.jnetai.assistant.ui.components.Tone.ERROR); return@launch
            }

            chatBusy.value = true
            isStreaming.value = true
            streamingText.value = ""

            // ensure a conversation exists
            var cid = _activeConversationId.value
            if (cid == 0L) {
                cid = chatRepo.createConversation(profile.id, profile.model, _chatMode.value, _selectedCollections.value.firstOrNull() ?: 0)
                _activeConversationId.value = cid
            }
            chatRepo.saveMessage(cid, Message(conversationId = cid, role = "user", content = text, providerName = profile.name))
            if (chatRepo.historyFor(cid).size <= 1) chatRepo.autotitle(cid, text)
            _messages.value = chatRepo.historyFor(cid)
            inputText.value = ""

            // build context
            val mode = _chatMode.value
            var ragCtx: com.jnetai.assistant.rag.RagSearchContext? = null
            if (mode == ChatMode.RAG || mode == ChatMode.HYBRID) {
                val collIds = if (_selectedCollections.value.isNotEmpty()) _selectedCollections.value.toList() else null
                ragCtx = rag.searchRag(text, collIds, null, limit = settings.getRetrievalCount(), hybrid = settings.getHybridSearch())
                usage.logActivity("RAG", "Search: '$text' → ${ragCtx?.chunks?.size ?: 0} chunks")
            }

            // history for model context
            val history = chatRepo.historyFor(cid).takeLast(settings.getInt("profile.max_history", profile.maxHistory))
            val messages = buildList {
                if (profile.systemPrompt.isNotBlank()) add(ChatMessageInput("system", profile.systemPrompt))
                if (ragCtx != null && ragCtx.contextText.isNotBlank()) {
                    add(ChatMessageInput("system", "Use the following retrieved documents as the primary factual source. If the answer is not in these documents, say so.\n\n${ragCtx.contextText}"))
                }
                history.forEach { m -> add(ChatMessageInput(m.role, m.content)) }
            }

            val provider = graph.providerFactory.create(profile) { secrets.get(profile.apiKeyRef) }
            val engine = graph.chatEngine

            usage.record(profile.id, profile.model, 0, 0, category = "chat") // usage recorded on completion below

            streamJob = viewModelScope.launch {
                try {
                    val event = try {
                        engine.run(SendRequest(provider, messages, stream = profile.streaming)) { delta ->
                            streamingText.value += delta
                            _messages.value = chatRepo.historyFor(cid).toMutableList().also { list ->
                                val last = list.lastOrNull()
                                if (last?.role == "assistant") {
                                    list[list.size - 1] = last.copy(content = last.content + delta)
                                } else {
                                    list.add(Message(conversationId = cid, role = "assistant", content = delta, providerName = profile.name))
                                }
                            }
                        }
                    } catch (t: kotlinx.coroutines.CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        // A transient stream/network failure must never crash the app.
                        // Log it, surface an error status, and keep the user message intact.
                        Err.e(Err.API_STREAM_ERROR, "Chat request failed (recovered)", t)
                        EngineEvent.Failed(
                            code = Err.API_STREAM_ERROR,
                            message = "Request failed — try again. Details in Error logs."
                        )
                    }
                    streamingText.value = ""
                    isStreaming.value = false
                    chatBusy.value = false
                    when (event) {
                        is EngineEvent.Done -> {
                            // replace any partial assistant rows with one final message
                            val finalMsgs = chatRepo.historyFor(cid).toMutableList()
                            finalMsgs.removeAll { m -> m.role == "assistant" && m.content.isNotBlank() && m.content != event.fullText }
                            val msg = Message(
                                conversationId = cid, role = "assistant", content = event.fullText,
                                sources = chatRepo.encodeSources(event.sources),
                                promptTokens = event.promptTokens, completionTokens = event.completionTokens,
                                providerName = profile.name
                            )
                            finalMsgs.add(msg)
                            // persist cleanly: delete rows of this conversation, re-save
                            db.messageDao().deleteByConversation(cid)
                            val clean = finalMsgs.filter { it.role == "user" || it.content.isNotBlank() }
                            clean.forEach { chatRepo.saveMessage(cid, it.copy(id = 0)) }
                            _messages.value = chatRepo.historyFor(cid)
                            usage.record(profile.id, profile.model, event.promptTokens, event.completionTokens, category = when (mode) {
                                ChatMode.RAG, ChatMode.HYBRID -> "rag"; ChatMode.AGENT -> "agent"; else -> "chat"
                            })
                            usage.logActivity("AI", "Chat with ${profile.name}/${profile.model}", "mode=${mode.name}", event.promptTokens + event.completionTokens)
                            chatRepo.touch(cid)
                            refreshAll()
                        }
                        is EngineEvent.Failed -> {
                            _messages.value = chatRepo.historyFor(cid)
                            setStatus(event.message, com.jnetai.assistant.ui.components.Tone.ERROR)
                            usage.logActivity("error", "Failed request", event.message)
                        }
                        is EngineEvent.ToolRequested -> {
                            setStatus("Agent tools requested but not auto-executed in chat mode", com.jnetai.assistant.ui.components.Tone.INFO)
                        }
                        else -> {}
                    }
                } catch (t: kotlinx.coroutines.CancellationException) {
                    throw t
                } catch (t: Throwable) {
                    // Last-resort guard: any handler/database hiccup must not crash the app.
                    Err.e(Err.API_STREAM_ERROR, "Chat completion handler failed (recovered)", t)
                    isStreaming.value = false
                    chatBusy.value = false
                    streamingText.value = ""
                    setStatus("Something went wrong completing the reply — see Error logs", com.jnetai.assistant.ui.components.Tone.ERROR)
                }
            }
        }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        isStreaming.value = false
        chatBusy.value = false
        viewModelScope.launch {
            // persist what streamed so far
            val cid = _activeConversationId.value
            if (cid != 0L && streamingText.value.isNotBlank()) {
                chatRepo.saveMessage(cid, Message(conversationId = cid, role = "assistant", content = streamingText.value, providerName = selectedProfile()?.name ?: ""))
                _messages.value = chatRepo.historyFor(cid)
            }
            streamingText.value = ""
            setStatus("Generation stopped", com.jnetai.assistant.ui.components.Tone.INFO)
        }
    }

    fun regenerateLast() {
        val msgs = _messages.value
        val lastUser = msgs.lastOrNull { it.role == "user" } ?: return
        val cid = _activeConversationId.value
        // remove last assistant messages
        viewModelScope.launch {
            val remaining = msgs.filter { it.role != "assistant" }
            if (cid != 0L) {
                db.messageDao().deleteByConversation(cid)
                remaining.forEach { chatRepo.saveMessage(cid, it) }
                _messages.value = remaining
            }
            sendMessage(lastUser.content)
        }
    }

    fun renameConversation(id: Long, title: String) {
        viewModelScope.launch { chatRepo.rename(id, title); refreshAll() }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            chatRepo.deleteConversation(id)
            if (_activeConversationId.value == id) { _activeConversationId.value = 0; _messages.value = emptyList() }
            refreshAll()
        }
    }

    fun duplicateConversation(id: Long) {
        viewModelScope.launch { val n = chatRepo.duplicate(id); refreshAll() }
    }

    // ---------- DOCUMENTS ----------

    /**
     * Imports a document into a run of picker-selected files. When a specific
     * collection was chosen for the upload it is used; otherwise the document
     * lands in the first selected / first existing / auto-created collection.
     * Files are only indexed into the RAG database — the original file on the
     * device is never modified or deleted.
     */
    fun importDocument(uri: Uri) {
        viewModelScope.launch {
            try {
                val (name, size) = withContext(Dispatchers.IO) {
                    com.jnetai.assistant.document.DocumentParser.nameAndSize(getApplication(), uri)
                }
                val mime = getApplication<Application>().contentResolver.getType(uri) ?: ""
                if (!com.jnetai.assistant.document.DocumentParser.isSupported(mime, name)) {
                    setStatus("Unsupported document: $name — the file stays on your device", com.jnetai.assistant.ui.components.Tone.ERROR)
                    return@launch
                }
                val collId = _selectedCollections.value.firstOrNull()
                    ?: collections.value.firstOrNull()?.id
                    ?: rag.createCollection("Documents")
                runCatching { importDocumentToCollection(uri, collId) }
                    .onSuccess { refreshAll() }
                    .onFailure { t ->
                        Err.e(Err.RAG_INDEX_ERROR, "Import failed", t)
                        setStatus("Failed to index document — see Error logs", com.jnetai.assistant.ui.components.Tone.ERROR)
                    }
            } catch (t: com.jnetai.assistant.document.UnsupportedDocumentException) {
                setStatus(t.message ?: "Unsupported document", com.jnetai.assistant.ui.components.Tone.ERROR)
            } catch (t: Throwable) {
                Err.e(Err.RAG_INDEX_ERROR, "Import failed", t)
                setStatus("Failed to index document — see Error logs", com.jnetai.assistant.ui.components.Tone.ERROR)
            }
        }
    }

    /** Indexes a picked document into an explicitly chosen collection. Never crashes on failure. */
    fun importDocumentToCollection(uri: Uri, collectionId: Long) {
        viewModelScope.launch {
            try {
                val (name, size) = withContext(Dispatchers.IO) {
                    com.jnetai.assistant.document.DocumentParser.nameAndSize(getApplication(), uri)
                }
                val mime = getApplication<Application>().contentResolver.getType(uri) ?: ""
                if (!com.jnetai.assistant.document.DocumentParser.isSupported(mime, name)) {
                    setStatus("Unsupported document: $name — the file stays on your device", com.jnetai.assistant.ui.components.Tone.ERROR)
                    return@launch
                }
                val collId = if (collectionId > 0L) collectionId
                else _selectedCollections.value.firstOrNull()
                    ?: collections.value.firstOrNull()?.id
                    ?: rag.createCollection("Documents")
                setStatus("Indexing $name…")
                rag.indexDocument(uri, name, mime, collId)
                usage.logActivity("RAG", "Indexed $name")
                setStatus("Indexed $name", com.jnetai.assistant.ui.components.Tone.SUCCESS)
                refreshAll()
            } catch (t: com.jnetai.assistant.document.UnsupportedDocumentException) {
                setStatus(t.message ?: "Unsupported document", com.jnetai.assistant.ui.components.Tone.ERROR)
            } catch (t: Throwable) {
                Err.e(Err.RAG_INDEX_ERROR, "Import to collection $collectionId failed", t)
                setStatus("Failed to index document — see Error logs", com.jnetai.assistant.ui.components.Tone.ERROR)
            }
        }
    }

    fun queueIndexingViaWorkManager(uri: Uri, name: String, mime: String, collId: Long) {
        val data = androidx.work.Data.Builder()
            .putString("uri", uri.toString())
            .putString("name", name)
            .putString("mime", mime)
            .putLong("collectionId", collId)
            .build()
        val req = androidx.work.OneTimeWorkRequestBuilder<com.jnetai.assistant.document.IndexWorker>()
            .setInputData(data)
            .build()
        androidx.work.WorkManager.getInstance(getApplication()).enqueue(req)
        setStatus("Indexing queued in background")
    }

    fun deleteDocument(id: Long) {
        viewModelScope.launch {
            try {
                rag.deleteDocument(id)
                refreshAll()
                setStatus("Document removed — the file stays on your device", com.jnetai.assistant.ui.components.Tone.INFO)
            } catch (t: Throwable) {
                Err.e(Err.RAG_INDEX_ERROR, "deleteDocument failed id=$id", t)
                setStatus("Could not remove document — see Error logs", com.jnetai.assistant.ui.components.Tone.ERROR)
            }
        }
    }

    fun createCollection(name: String) {
        viewModelScope.launch {
            if (name.isBlank()) return@launch
            try {
                rag.createCollection(name)
                refreshAll()
                setStatus("Collection '$name' created", com.jnetai.assistant.ui.components.Tone.SUCCESS)
            } catch (t: Throwable) {
                Err.e(Err.RAG_COLLECTION_ERROR, "createCollection failed", t)
                setStatus("Could not create collection — see Error logs", com.jnetai.assistant.ui.components.Tone.ERROR)
            }
        }
    }

    /** Renames/saves a collection's name. */
    fun saveCollectionName(id: Long, name: String) {
        viewModelScope.launch {
            if (name.isBlank()) { setStatus("Collection name cannot be blank", com.jnetai.assistant.ui.components.Tone.ERROR); return@launch }
            try {
                rag.renameCollection(id, name)
                refreshAll()
                setStatus("Collection renamed to '$name'", com.jnetai.assistant.ui.components.Tone.SUCCESS)
            } catch (t: Throwable) {
                Err.e(Err.RAG_COLLECTION_ERROR, "saveCollectionName failed id=$id", t)
                setStatus("Could not rename collection — see Error logs", com.jnetai.assistant.ui.components.Tone.ERROR)
            }
        }
    }

    /**
     * Removes a collection and every document inside it from the Docs / RAG /
     * search scope. The files themselves remain on the device untouched (only
     * the local index is cleared). Also unsets it from the RAG scope filter.
     */
    fun deleteCollection(id: Long) {
        viewModelScope.launch {
            try {
                rag.deleteCollection(id)
                val sel = _selectedCollections.value.toMutableSet()
                sel.remove(id)
                _selectedCollections.value = sel
                refreshAll()
                setStatus("Collection removed — files stay on your device", com.jnetai.assistant.ui.components.Tone.INFO)
            } catch (t: Throwable) {
                Err.e(Err.RAG_COLLECTION_ERROR, "deleteCollection failed id=$id", t)
                setStatus("Could not remove collection — see Error logs", com.jnetai.assistant.ui.components.Tone.ERROR)
            }
        }
    }

    // ---------- VOICE ----------
    fun ensureTts() {
        if (!voice.ttsReady) {
            graph.tts.init { ok ->
                if (ok) {
                    voice.ttsReady = true
                    viewModelScope.launch {
                        graph.tts.setRate(settings.getTtsRate())
                        graph.tts.setPitch(settings.getTtsPitch())
                    }
                } else setStatus("TTS unavailable", com.jnetai.assistant.ui.components.Tone.ERROR)
            }
        }
    }

    fun onVoiceMicPress() {
        ensureTts()
        if (voice.state.value == com.jnetai.assistant.voice.VoiceState.LISTENING) { voice.stopListening(); return }
        viewModelScope.launch {
            val profile0 = selectedProfile() ?: run { setStatus("Select a profile for voice mode", com.jnetai.assistant.ui.components.Tone.ERROR); return@launch }
            val profile = resolveProfileModel(profile0)
            voice.wire(
                conversation = { transcript ->
                    // build conversation for voice: full history + system
                    val cid = _activeConversationId.value
                    val messages = buildList {
                        if (profile.systemPrompt.isNotBlank()) add(ChatMessageInput("system", profile.systemPrompt))
                        if (cid != 0L) {
                            chatRepo.historyFor(cid).takeLast(settings.getInt("profile.max_history", profile.maxHistory)).forEach { m -> add(ChatMessageInput(m.role, m.content)) }
                        }
                        add(ChatMessageInput("user", transcript))
                    }
                    messages
                },
                provider = { graph.providerFactory.create(profile) { secrets.get(profile.apiKeyRef) } }
            )
            voice.startListening()
        }
    }

    fun onVoiceInterrupt() {
        voice.cancel()
    }

    /**
     * Issue #5 — writes a finished Voice Assistant turn (user transcript +
     * AI response) into the most recent Voice-mode conversation, creating a
     * new one if needed. This gives the Voice Assistant real history.
     */
    private suspend fun persistVoiceTurn(turn: com.jnetai.assistant.voice.VoiceTurn) {
        try {
            val cid = withContext(Dispatchers.IO) {
                val profile = selectedProfile() ?: return@withContext 0L
                val existing = firstOf(db.conversationDao().getAll())
                    .filter { it.mode == ChatMode.VOICE }
                    .maxByOrNull { it.updatedAt }
                val id = existing?.id ?: chatRepo.createConversation(profile.id, profile.model, ChatMode.VOICE)
                chatRepo.saveMessage(id, Message(conversationId = id, role = "user", content = turn.transcript, providerName = profile.name))
                chatRepo.saveMessage(id, Message(
                    conversationId = id, role = "assistant", content = turn.response,
                    sources = chatRepo.encodeSources(turn.sources), providerName = profile.name
                ))
                chatRepo.touch(id)
                id
            }
            if (cid != 0L) loadData()
        } catch (t: Throwable) {
            Err.e(Err.DB_ERROR, "persistVoiceTurn failed", t)
        }
    }

    // ---------- CHAT MIC (STT → text input) ----------
    /** True while the chat mic is listening for a transcription. */
    val chatMicListening = MutableStateFlow(false)

    /**
     * Incremented on every press/cancel so a stale recogniser callback from a
     * previous (cancelled/superseded) session can never write into the box.
     */
    private var chatMicSeq = 0L

    /**
     * Chat-bar microphone: transcribes speech to text and places it in the
     * chat input box, ready to review and send. This is intentionally NOT the
     * full voice-assistant pipeline — it only uses speech-to-text.
     *
     * The recogniser reports many interim (partial) transcripts while you speak
     * and then one final transcript — only the FINAL result is accepted, exactly
     * once per press, so the words never end up in the box twice.
     */
    fun onChatMicPress() {
        if (chatMicListening.value) { stopChatMic(); return }
        chatMicListening.value = true
        val seq = ++chatMicSeq
        var accepted = false
        setStatus("Listening — speak now", com.jnetai.assistant.ui.components.Tone.INFO)
        try {
            graph.stt.startListening(
                onResult = onResult@{ result ->
                    if (seq != chatMicSeq) return@onResult // stale session — ignore
                    if (result.errorCode != null) {
                        chatMicListening.value = false
                        Err.e(result.errorCode, "Chat mic STT failed: ${result.errorMessage}")
                        setStatus(result.errorMessage ?: "Speech recognition failed", com.jnetai.assistant.ui.components.Tone.ERROR)
                    } else if (result.isFinal) {
                        // only accept the final, most accurate transcript, once.
                        if (accepted) return@onResult
                        accepted = true
                        chatMicListening.value = false
                        if (result.text.isNotBlank()) {
                            appendToInput(result.text.trim())
                            setStatus("Transcribed — review and send", com.jnetai.assistant.ui.components.Tone.SUCCESS)
                        } else {
                            setStatus("No speech detected", com.jnetai.assistant.ui.components.Tone.INFO)
                        }
                    }
                    // provisional isFinal=false results are intentionally ignored
                },
                onState = { listening ->
                    if (seq == chatMicSeq) chatMicListening.value = listening
                    if (listening && seq == chatMicSeq) setStatus("Listening — speak now", com.jnetai.assistant.ui.components.Tone.INFO)
                }
            )
        } catch (t: Throwable) {
            Err.e(Err.STT_UNAVAILABLE, "Chat mic failed to start", t)
            chatMicListening.value = false
            setStatus("Could not start speech recognition", com.jnetai.assistant.ui.components.Tone.ERROR)
        }
    }

    fun stopChatMic() {
        chatMicSeq++ // invalidate any in-flight recogniser callback for this session
        runCatching { graph.stt.stopListening() }
        chatMicListening.value = false
        setStatus("Cancelled", com.jnetai.assistant.ui.components.Tone.INFO)
    }

    private fun appendToInput(text: String) {
        inputText.value = if (inputText.value.isBlank()) text else inputText.value.trimEnd() + " " + text
    }

    /**
     * Issue #7 — saves the given voice-assistant response text as a WAV audio
     * file in Downloads via the TTS synthesizer. Runs off the main thread.
     */
    fun saveVoiceResponseAsAudio(text: String, onResult: (Boolean, String) -> Unit) {
        if (text.isBlank()) {
            onResult(false, "No response to save")
            return
        }
        if (graph.tts.isReady) {
            com.jnetai.assistant.voice.VoiceClipSaver.save(getApplication(), graph.tts, text, onResult)
        } else {
            graph.tts.init { ready ->
                if (ready) {
                    voice.ttsReady = true
                    com.jnetai.assistant.voice.VoiceClipSaver.save(getApplication(), graph.tts, text, onResult)
                } else {
                    onResult(false, "Speech engine unavailable")
                }
            }
        }
    }

    // ---------- AGENT ----------
    fun runAgent(prompt: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val profile0 = selectedProfile() ?: run { setStatus("Select a profile", com.jnetai.assistant.ui.components.Tone.ERROR); return@launch }
            val profile = resolveProfileModel(profile0)
            usage.logActivity("agent", "Agent: $prompt")
            // construct tool list + system prompt, call provider streaming, if tools requested execute them with permissions
            val registry = com.jnetai.assistant.agent.ToolRegistry(
                getApplication(), permissions,
                onToolExecuted = { a -> viewModelScope.launch { db.agentDao().insert(a) } },
                rag = rag
            )
            val tools = registry.allTools().filter { t -> permissions.toolsEnabled() }
            val messages = listOf(
                ChatMessageInput("system", "You are J~Net AI Assistant's agent. To perform actions, call tools. Never claim an action succeeded unless a tool result confirms it. If you cannot do something, say so."),
                ChatMessageInput("user", prompt)
            )
            val provider = graph.providerFactory.create(profile) { secrets.get(profile.apiKeyRef) }
            val result = provider.chat(messages)
            onDone(result.text)
        }
    }

    // ---------- ONBOARDING / LOCK ----------
    fun completeOnboarding() {
        viewModelScope.launch {
            settings.setOnboardingDone()
            needsOnboarding.value = false
        }
    }

    /** True while a PIN is being verified/updated (PBKDF2 runs off the main thread). */
    val unlockBusy = MutableStateFlow(false)

    /**
     * Async PIN unlock. Simply unlocks the app when the PIN matches (default
     * or personal) — no forced-change screen. Secure mode is only ever set up
     * from Settings → Security (default PIN → new PIN → confirm → store hash).
     */
    fun unlockWithPin(pin: String, onResult: (Boolean) -> Unit) {
        if (unlockBusy.value) return
        unlockBusy.value = true
        Err.i("PIN unlock attempt starting")
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) {
                runCatching { lock.verifyPin(pin) }.getOrDefault(false)
            }
            Err.i(if (ok) "PIN accepted" else "PIN rejected")
            if (ok) markUnlocked()
            unlockBusy.value = false
            onResult(ok)
        }
    }

    fun markUnlocked() {
        Err.i("App unlocked")
        lock.markUnlocked()
        appLocked.value = false
    }

    /**
     * Enables Secure mode from Settings:
     *   1) the default PIN must be entered,
     *   2) a new personal PIN is captured + confirmed by the UI,
     *   3) only its PBKDF2 hash is stored, then protection turns on.
     * Returns an error message, or null on success.
     */
    fun enablePinSecurity(defaultPin: String, newPin: String, confirmedPin: String, onDone: (String?) -> Unit) {
        if (defaultPin != com.jnetai.assistant.data.security.AppLockManager.DEFAULT_PIN) {
            onDone("Enter the default PIN (${com.jnetai.assistant.data.security.AppLockManager.DEFAULT_PIN}) to enable")
            return
        }
        if (newPin.length < 4) { onDone("New PIN must be at least 4 characters"); return }
        if (newPin != confirmedPin) { onDone("New PIN and confirmation do not match"); return }
        if (unlockBusy.value) return
        unlockBusy.value = true
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) {
                runCatching {
                    lock.setPin(newPin)
                    true
                }.getOrDefault(false)
            }
            if (ok) {
                lock.isEnabled = true
                Err.i("Secure mode enabled (new PIN stored as hash)")
                setStatus("Secure mode enabled", com.jnetai.assistant.ui.components.Tone.SUCCESS)
            } else {
                Err.e(Err.LOCK_PIN_ERROR, "Failed to store new PIN")
            }
            unlockBusy.value = false
            onDone(if (ok) null else "Could not enable Secure mode — please try again")
        }
    }

    /** Turns Secure mode off from Settings. */
    fun disablePinSecurity(onDone: (Boolean) -> Unit) {
        Err.i("Secure mode disabled from Settings")
        lock.isEnabled = false
        lock.biometricEnabled = false
        lock.markUnlocked()
        appLocked.value = false
        onDone(true)
    }

    /** Turns Secure mode off entirely from the lock screen — requires the default PIN. */
    fun resetLockUsingDefaultPin(): Boolean {
        val ok = runCatching { lock.verifyPin(com.jnetai.assistant.data.security.AppLockManager.DEFAULT_PIN) }.getOrDefault(false)
        if (!ok) {
            Err.w("Reset attempt without default PIN rejected")
            return false
        }
        Err.i("Secure mode disabled via default PIN")
        lock.isEnabled = false
        lock.biometricEnabled = false
        lock.markUnlocked()
        return true
    }

    fun exportConversation(id: Long): String {
        val msgs = _messages.value
        return buildString {
            append("# Conversation\n\n")
            msgs.forEach { m -> append("**${m.role}**: ${m.content}\n\n") }
        }
    }

    /** Text of the currently open conversation (used by Share). */
    fun currentConversationText(): String {
        val title = _conversations.value.find { it.id == _activeConversationId.value }?.takeIf { it.id != 0L }
            ?: run {
                _conversations.value.maxByOrNull { it.updatedAt }?.takeIf { _messages.value.isNotEmpty() }
            }
        return buildString {
            append("# ${title?.title ?: "Conversation"}\n\n")
            _messages.value.forEach { m ->
                if (m.content.isNotBlank()) append("**${m.role}**: ${m.content}\n\n")
            }
        }
    }

    /**
     * Issue #5 — clears ALL conversation history (every mode) and the active
     * view. Confirmation is handled by the UI before this is called.
     */
    fun clearAllHistory() {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                val convs = firstOf(db.conversationDao().getAll())
                convs.forEach { c -> db.messageDao().deleteByConversation(c.id) }
                convs.forEach { db.conversationDao().delete(it.id) }
                convs.size
            }
            _activeConversationId.value = 0
            _messages.value = emptyList()
            loadData()
            setStatus("Cleared $count conversation(s)", com.jnetai.assistant.ui.components.Tone.INFO)
            usage.logActivity("settings", "Cleared all conversation history", "$count conversations")
        }
    }

    /**
     * Issue #5 — exports every conversation (all modes, every message) as a
     * readable markdown/text file to the given SAF Uri.
     */
    fun exportHistoryToUri(uri: Uri) {
        viewModelScope.launch {
            val body = withContext(Dispatchers.IO) {
                val convs = firstOf(db.conversationDao().getAll())
                val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT)
                buildString {
                    append("# J~Net AI Assistant — Conversation History\n")
                    append("Exported ${fmt.format(java.util.Date())}\n\n")
                    if (convs.isEmpty()) {
                        append("(no conversations yet)\n")
                    }
                    convs.forEachIndexed { i, c ->
                        append("---\n\n")
                        append("## ${i + 1}. ${c.title}\n")
                        append("Mode: ${c.mode.display} · Model: ${c.model.ifBlank { "—" }} · Updated: ${fmt.format(java.util.Date(c.updatedAt))}\n\n")
                        db.messageDao().getByConversationOnce(c.id).forEach { m ->
                            if (m.content.isNotBlank()) append("**${m.role}**: ${m.content}\n\n")
                        }
                    }
                }
            }
            try {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(body.toByteArray())
                } ?: throw IllegalStateException("No output stream")
                setStatus("History exported", com.jnetai.assistant.ui.components.Tone.SUCCESS)
            } catch (t: Throwable) {
                Err.e(Err.BACKUP_ERROR, "History export failed", t)
                setStatus("Could not export history", com.jnetai.assistant.ui.components.Tone.ERROR)
            }
        }
    }

    fun clearActivityLog() {
        viewModelScope.launch { usage.clearActivity(); setStatus("Activity log cleared") }
    }

    fun importLocalModel(name: String, uri: String, sizeBytes: Long) {
        viewModelScope.launch {
            val modelMgr = com.jnetai.assistant.ml.ModelManager(getApplication(), db)
            modelMgr.importModel(name = name, fileUri = uri, sizeBytes = sizeBytes)
            usage.logActivity("models", "Imported local model $name")
            setStatus("Model imported — configure context/threads in Models", com.jnetai.assistant.ui.components.Tone.SUCCESS)
        }
    }

    fun saveVoiceSettings(stt: String, tts: String, rate: Float, pitch: Float, autoSpeak: Boolean, liveMode: Boolean) {
        viewModelScope.launch {
            settings.setSttProvider(stt)
            settings.setTtsProvider(tts)
            settings.setTtsRate(rate)
            settings.setTtsPitch(pitch)
            settings.setAutoSpeak(autoSpeak)
            settings.setBool("voice.live_mode", liveMode)
            graph.tts.setRate(rate)
            graph.tts.setPitch(pitch)
            setStatus("Voice settings saved", com.jnetai.assistant.ui.components.Tone.SUCCESS)
            usage.logActivity("settings", "Voice settings updated")
        }
    }

    fun activeModelId() = _activeModelId.value
    fun selectActiveModel(id: Long) { viewModelScope.launch { db.modelDao().getAllOnce().forEach { m -> db.modelDao().update(m.copy(active = m.id == id)) }; _activeModelId.value = id } }
    fun removeModel(id: Long) { viewModelScope.launch { db.modelDao().delete(id) } }
    private val _activeModelId = MutableStateFlow(0L)
    fun setModelLoaded(id: Long, loaded: Boolean) { viewModelScope.launch { db.modelDao().getAllOnce().find { it.id == id }?.let { db.modelDao().update(it.copy(loaded = loaded)) } } }
}