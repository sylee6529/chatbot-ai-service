package com.example.aichatbot.knowledge

import com.example.aichatbot.chat.Chat
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "demo_documents")
class DemoDocument(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, length = 255)
    var title: String,

    @Column(name = "source_key", nullable = false, unique = true, length = 100)
    var sourceKey: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
)

@Entity
@Table(name = "document_chunks")
class DocumentChunk(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    var document: DemoDocument,

    @Column(name = "chunk_index", nullable = false)
    var chunkIndex: Int,

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String,

    @Column(columnDefinition = "TEXT")
    var embedding: String? = null,

    @Column(name = "embedding_model", length = 100)
    var embeddingModel: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
)

@Entity
@Table(name = "chat_document_sources")
class ChatDocumentSource(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    var chat: Chat,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_chunk_id", nullable = false)
    var documentChunk: DocumentChunk,

    @Column(name = "document_title", nullable = false, length = 255)
    var documentTitle: String,

    @Column(name = "chunk_index", nullable = false)
    var chunkIndex: Int,

    @Column
    var score: Double? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
