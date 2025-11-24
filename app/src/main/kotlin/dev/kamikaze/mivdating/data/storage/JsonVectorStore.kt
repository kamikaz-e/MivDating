package dev.kamikaze.mivdating.data.storage

import android.content.Context
import dev.kamikaze.mivdating.data.models.ChunkEmbedding
import dev.kamikaze.mivdating.data.models.Document
import dev.kamikaze.mivdating.data.models.VectorIndex
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.sqrt

class JsonVectorStore(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val indexFile: File
        get() = File(context.filesDir, "vector_index.json")

    private var cachedIndex: VectorIndex? = null

    fun saveIndex(documents: List<Document>, embeddings: List<ChunkEmbedding>) {
        val index = VectorIndex(
            documents = documents,
            embeddings = embeddings
        )
        cachedIndex = index
        indexFile.writeText(json.encodeToString(index))
    }

    fun loadIndex(): VectorIndex? {
        cachedIndex?.let { return it }
        
        return if (indexFile.exists()) {
            try {
                val index = json.decodeFromString<VectorIndex>(indexFile.readText())
                cachedIndex = index
                index
            } catch (e: Exception) {
                null
            }
        } else null
    }

    fun addToIndex(document: Document, embeddings: List<ChunkEmbedding>) {
        val currentIndex = loadIndex() ?: VectorIndex(
            documents = emptyList(),
            embeddings = emptyList()
        )
        
        val updatedIndex = currentIndex.copy(
            documents = currentIndex.documents + document,
            embeddings = currentIndex.embeddings + embeddings
        )
        
        cachedIndex = updatedIndex
        indexFile.writeText(json.encodeToString(updatedIndex))
    }

    fun searchSimilar(queryEmbedding: List<Double>, topK: Int = 5): List<SearchResult> {
        val index = loadIndex() ?: return emptyList()
        
        return index.embeddings
            .map { chunk ->
                SearchResult(
                    chunk = chunk,
                    score = cosineSimilarity(queryEmbedding, chunk.embedding)
                )
            }
            .sortedByDescending { it.score }
            .take(topK)
    }

    private fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator == 0.0) 0.0 else dotProduct / denominator
    }

    fun clear() {
        cachedIndex = null
        if (indexFile.exists()) {
            indexFile.delete()
        }
    }

    fun getStats(): IndexStats {
        val index = loadIndex()
        return IndexStats(
            documentsCount = index?.documents?.size ?: 0,
            chunksCount = index?.embeddings?.size ?: 0,
            indexSizeBytes = if (indexFile.exists()) indexFile.length() else 0
        )
    }
}

data class IndexStats(
    val documentsCount: Int,
    val chunksCount: Int,
    val indexSizeBytes: Long
)