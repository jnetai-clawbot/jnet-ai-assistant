package com.jnetai.assistant.ml

import android.content.Context
import com.jnetai.assistant.data.db.AppDatabase
import com.jnetai.assistant.data.model.LocalModel
import com.jnetai.assistant.util.Err
import kotlinx.coroutines.flow.Flow

data class HardwareAccel(
    val cpu: Boolean = true,
    val gpu: Boolean = false,
    val nna: Boolean = false
)

/** Detects hardware capabilities for local inference. */
fun detectHardware(): HardwareAccel {
    val arm64 = runCatching {
        android.os.Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
    }.getOrDefault(false)
    // GPU/NNA availability is conservative. On modern devices Vulkan/ArmNN may
    // be present; we expose CPU always and GPU when arm64 with >= Android 10.
    val gpu = arm64 && android.os.Build.VERSION.SDK_INT >= 29
    return HardwareAccel(cpu = true, gpu = gpu, nna = gpu && android.os.Build.VERSION.SDK_INT >= 31)
}

/**
 * Model manager: import local model files, inspect metadata, activate/
 * deactivate, choose backend + threads + GPU layers. Used with the storage
 * access framework; no hard-coded storage location is assumed.
 */
class ModelManager(private val context: Context, private val db: AppDatabase) {

    fun models(): Flow<List<LocalModel>> = db.modelDao().getAll()

    suspend fun importModel(
        name: String, fileUri: String,
        sizeBytes: Long, contextLength: Int = 4096,
        quantisation: String = "unknown", backend: String = "llama.cpp",
        threads: Int = 4, gpuLayers: Int = 0
    ): Long {
        val memoryEstimate = estimateMemoryMb(sizeBytes, contextLength, quantisation)
        return db.modelDao().insert(
            LocalModel(
                name = name, fileUri = fileUri, sizeBytes = sizeBytes,
                contextLength = contextLength, quantisation = quantisation,
                backend = backend, threads = threads, gpuLayers = gpuLayers,
                memoryEstimateMb = memoryEstimate
            )
        )
    }

    suspend fun update(m: LocalModel) = db.modelDao().update(m)

    suspend fun activate(id: Long) {
        // deactivate all, then set the chosen model active
        db.modelDao().getAllOnce().forEach { m ->
            val active = m.id == id
            if (m.active != active) db.modelDao().update(m.copy(active = active))
        }
    }

    suspend fun remove(id: Long) = db.modelDao().delete(id)

    suspend fun setLoaded(id: Long, loaded: Boolean) {
        db.modelDao().getAllOnce().find { it.id == id }?.let { db.modelDao().update(it.copy(loaded = loaded)) }
    }

    /** Rough memory estimate in MB for the metadata card (never crashes app). */
    private fun estimateMemoryMb(sizeBytes: Long, ctx: Int, quant: String): Int {
        val fileMb = sizeBytes / (1024 * 1024)
        val overhead = when (quant.lowercase()) {
            "q4", "q4_0", "q4_1", "q4_k" -> 1.25
            "q5", "q5_0", "q5_1", "q5_k" -> 1.4
            "q6", "q8", "q8_0" -> 1.7
            "f16" -> 2.0
            "f32" -> 4.0
            else -> 1.5
        }
        val kvMb = (ctx / 1000) * 16
        return (fileMb * overhead).toInt() + kvMb + 128
    }

    /** Loads a model if memory estimate permits; warns otherwise (never crashes). */
    suspend fun loadModel(id: Long, availableMemMb: Long, onWarn: (String) -> Unit): Boolean {
        val model = db.modelDao().getAllOnce().find { it.id == id } ?: return false
        if (model.memoryEstimateMb > availableMemMb) {
            onWarn("Model ${model.name} may need ${model.memoryEstimateMb} MB but only ~$availableMemMb MB is available. Loading may fail.")
            Err.e(Err.LOCAL_MODEL_MEM, "Low memory for ${model.name}")
        }
        setLoaded(id, true)
        return true
    }
}