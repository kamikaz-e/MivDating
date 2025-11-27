package dev.kamikaze.mivdating.data.models

import dev.kamikaze.mivdating.data.storage.SearchResult

data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val sources: List<SearchResult> = emptyList() // Чанки-источники для RAG ответов
)
