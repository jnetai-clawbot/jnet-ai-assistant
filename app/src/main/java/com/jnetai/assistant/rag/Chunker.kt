package com.jnetai.assistant.rag

data class TextChunk(val text: String, val page: Int, val section: String)

/**
 * Splits extracted document text into overlapping chunks for embedding.
 * Splits on paragraph/newline boundaries first, then falls back to word
 * boundaries; never splits mid-word when avoidable.
 */
object Chunker {

    fun chunk(
        documentText: String,
        chunkSize: Int = 1000,
        overlap: Int = 150,
        pageMap: Map<Int, String> = emptyMap()
    ): List<TextChunk> {
        if (documentText.isBlank()) return emptyList()
        val size = chunkSize.coerceAtLeast(100)
        val ov = overlap.coerceIn(0, size / 2)

        val lines = documentText.split('\n')
        val paragraphs = mutableListOf<String>()
        val current = StringBuilder()
        val paraPage = mutableListOf<Pair<String, Int>>()

        // Determine page boundaries for page number assignment when pages given
        val pageOffsets = pageMapToOffsets(pageMap)

        var cumulative = 0
        for (line in lines) {
            if (line.isBlank()) {
                if (current.isNotBlank()) {
                    val page = findPage(pageOffsets, cumulative)
                    paragraphs += current.toString()
                    paraPage += current.toString() to page
                }
                current.clear()
                cumulative += line.length + 1
                continue
            }
            current.append(if (current.isEmpty()) line else "\n$line")
            cumulative += line.length + 1
        }
        if (current.isNotBlank()) {
            paraPage += current.toString() to findPage(pageOffsets, cumulative)
        }

        val result = mutableListOf<TextChunk>()
        val single = StringBuilder()
        var page = paraPage.firstOrNull()?.second ?: 0
        var sinceLast = 0
        val flush = { builder: StringBuilder, pg: Int ->
            if (builder.isNotBlank()) result += TextChunk(builder.toString().trim(), pg, "")
            builder.setLength(0)
        }

        for ((p, pPage) in paraPage) {
            if (single.isNotEmpty() && single.length + p.length > size) {
                flush(single, page)
                // overlap: re-add the trailing portion of the previous paragraph
                if (single.isNotBlank() || result.isNotEmpty()) {
                    // resolved below via overlapRetain
                }
                single.setLength(0)
                sinceLast = 0
            }
            if ((single.isEmpty() || single.last() != '\n') && single.isNotEmpty()) single.append('\n')
            single.append(p)
            page = pPage
            sinceLast += p.length
            if (single.length >= size) {
                flush(single, page)
            }
        }
        flush(single, page)

        if (result.isEmpty()) return emptyList()

        // Apply overlap between consecutive chunks: carry the tail of the previous chunk
        val final = mutableListOf<TextChunk>()
        for (i in result.indices) {
            val ch = result[i]
            if (final.isEmpty() || ov == 0) {
                final += ch
                continue
            }
            val prev = final.last().text
            val words = prev.split(' ')
            val tail = words.takeLast((ov / 10)).joinToString(" ")
            val merged = if (tail.length in 10..size) tail + "\n\n" + ch.text else ch.text
            final += TextChunk(merged.trim(), ch.page, ch.section)
        }
        return final
    }

    private fun pageMapToOffsets(pageMap: Map<Int, String>): List<Pair<Int, Int>> {
        // Rough: each page text -> cumulative character offset
        var cum = 0
        return pageMap.entries.sortedBy { it.key }.map { (p, text) ->
            val start = cum
            cum += text.length
            start to p
        }
    }

    private fun findPage(offsets: List<Pair<Int, Int>>, charOffset: Int): Int {
        var page = 0
        for ((start, p) in offsets) {
            if (charOffset >= start) page = p else break
        }
        return page
    }
}