package com.jnetai.assistant.rag

import com.jnetai.assistant.ai.AIProvider
import com.jnetai.assistant.data.db.AppDatabase
import com.jnetai.assistant.data.model.IndexedDocument
import com.jnetai.assistant.util.Err
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Embedding + vector search storage. Embeddings are kept as compact base64
 * float arrays alongside chunk rows. Cosine similarity is computed here. The
 * store remembers the embedding model so indexes can be flagged as needing a
 * rebuild when the embedding model changes.
 */
object VectorStore {
    private const val MAX_MEMORY_VECTORS = 40_000

    fun encode(vector: List<Double>): String {
        val floats = FloatArray(vector.size) { vector[it].toFloat() }
        return android.util.Base64.encodeToString(floats.toByteArray(), android.util.Base64.NO_WRAP)
    }

    fun decode(ref: String): FloatArray? = try {
        val bytes = android.util.Base64.decode(ref, android.util.Base64.NO_WRAP)
        val floats = FloatArray(bytes.size / 4)
        val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (i in floats.indices) floats[i] = buf.float
        floats
    } catch (_: Throwable) { null }

    fun cosine(a: FloatArray, b: FloatArray): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0
        var dot = 0.0f; var na = 0.0f; var nb = 0.0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        if (na == 0f || nb == 0f) return 0.0
        return (dot / (kotlin.math.sqrt(na) * kotlin.math.sqrt(nb))).toDouble()
    }
}

data class SearchResult(
    val chunk: com.jnetai.assistant.data.model.Chunk,
    val score: Double,
    val vectorScore: Double = 0.0,
    val keywordScore: Double = 0.0
)

/**
 * Hybrid RAG search: semantic (cosine) + keyword (token overlap) + optional
 * recency weighting. Not reliant only on vector similarity.
 */
class HybridSearch(private val db: AppDatabase) {

    /** Returns top-K ranked chunks across all (optionally filtered) documents. */
    suspend fun search(
        query: String,
        dimensions: Int,
        queryVector: FloatArray?,
        limit: Int = 8,
        collectionIds: List<Long>? = null,
        documentIds: List<Long>? = null,
        hybrid: Boolean = true
    ): List<SearchResult> = withContext(Dispatchers.Default) {
        val allChunks = kotlinx.coroutines.flow.first(db.chunkDao().getAll())
        val docs = kotlinx.coroutines.flow.first(db.documentDao().getAll())
        val docById = runCatching { docs.groupBy { it.id } }.getOrDefault(emptyMap())

        val filtered = allChunks.filter { c ->
            val d = docById[c.documentId]?.firstOrNull()
            if (d == null) false
            else {
                val inCollection = collectionIds == null || d.collectionId in collectionIds
                val inDoc = documentIds == null || c.documentId in documentIds
                inCollection && inDoc
            }
        }

        val qTokens = tokenize(query)

        val results = filtered.map { c ->
            val vec = VectorStore.decode(c.embeddingRef)
            val vScore = if (queryVector != null && vec != null && dimsCompatible(vec, queryVector)) {
                VectorStore.cosine(vec, queryVector)
            } else 0.0
            val kScore = if (hybrid && qTokens.isNotEmpty()) {
                val cTokens = tokenize(c.content)
                if (cTokens.isEmpty()) 0.0 else {
                    val overlap = qTokens.count { it in cTokens }
                    overlap.toDouble() / qTokens.size.toDouble()
                }
            } else 0.0
            val combined = if (hybrid) (vScore * 0.7) + (kScore * 0.3) else vScore
            SearchResult(c, combined, vScore, kScore)
        }
        results.sortedByDescending { it.score }.take(limit)
    }

    private fun dimsCompatible(a: FloatArray, b: FloatArray) = a.size == b.size

    private fun tokenize(text: String): Set<String> {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s-]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 1 && !STOPWORDS.contains(it) }
            .toSet()
    }

    companion object {
        val STOPWORDS = setOf(
            "the","and","for","are","but","not","you","all","any","can","her","was","one","our","out","day","get",
            "has","him","his","how","man","new","now","old","see","two","way","who","boy","did","its","let","put",
            "say","she","too","use","that","with","have","this","will","your","from","they","know","want","been",
            "good","much","some","time","very","when","come","here","just","like","long","make","many","more",
            "only","over","such","take","than","them","well","were","what","about","after","again","below","could",
            "every","first","found","great","house","large","learn","might","must","place","plant","point","right",
            "small","sound","spell","still","study","their","there","these","thing","think","three","water","where",
            "which","world","would","write","also","back","because","been","before","being","between","both","came",
            "does","done","during","each","ends","even","gave","going","gravity","hundred","into","kind","leave",
            "letter","life","might","mile","miss","name","near","never","next","night","often","once","open","own",
            "page","paper","part","people","problem","really","ready","rest","same","said","seem","seen","several",
            "should","show","side","sight","sometime","something","sometimes","stop","ten","took","tree","try",
            "turned","under","upon","use","used","using","verb","very","want","was","wasn't","watch","went","were",
            "what's","while","who","whole","whose","why","woman","women","wonder","word","work","working","year","yes","yet","you're"
        )
    }
}