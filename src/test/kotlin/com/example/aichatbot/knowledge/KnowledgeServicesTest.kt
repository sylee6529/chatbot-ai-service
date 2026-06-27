package com.example.aichatbot.knowledge

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KnowledgeServicesTest {
    @Test
    fun `chunker splits text by paragraphs and max length`() {
        val chunker = DocumentChunker()

        val chunks = chunker.chunk("짧음\n\n${"a".repeat(12)}", maxChars = 5)

        assertEquals(listOf("짧음", "aaaaa", "aaaaa", "aa"), chunks)
    }

    @Test
    fun `cosine similarity detects matching vectors`() {
        assertEquals(1.0, cosineSimilarity(listOf(1.0, 0.0), listOf(1.0, 0.0)))
        assertEquals(0.0, cosineSimilarity(listOf(1.0, 0.0), listOf(0.0, 1.0)))
    }

    @Test
    fun `fake embedding is deterministic`() {
        val client = FakeEmbeddingClient()

        val first = client.embed("AIChatbot 서비스 설명")
        val second = client.embed("AIChatbot 서비스 설명")

        assertEquals(first, second)
        assertTrue(first.any { it != 0.0 })
    }
}
