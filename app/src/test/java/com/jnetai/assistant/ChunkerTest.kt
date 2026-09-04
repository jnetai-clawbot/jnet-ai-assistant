package com.jnetai.assistant

import com.jnetai.assistant.rag.Chunker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkerTest {

    @Test
    fun `empty text produces no chunks`() {
        assertTrue(Chunker.chunk("").isEmpty())
        assertTrue(Chunker.chunk("   \n  \n").isEmpty())
    }

    @Test
    fun `short text produces a single chunk`() {
        val chunks = Chunker.chunk("Hello world this is a short document.")
        assertEquals(1, chunks.size)
    }

    @Test
    fun `long text is split into multiple chunks`() {
        val text = (1..2000).joinToString(" ") { "word$it" }
        val chunks = Chunker.chunk(text, chunkSize = 500, overlap = 100)
        assertTrue("expected >1 chunk, got ${chunks.size}", chunks.size > 1)
        chunks.forEach { assertTrue(it.text.isNotBlank()) }
    }

    @Test
    fun `chunks never exceed the configured size by much`() {
        val text = (1..3000).joinToString(" ") { "pad${it % 7}" }
        val chunks = Chunker.chunk(text, chunkSize = 800, overlap = 120)
        assertTrue(chunks.all { it.text.length <= 800 * 2 })
    }

    @Test
    fun `overlap carries trailing text between chunks`() {
        val text = (1..1000).joinToString(" ") { "word$it" }
        val chunks = Chunker.chunk(text, chunkSize = 300, overlap = 80)
        if (chunks.size > 1) {
            val a = chunks[0].text
            val b = chunks[1].text
            // overlap region should share tokens
            val aTail = a.split(" ").takeLast(10).joinToString(" ")
            assertTrue(b.contains(aTail.split(" ")[1]))
        }
    }
}