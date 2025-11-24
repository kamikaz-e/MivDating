package dev.kamikaze.mivdating.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Document(
    val id: String,
    val title: String,
    val source: String,
    val type: DocumentType
)

@Serializable
enum class DocumentType {
    TXT, HTML
}

@Serializable
data class DocumentChunk(
    val id: String,
    val documentId: String,
    val content: String,
    val chunkIndex: Int,
    val startPosition: Int,
    val endPosition: Int
)

@Serializable
data class ChunkEmbedding(
    val chunkId: String,
    val documentId: String,
    val content: String,
    val embedding: List<Double>
)

@Serializable
data class VectorIndex(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val documents: List<Document>,
    val embeddings: List<ChunkEmbedding>
)