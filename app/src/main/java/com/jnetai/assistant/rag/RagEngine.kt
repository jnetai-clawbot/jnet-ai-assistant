package com.jnetai.assistant.rag

import android.content.Context
import android.net.Uri
import com.jnetai.assistant.data.db.AppDatabase
import com.jnetai.assistant.data.model.IndexStatus
import com.jnetai.assistant.data.model.IndexedDocument
import com.jnetai.assistant.data.model.Chunk as ChunkEntity
import com.jnetai.assistant.data.model.DocCollection
import com.jnetai.assistant.document.DocumentParser
import com.jnetai.assistant.document.UnsupportedDocumentException
import com.jnetai.assistant.util.Err
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class RagSearchContext(
    val chunks: List<SearchResult>,
    val contextText: String
)

/**
 * Core RAG pipeline:
 * File → extraction → cleaning → chunking → embedding → local index → retrieval
 * Only retrieved chunks ever leave the device for the remote model.
 */
class RagEngine(
    private val context: Context,
    private val db: AppDatabase,
    private val embeddingProvider: suspend (List<String>) -> List<List<Double>>,
    private val embeddingModelName: () -> String
) {
    suspend fun createCollection(name: String): Long = db.collectionDao().insert(DocCollection(name = name))

    suspend fun collections(): Flow<List<DocCollection>> = db.collectionDao().getAll()

    /** Renames a collection. Blank names are rejected instead of clobbering the row. */
    suspend fun renameCollection(id: Long, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) {
            Err.w("Rename collection $id rejected: blank name")
            return
        }
        try {
            db.collectionDao().rename(id, clean)
            Err.i("Renamed collection $id -> '$clean'")
        } catch (t: Throwable) {
            Err.e(Err.RAG_COLLECTION_ERROR, "renameCollection failed for id=$id", t)
            throw t
        }
    }

    /**
     * Removes a collection and ALL of its documents + chunks from the RAG
     * index. The original files on the user's device are NEVER touched — only
     * the local database rows (search + RAG context) are cleared.
     */
    suspend fun deleteCollection(id: Long) {
        withContext(Dispatchers.IO) {
            try {
                val docs = db.documentDao().getByCollectionOnce(id)
                docs.forEach { d ->
                    db.chunkDao().deleteByDocument(d.id)
                    db.documentDao().delete(d.id)
                }
                db.collectionDao().delete(id)
                Err.i("Deleted collection $id with ${docs.size} document(s) from RAG (files on device untouched)")
            } catch (t: Throwable) {
                Err.e(Err.RAG_COLLECTION_ERROR, "deleteCollection failed for id=$id", t)
                throw t
            }
        }
    }

    suspend fun indexDocument(uri: Uri, name: String, mime: String, collectionId: Long, onProgress: (Float) -> Unit = {}) {
        withContext(Dispatchers.IO) {
            Err.i("Indexing $name (mime=$mime) into collection $collectionId")
            val size = try { DocumentParser.nameAndSize(context, uri).second } catch (_: Throwable) { 0L }
            val hash = try { DocumentParser.hashUri(context, uri) } catch (_: Throwable) { "unknown-${System.currentTimeMillis()}" }

            val existing = db.documentDao().getByHash(hash)
            if (existing != null) {
                Err.i("Duplicate document skipped: $name (already indexed as ${existing.name})")
                return@withContext
            }

            val docId = db.documentDao().insert(
                IndexedDocument(
                    collectionId = collectionId, name = name, mimeType = mime,
                    uri = uri.toString(), fileHash = hash, sizeBytes = size,
                    status = IndexStatus.INDEXING, embeddingModel = embeddingModelName()
                )
            )

            try {
                val parsed = DocumentParser.parse(context, uri, name, mime)
                if (parsed.text.isBlank()) {
                    throw UnsupportedDocumentException("The document contains no extractable text")
                }
                onProgress(0.2f)
                val chunks = Chunker.chunk(
                    parsed.text,
                    chunkSize = 800,      // configurable default
                    overlap = 120,
                    pageMap = parsed.sections
                )
                onProgress(0.35f)
                if (chunks.isEmpty()) throw UnsupportedDocumentException("No indexable chunks produced")

                onProgress(0.5f)
                val vectors = try {
                    embeddingProvider(chunks.map { it.text })
                } catch (t: Throwable) {
                    // A remote-embedding blip must never kill the import — index
                    // keyword-only so the document is still searchable. The failure
                    // is logged for diagnostics.
                    Err.e(Err.RAG_EMBED_ERROR, "Embedding failed for $name; indexing keyword-only", t)
                    emptyList()
                }
                onProgress(0.8f)

                if (vectors.isEmpty()) {
                    // remote embedding unavailable → store keyword-indexable chunks only
                    Err.w("Embedding unavailable; indexing keyword-only")
                    db.chunkDao().insertAll(chunks.map {
                        ChunkEntity(documentId = docId, content = it.text, page = it.page, section = it.section, embeddingRef = "")
                    })
                    db.documentDao().update(docById(docId).copy(status = IndexStatus.READY))
                    onProgress(1f)
                    return@withContext
                }
                if (vectors.size != chunks.size) {
                    Err.e(Err.RAG_EMBED_ERROR, "Embedding count mismatch (${vectors.size} vs ${chunks.size})")
                    throw IllegalStateException("Embedding count mismatch")
                }
                db.chunkDao().insertAll(chunks.mapIndexed { i, c ->
                    c.toEntity(docId, VectorStore.encode(vectors[i]))
                })
                db.documentDao().update(docById(docId).copy(status = IndexStatus.READY))
                onProgress(1f)
                Err.i("Indexed ${chunks.size} chunks for $name")
            } catch (t: Throwable) {
                Err.e(Err.RAG_INDEX_ERROR, "Indexing failed for $name", t)
                db.documentDao().update(docById(docId).copy(status = IndexStatus.FAILED))
                throw t
            }
        }
    }

    private fun TextChunk.toEntity(docId: Long, emb: String) =
        ChunkEntity(documentId = docId, content = text, page = page, section = section, embeddingRef = emb)

    private suspend fun docById(id: Long): IndexedDocument = db.documentDao().getById(id) ?: IndexedDocument(id = id)

    /**
     * Performs hybrid search and assembles a context string for injection into
     * the AI request. Returns both so the UI can render citations.
     */
    suspend fun searchRag(
        query: String,
        collectionIds: List<Long>?,
        documentIds: List<Long>?,
        limit: Int = 8,
        hybrid: Boolean = true,
        dimQuery: FloatArray? = null
    ): RagSearchContext {
        val chunks = HybridSearch(db).search(
            query = query, dimensions = 0, queryVector = dimQuery,
            limit = limit, collectionIds = collectionIds, documentIds = documentIds, hybrid = hybrid
        )
        if (chunks.isEmpty()) return RagSearchContext(emptyList(), "")
        val ctx = buildString {
            chunks.forEachIndexed { i, r ->
                val doc = db.documentDao().getById(r.chunk.documentId)
                append("[DOC ${i + 1}: ${doc?.name ?: "doc"}${if (r.chunk.page > 0) " page ${r.chunk.page}" else ""}]\n")
                append(r.chunk.content)
                append("\n\n")
            }
        }
        return RagSearchContext(chunks, ctx.trim())
    }

    suspend fun deleteDocument(docId: Long) {
        db.chunkDao().deleteByDocument(docId)
        db.documentDao().delete(docId)
    }

    suspend fun getChunks(docId: Long): List<ChunkEntity> = db.chunkDao().getByDocument(docId)
    suspend fun documents(): Flow<List<IndexedDocument>> = db.documentDao().getAll()
    suspend fun documentById(id: Long): IndexedDocument? = db.documentDao().getById(id)
}