// indexing/IndexingService.kt
package dev.kamikaze.mivdating.data.indexing

import dev.kamikaze.mivdating.data.chunking.ChunkingConfig
import dev.kamikaze.mivdating.data.chunking.TextChunker
import dev.kamikaze.mivdating.data.models.ChunkEmbedding
import dev.kamikaze.mivdating.data.network.OllamaClient
import dev.kamikaze.mivdating.data.parser.DocumentParser
import dev.kamikaze.mivdating.data.storage.VectorDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

sealed class IndexingProgress {
    data class Parsing(val fileName: String) : IndexingProgress()
    data class Chunking(val fileName: String, val chunksCount: Int) : IndexingProgress()
    data class Embedding(val current: Int, val total: Int) : IndexingProgress()
    data class Saving(val chunksCount: Int) : IndexingProgress()
    data class Completed(val totalDocuments: Int, val totalChunks: Int) : IndexingProgress()
    data class Error(val message: String) : IndexingProgress()
}

class IndexingService(
    private val documentParser: DocumentParser,
    private val ollamaClient: OllamaClient,
    private val vectorDatabase: VectorDatabase,
    private val chunkingConfig: ChunkingConfig = ChunkingConfig()
) {
    private val chunker = TextChunker(chunkingConfig)

    /**
     * Индексирует документы из assets с прогрессом
     */
    fun indexDocuments(fileNames: List<String>): Flow<IndexingProgress> = flow {
        try {
            var totalChunks = 0
            var indexedDocuments = 0

            for (fileName in fileNames) {
                // 1. Парсинг документа
                emit(IndexingProgress.Parsing(fileName))
                val (document, text) = withContext(Dispatchers.IO) {
                    documentParser.parseFromAssets(fileName)
                }

                // Проверяем, существует ли уже документ с таким ID
                val documentExists = withContext(Dispatchers.IO) {
                    vectorDatabase.documentExistsById(document.id)
                }

                if (documentExists) {
                    // Пропускаем документ, если он уже проиндексирован
                    continue
                }

                // 2. Разбиение на чанки
                val chunks = chunker.chunkText(text, document.id)
                emit(IndexingProgress.Chunking(fileName, chunks.size))

                // 3. Сохранение документа
                withContext(Dispatchers.IO) {
                    vectorDatabase.insertDocument(document)
                }

                // 4. Генерация эмбеддингов
                val embeddings = mutableListOf<ChunkEmbedding>()
                chunks.forEachIndexed { index, chunk ->
                    emit(IndexingProgress.Embedding(index + 1, chunks.size))

                    val embedding = withContext(Dispatchers.IO) {
                        ollamaClient.embed(chunk.content)
                    }

                    embeddings.add(
                        ChunkEmbedding(
                            chunkId = chunk.id,
                            documentId = document.id,
                            content = chunk.content,
                            embedding = embedding
                        )
                    )
                }

                // 5. Сохранение эмбеддингов
                emit(IndexingProgress.Saving(embeddings.size))
                withContext(Dispatchers.IO) {
                    vectorDatabase.insertEmbeddings(embeddings)
                }

                totalChunks += embeddings.size
                indexedDocuments++
            }

            emit(IndexingProgress.Completed(indexedDocuments, totalChunks))

        } catch (e: Exception) {
            emit(IndexingProgress.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Поиск по запросу
     */
    suspend fun search(query: String, topK: Int = 5) = withContext(Dispatchers.IO) {
        val queryEmbedding = ollamaClient.embed(query)
        vectorDatabase.searchSimilar(queryEmbedding, topK)
    }
}