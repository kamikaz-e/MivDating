// chunking/TextChunker.kt
package dev.kamikaze.mivdating.data.chunking

import dev.kamikaze.mivdating.data.models.DocumentChunk
import java.util.UUID

data class ChunkingConfig(
    val chunkSize: Int = 512,           // размер чанка в символах
    val chunkOverlap: Int = 128,        // перекрытие между чанками
    val minChunkSize: Int = 100,        // минимальный размер чанка
    val separators: List<String> = listOf("\n\n", "\n", ". ", "! ", "? ", " ")
)

class TextChunker(
    private val config: ChunkingConfig = ChunkingConfig()
) {

    /**
     * Разбивает текст на семантически осмысленные чанки
     */
    fun chunkText(text: String, documentId: String): List<DocumentChunk> {
        val chunks = mutableListOf<DocumentChunk>()
        val cleanedText = cleanText(text)
        
        if (cleanedText.length <= config.chunkSize) {
            return listOf(
                DocumentChunk(
                    id = UUID.randomUUID().toString(),
                    documentId = documentId,
                    content = cleanedText,
                    chunkIndex = 0,
                    startPosition = 0,
                    endPosition = cleanedText.length
                )
            )
        }

        var currentPosition = 0
        var chunkIndex = 0

        while (currentPosition < cleanedText.length) {
            val endPosition = minOf(currentPosition + config.chunkSize, cleanedText.length)
            var chunkEnd = endPosition

            // Ищем лучшее место для разделения
            if (endPosition < cleanedText.length) {
                chunkEnd = findBestSplitPoint(cleanedText, currentPosition, endPosition)
            }

            val chunkContent = cleanedText.substring(currentPosition, chunkEnd).trim()

            if (chunkContent.length >= config.minChunkSize) {
                chunks.add(
                    DocumentChunk(
                        id = UUID.randomUUID().toString(),
                        documentId = documentId,
                        content = chunkContent,
                        chunkIndex = chunkIndex,
                        startPosition = currentPosition,
                        endPosition = chunkEnd
                    )
                )
                chunkIndex++
            }

            // Следующий чанк начинается с учётом перекрытия
            currentPosition = maxOf(
                currentPosition + 1,
                chunkEnd - config.chunkOverlap
            )
        }

        return chunks
    }

    private fun cleanText(text: String): String {
        return text
            .replace(Regex("\\s+"), " ")           // Множественные пробелы -> один
            .replace(Regex("\\n{3,}"), "\n\n")     // Множественные переносы -> два
            .trim()
    }

    private fun findBestSplitPoint(text: String, start: Int, end: Int): Int {
        // Ищем разделитель в обратном порядке приоритета
        for (separator in config.separators) {
            val searchStart = maxOf(start + config.minChunkSize, end - 100)
            val lastIndex = text.lastIndexOf(separator, end)
            if (lastIndex > searchStart) {
                return lastIndex + separator.length
            }
        }
        return end
    }

    /**
     * Разбивка с использованием предложений
     */
    fun chunkBySentences(text: String, documentId: String, sentencesPerChunk: Int = 5): List<DocumentChunk> {
        val sentences = splitIntoSentences(text)
        val chunks = mutableListOf<DocumentChunk>()
        
        var chunkIndex = 0
        var currentPosition = 0

        sentences.chunked(sentencesPerChunk).forEach { sentenceGroup ->
            val content = sentenceGroup.joinToString(" ")
            val endPosition = currentPosition + content.length

            chunks.add(
                DocumentChunk(
                    id = UUID.randomUUID().toString(),
                    documentId = documentId,
                    content = content,
                    chunkIndex = chunkIndex,
                    startPosition = currentPosition,
                    endPosition = endPosition
                )
            )

            chunkIndex++
            currentPosition = endPosition
        }

        return chunks
    }

    private fun splitIntoSentences(text: String): List<String> {
        return text.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}