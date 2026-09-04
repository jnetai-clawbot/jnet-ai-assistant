package com.jnetai.assistant.document

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.jnetai.assistant.data.AppGraph
import com.jnetai.assistant.util.Err
import java.util.concurrent.CancellationException

/**
 * Background document indexing via WorkManager. Runs the full pipeline
 * (parse → chunk → embed → store) off the main thread. Restarting after
 * interruption naturally resumes because indexing is idempotent by file hash.
 */
class IndexWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val uri = runCatching { android.net.Uri.parse(inputData.getString("uri")) }.getOrNull()
            ?: return Result.failure()
        val name = inputData.getString("name") ?: "document"
        val mime = inputData.getString("mime") ?: ""
        val collId = inputData.getLong("collectionId", 0)
        if (collId <= 0) return Result.failure()

        return try {
            val graph = AppGraph.get(applicationContext)
            graph.rag.indexDocument(uri, name, mime, collId)
            graph.usage.logActivity("RAG", "Indexed $name")
            Result.success()
        } catch (e: CancellationException) {
            Err.w("Indexing cancelled: $name")
            Result.failure()
        } catch (t: Throwable) {
            Err.e(Err.RAG_INDEX_ERROR, "IndexWorker failed for $name", t)
            Result.failure()
        }
    }
}