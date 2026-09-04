package com.jnetai.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.jnetai.assistant.util.Err
import java.util.Locale

/**
 * Provider interface for text-to-speech; Android TTS is the bundled provider.
 * Exposing the same interface keeps future providers (server TTS) pluggable.
 */
interface TtsProvider {
    fun init(onReady: (Boolean) -> Unit)
    fun speak(text: String, onStart: () -> Unit, onDone: () -> Unit): Boolean
    fun stop()
    fun setRate(rate: Float)
    fun setPitch(pitch: Float)
    fun setLanguage(lang: String)
}

class AndroidTtsProvider(private val context: Context) : TtsProvider {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingRate = 1.0f
    private var pendingPitch = 1.0f
    private var pendingLang = "en"

    override fun init(onReady: (Boolean) -> Unit) {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                tts?.language = Locale.getDefault()
                if (pendingRate != 1.0f) tts?.setSpeechRate(pendingRate)
                if (pendingPitch != 1.0f) tts?.setPitch(pendingPitch)
                onReady(true)
            } else {
                Err.e(Err.TTS_UNAVAILABLE, "TTS init failed with status $status")
                onReady(false)
            }
        }
    }

    override fun speak(text: String, onStart: () -> Unit, onDone: () -> Unit): Boolean {
        if (!ready || tts == null) {
            Err.e(Err.TTS_UNAVAILABLE, "TTS not ready — speak ignored")
            return false
        }
        currentUtteranceId = "utt-${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { if (utteranceId == currentUtteranceId) onStart() }
            override fun onDone(utteranceId: String?) { if (utteranceId == currentUtteranceId) onDone() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == currentUtteranceId) { onDone(); Err.w("TTS onError for $utteranceId") }
            }
        })
        val r = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, currentUtteranceId) ?: TextToSpeech.ERROR
        return r == TextToSpeech.SUCCESS
    }

    private var currentUtteranceId: String? = null

    override fun stop() { tts?.stop() }
    override fun setRate(rate: Float) { pendingRate = rate; if (ready) tts?.setSpeechRate(rate) }
    override fun setPitch(pitch: Float) { pendingPitch = pitch; if (ready) tts?.setPitch(pitch) }
    override fun setLanguage(lang: String) {
        pendingLang = lang
        if (ready) tts?.language = Locale.forLanguageTag(lang)
    }

    fun destroy() { tts?.stop(); tts?.shutdown() }
}