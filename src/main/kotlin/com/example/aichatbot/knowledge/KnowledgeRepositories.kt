package com.example.aichatbot.knowledge

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface DemoDocumentRepository : JpaRepository<DemoDocument, Long> {
    fun findBySourceKeyAndDeletedAtIsNull(sourceKey: String): Optional<DemoDocument>
}

interface DocumentChunkRepository : JpaRepository<DocumentChunk, Long> {
    @Query(
        """
        select c from DocumentChunk c
        join fetch c.document d
        where d.sourceKey = :sourceKey
          and c.deletedAt is null
          and d.deletedAt is null
        order by c.chunkIndex asc
        """,
    )
    fun findByDocumentSourceKeyAndDeletedAtIsNullOrderByChunkIndexAsc(@Param("sourceKey") sourceKey: String): List<DocumentChunk>

    @Query(
        """
        select c from DocumentChunk c
        join fetch c.document d
        where c.deletedAt is null
          and d.deletedAt is null
          and c.embeddingModel = :embeddingModel
        order by c.createdAt desc
        """,
    )
    fun findByDeletedAtIsNullAndEmbeddingModelOrderByCreatedAtDesc(@Param("embeddingModel") embeddingModel: String): List<DocumentChunk>
}

interface ChatDocumentSourceRepository : JpaRepository<ChatDocumentSource, Long> {
    @Query(
        """
        select s from ChatDocumentSource s
        join fetch s.chat c
        where c.id in :chatIds
        order by s.id asc
        """,
    )
    fun findByChatIdInOrderByIdAsc(@Param("chatIds") chatIds: Collection<Long>): List<ChatDocumentSource>
}
