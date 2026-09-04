package com.jnetai.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Result of a transcription. */
data class TranscriptResult(val text: String, val errorCode: String? = null, val errorMessage: String? = null)

/** States for the realtime voice state machine. */
enum class VoiceState { IDLE, LISTENING, TRANSCRIBING, THINKING, SPEAKING, INTERRUPTED, ERROR }

/**
 * Provider interface for speech-to-text. Currently backs onto Android's built-in
 * on-device/local speech recogniser, with the same interface usable for
 * remote/server STT providers later.
 */
interface SttProvider {
    /** Start listening; results delivered via callback. */
    fun startListening(onResult: (TranscriptResult) -> Unit, onState: (Boolean) -> Unit)
    fun stopListening() {}
    fun cancel() {}
    val supported: Boolean
}

/**
 * Android SpeechRecognizer-based STT. Works wherever Google/local speech
 * recognition is available. Detection of unavailability is handled gracefully.
 */
class AndroidSttProvider(private val context: Context) : SttProvider {
    private var recognizer: SpeechRecognizer? = null
    override val supported: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    override fun startListening(onResult: (TranscriptResult) -> Unit, onState: (Boolean) -> Unit) {
        if (!supported) {
            onResult(TranscriptResult("", "E0601", "Speech recognition is not available on this device"))
            return
        }
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { onState(true) }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { onState(false) }
                override fun onError(error: Int) {
                    val mapped = when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "E0602" to "Microphone permission needed"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "E0601" to "Speech service busy"
                        SpeechRecognizer.ERROR_NETWORK -> "E0601" to "Speech service network problem"
                        else -> "E0601" to "Speech recognition error ($error)"
                    }
                    onResult(TranscriptResult("", mapped.first, mapped.second))
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    onResult(TranscriptResult(text))
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { onResult(TranscriptResult(it)) }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer?.startListening(intent)
        } catch (t: Throwable) {
            onResult(TranscriptResult("", "E0601", "Speech recognition failed to start: ${t.message}"))
        }
    }

    override fun stopListening() { recognizer?.stopListening() }
    override fun cancel() {
        runCatching { recognizer?.cancel() }
        recognizer?.destroy()
    }
}