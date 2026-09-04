package com.jnetai.assistant.voice

import com.jnetai.assistant.ai.AIProvider
import com.jnetai.assistant.ai.ChatEngine
import com.jnetai.assistant.ai.ChatMessageInput
import com.jnetai.assistant.ai.EngineEvent
import com.jnetai.assistant.ai.SendRequest
import com.jnetai.assistant.util.Err
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class VoiceTurn(
    val transcript: String,
    val response: String,
    val sources: List<com.jnetai.assistant.data.model.ChatSource>,
    val interrupted: Boolean = false
)

/**
 * Real-time voice assistant session manager.
 *
 * State machine: IDLE → LISTENING → TRANSCRIBING → THINKING → SPEAKING →
 * IDLE, with INTERRUPTED and ERROR as transient states. Transitions are
 * deterministic and guarded by a mutex to prevent duplicate requests caused
 * by rapid microphone presses.
 *
 * Pipeline: Mic → STT → AI → streamed response → TTS → speaker. If the user
 * starts speaking while TTS plays, TTS stops and the previous response is
 * cancelled where possible.
 */
class VoiceSessionManager(
    private val scope: CoroutineScope,
    private val stt: SttProvider,
    private val tts: TtsProvider,
    private val engine: ChatEngine
) {
    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _currentTurn = MutableStateFlow<VoiceTurn?>(null)
    val currentTurn: StateFlow<VoiceTurn?> = _currentTurn.asStateFlow()

    private val _micActive = MutableStateFlow(false)
    val micActive: StateFlow<Boolean> = _micActive.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _streamingResponse = MutableStateFlow("")
    val streamingResponse: StateFlow<String> = _streamingResponse.asStateFlow()

    private var sttJob: Job? = null
    private var aiJob: Job? = null
    private var ttsJob: Job? = null
    private val mutex = Mutex()

    // callbacks wired by the ViewModel
    private var conversationFactory: (suspend (String) -> List<ChatMessageInput>)? = null
    private var providerFactory: (() -> AIProvider)? = null

    fun wire(
        conversation: suspend (String) -> List<ChatMessageInput>,
        provider: () -> AIProvider
    ) {
        conversationFactory = conversation
        providerFactory = provider
    }

    fun startListening() {
        scope.launch {
            mutex.withLock {
                interruptTtsInternal()
                if (_state.value == VoiceState.LISTENING || _state.value == VoiceState.TRANSCRIBING) {
                    Err.w("Voice already listening — duplicate press ignored")
                    return@withLock
                }
                _state.value = VoiceState.LISTENING
                _micActive.value = true
                _partialTranscript.value = ""
                _streamingResponse.value = ""
                stt.startListening(
                    onResult = { result -> scope.launch { handleSttResult(result) } },
                    onState = { listening -> _micActive.value = listening }
                )
            }
        }
    }

    fun stopListening() {
        stt.stopListening()
        _micActive.value = false
        if (_state.value == VoiceState.LISTENING) _state.value = VoiceState.IDLE
    }

    fun cancel() {
        stt.cancel()
        aiJob?.cancel()
        ttsJob?.cancel()
        tts.stop()
        _micActive.value = false
        _state.value = VoiceState.IDLE
    }

    private fun interruptTtsInternal() {
        ttsJob?.cancel()
        tts.stop()
        ttsJob = null
        if (_state.value == VoiceState.SPEAKING) {
            _state.value = VoiceState.INTERRUPTED
        }
    }

    private suspend fun handleSttResult(result: TranscriptResult) {
        if (result.errorCode != null) {
            _state.value = VoiceState.ERROR
            Err.e(result.errorCode, "STT error: ${result.errorMessage}")
            return
        }
        val text = result.text.trim()
        if (text.isEmpty()) {
            _state.value = VoiceState.IDLE
            _micActive.value = false
            return
        }
        _state.value = VoiceState.TRANSCRIBING
        _partialTranscript.value = text
        _micActive.value = false
        sttJob?.cancel()
        runAiTurn(text)
    }

    private fun runAiTurn(text: String) {
        val provider = providerFactory?.invoke() ?: run {
            _state.value = VoiceState.ERROR
            return
        }
        val conversation = conversationFactory ?: run {
            _state.value = VoiceState.ERROR
            return
        }
        _state.value = VoiceState.THINKING
        _streamingResponse.value = ""
        aiJob = scope.launch {
            try {
                val messages = conversation(text)
                val event = engine.run(SendRequest(provider, messages)) { delta ->
                    _streamingResponse.value += delta
                }
                when (event) {
                    is EngineEvent.Done -> {
                        _currentTurn.value = VoiceTurn(text, event.fullText, event.sources)
                        if (event.fullText.isNotBlank() && ttsReady) {
                            speakResponse(event.fullText)
                        } else {
                            _state.value = VoiceState.IDLE
                        }
                    }
                    is EngineEvent.Failed -> {
                        _state.value = VoiceState.ERROR
                        Err.e(event.code, "Voice AI failed: ${event.message}")
                    }
                    is EngineEvent.ToolRequested -> {
                        _state.value = VoiceState.ERROR
                        Err.w("Tool requests in voice mode are not auto-executed")
                    }
                    else -> {}
                }
            } catch (e: CancellationException) {
                _state.value = VoiceState.INTERRUPTED
            } catch (t: Throwable) {
                Err.e(Err.API_STREAM_ERROR, "Voice turn failed", t)
                _state.value = VoiceState.ERROR
            }
        }
    }

    var ttsReady: Boolean = false

    private fun speakResponse(text: String) {
        _state.value = VoiceState.SPEAKING
        ttsJob = scope.launch {
            tts.speak(
                text,
                onStart = { if (_state.value != VoiceState.SPEAKING) _state.value = VoiceState.SPEAKING },
                onDone = {
                    _state.value = VoiceState.IDLE
                    ttsJob = null
                }
            )
        }
    }
}