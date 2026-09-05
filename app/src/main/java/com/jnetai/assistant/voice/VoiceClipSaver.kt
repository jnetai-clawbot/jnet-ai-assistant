package com.jnetai.assistant.voice

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.jnetai.assistant.util.Err
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Issue #7 — saves a voice-assistant response (AI text) as a playable WAV
 * audio file in the Downloads folder.
 *
 * Writes the response to a WAV file first via the TTS synthesizer, then copies
 * it into Downloads:
 *  - Android 10+ (API 29+): via MediaStore Downloads (no permission needed).
 *  - Android 8/9 (API 26–28): direct write to /storage/emulated/0/Download;
 *    the caller must have WRITE_EXTERNAL_STORAGE granted first.
 */
object VoiceClipSaver {

    /**
     * Runs on a background thread. [onResult] is invoked with (success, pathOrError).
     * Returns immediately; results arrive via the callback.
     */
    fun save(context: Context, tts: TtsProvider, text: String, onResult: (Boolean, String) -> Unit) {
        if (text.isBlank()) {
            onResult(false, "No response to save")
            return
        }
        if (!tts.isReady) {
            onResult(false, "Speech engine not ready yet — try again in a moment")
            return
        }
        val fileName = "VoiceResponse-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())}.wav"
        Thread {
            val tempFile = File(context.cacheDir, fileName)
            try {
                val latch = CountDownLatch(1)
                var ok = false
                tts.synthesizeToFile(text, tempFile) { r -> ok = r; latch.countDown() }
                if (!latch.await(30, TimeUnit.SECONDS)) {
                    Err.e(Err.TTS_UNAVAILABLE, "Voice clip synthesis timed out")
                    onResult(false, "Synthesis timed out")
                    return@Thread
                }
                if (!ok || !tempFile.exists() || tempFile.length() < 44L) {
                    onResult(false, "Synthesis produced no audio")
                    return@Thread
                }
                val savedPath = copyToDownloads(context, tempFile, fileName)
                tempFile.delete()
                if (savedPath == null) {
                    Err.e(Err.TTS_UNAVAILABLE, "Voice clip copy to Downloads failed")
                    onResult(false, "Could not write to Downloads")
                } else {
                    Err.i("Voice clip saved: $savedPath")
                    onResult(true, savedPath)
                }
            } catch (t: Throwable) {
                Err.e(Err.TTS_UNAVAILABLE, "Voice clip save failed", t)
                tempFile.delete()
                onResult(false, t.message ?: "Save failed")
            }
        }.start()
    }

    private fun copyToDownloads(context: Context, src: File, name: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: return null
            val written = resolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            } ?: 0
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            if (written <= 0L) return null
            "$name (in Downloads)"
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)
            if (!dir.exists() && !dir.mkdirs()) return null
            val dest = File(dir, name)
            src.copyTo(dest, overwrite = true)
            dest.absolutePath
        }
    }.getOrElse { t ->
        Err.e(Err.TTS_UNAVAILABLE, "copyToDownloads failed", t)
        null
    }
}