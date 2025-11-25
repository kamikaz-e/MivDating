package dev.kamikaze.mivdating

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kamikaze.mivdating.data.chunking.ChunkingConfig
import dev.kamikaze.mivdating.data.indexing.IndexingProgress
import dev.kamikaze.mivdating.data.indexing.IndexingService
import dev.kamikaze.mivdating.data.models.ChatMessage
import dev.kamikaze.mivdating.data.models.Document
import dev.kamikaze.mivdating.data.network.MessageRequest
import dev.kamikaze.mivdating.data.network.OllamaClient
import dev.kamikaze.mivdating.data.network.YandexGptClient
import dev.kamikaze.mivdating.data.parser.DocumentParser
import dev.kamikaze.mivdating.data.storage.VectorDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    // Общие данные
    val documentsCount: Int = 0,
    val chunksCount: Int = 0,
    val documents: List<Document> = emptyList(),

    // NO RAG чат
    val noRagMessages: List<ChatMessage> = emptyList(),
    val isNoRagLoading: Boolean = false,

    // RAG чат
    val ragMessages: List<ChatMessage> = emptyList(),
    val isRagLoading: Boolean = false,

    // Индексация
    val isIndexing: Boolean = false,
    val indexingProgress: String = "",
    val indexingPercent: Float = 0f,

    // Ошибки
    val error: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val ollamaClient = OllamaClient()
    private val yandexGptClient = YandexGptClient
    private val documentParser = DocumentParser(application)
    private val vectorDatabase = VectorDatabase(application)

    private val indexingService = IndexingService(
        documentParser = documentParser,
        ollamaClient = ollamaClient,
        vectorDatabase = vectorDatabase,
        chunkingConfig = ChunkingConfig(
            chunkSize = 512,
            chunkOverlap = 128
        )
    )

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    var commonInput by mutableStateOf("")
        private set

    init {
        loadStats()
    }

    fun updateCommonInput(text: String) {
        commonInput = text
    }

    private fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                documentsCount = vectorDatabase.getDocumentsCount(),
                chunksCount = vectorDatabase.getEmbeddingsCount(),
                documents = vectorDatabase.getAllDocuments()
            )
        }
    }

    /**
     * Отправка сообщения в оба режима одновременно
     */
    fun sendBothMessages() {
        if (commonInput.isBlank()) return

        val userMessage = commonInput
        commonInput = ""

        // Отправляем в оба режима параллельно
        viewModelScope.launch {
            launch { sendNoRagMessage(userMessage) }
            launch { sendRagMessage(userMessage) }
        }
    }

    /**
     * Отправка сообщения в NO RAG режиме
     */
    private fun sendNoRagMessage(userMessage: String) {
        if (userMessage.isBlank()) return

        val userChatMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = userMessage,
            isUser = true
        )

        _uiState.value = _uiState.value.copy(
            noRagMessages = _uiState.value.noRagMessages + userChatMessage,
            isNoRagLoading = true,
            error = null
        )

        viewModelScope.launch {
            try {
                // Формируем историю для API
                val history = _uiState.value.noRagMessages
                    .dropLast(1) // Исключаем текущее сообщение пользователя
                    .map { msg ->
                        MessageRequest.Message(
                            role = if (msg.isUser) "user" else "assistant",
                            text = msg.text
                        )
                    }

                val response = yandexGptClient.sendMessage(
                    userMessage = userMessage,
                    conversationHistory = history
                )

                val assistantMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = response.text,
                    isUser = false
                )

                _uiState.value = _uiState.value.copy(
                    noRagMessages = _uiState.value.noRagMessages + assistantMessage,
                    isNoRagLoading = false
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isNoRagLoading = false,
                    error = "Ошибка NO RAG: ${e.message}"
                )
            }
        }
    }

    /**
     * Отправка сообщения в RAG режиме
     */
    private fun sendRagMessage(userMessage: String) {
        if (userMessage.isBlank()) return

        // Проверяем наличие чанков перед отправкой
        if (_uiState.value.chunksCount == 0) {
            val userChatMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                text = userMessage,
                isUser = true
            )
            val errorMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                text = "⚠️ Документы не проиндексированы. Пожалуйста, сначала проиндексируйте документы.",
                isUser = false
            )
            _uiState.value = _uiState.value.copy(
                ragMessages = _uiState.value.ragMessages + userChatMessage + errorMessage
            )
            return
        }

        val userChatMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = userMessage,
            isUser = true
        )

        _uiState.value = _uiState.value.copy(
            ragMessages = _uiState.value.ragMessages + userChatMessage,
            isRagLoading = true,
            error = null
        )

        viewModelScope.launch {
            try {
                // 1. Поиск релевантных чанков
                val queryEmbedding = ollamaClient.embed(userMessage)
                val searchResults = vectorDatabase.searchSimilar(queryEmbedding, topK = 5)

                // 2. Формирование контекста из чанков
                val context =  userMessage + searchResults.joinToString("\n") { result ->
                    result.chunk.content
                }

                // 3. Формируем историю для API
                val history = _uiState.value.ragMessages
                    .dropLast(1) // Исключаем текущее сообщение пользователя
                    .map { msg ->
                        MessageRequest.Message(
                            role = if (msg.isUser) "user" else "assistant",
                            text = msg.text
                        )
                    }

                // 4. Запрос к LLM с контекстом
                val response = yandexGptClient.sendMessageWithContext(
                    userMessage = userMessage,
                    context = context,
                    conversationHistory = history
                )

                val assistantMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = response.text,
                    isUser = false
                )

                _uiState.value = _uiState.value.copy(
                    ragMessages = _uiState.value.ragMessages + assistantMessage,
                    isRagLoading = false
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRagLoading = false,
                    error = "Ошибка RAG: ${e.message}"
                )
            }
        }
    }

    /**
     * Очистка чата NO RAG
     */
    fun clearNoRagChat() {
        _uiState.value = _uiState.value.copy(
            noRagMessages = emptyList()
        )
    }

    /**
     * Очистка чата RAG
     */
    fun clearRagChat() {
        _uiState.value = _uiState.value.copy(
            ragMessages = emptyList()
        )
    }

    /**
     * Индексация книг
     */
    fun indexBooks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isIndexing = true,
                error = null,
                indexingProgress = "Начинаем индексацию..."
            )

            val files = listOf("book1.txt", "book2.html")

            indexingService.indexDocuments(files).collect { progress ->
                when (progress) {
                    is IndexingProgress.Parsing -> {
                        _uiState.value = _uiState.value.copy(
                            indexingProgress = "📖 Парсинг: ${progress.fileName}"
                        )
                    }
                    is IndexingProgress.Chunking -> {
                        _uiState.value = _uiState.value.copy(
                            indexingProgress = "✂️ Разбивка: ${progress.chunksCount} чанков"
                        )
                    }
                    is IndexingProgress.Embedding -> {
                        _uiState.value = _uiState.value.copy(
                            indexingProgress = "🧠 Эмбеддинг: ${progress.current}/${progress.total}",
                            indexingPercent = progress.current.toFloat() / progress.total
                        )
                    }
                    is IndexingProgress.Saving -> {
                        _uiState.value = _uiState.value.copy(
                            indexingProgress = "💾 Сохранение ${progress.chunksCount} векторов..."
                        )
                    }
                    is IndexingProgress.Completed -> {
                        val actualDocsCount = vectorDatabase.getDocumentsCount()
                        val actualChunksCount = vectorDatabase.getEmbeddingsCount()
                        val actualDocuments = vectorDatabase.getAllDocuments()

                        val message = if (progress.totalDocuments == 0) {
                            "✅ Все документы уже проиндексированы"
                        } else {
                            "✅ Готово! Добавлено: ${progress.totalDocuments} документов, ${progress.totalChunks} чанков"
                        }

                        _uiState.value = _uiState.value.copy(
                            isIndexing = false,
                            indexingProgress = message,
                            indexingPercent = 1f,
                            documentsCount = actualDocsCount,
                            chunksCount = actualChunksCount,
                            documents = actualDocuments
                        )
                    }
                    is IndexingProgress.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isIndexing = false,
                            error = progress.message,
                            indexingProgress = ""
                        )
                    }
                }
            }
        }
    }

    /**
     * Полная очистка: БД + история чатов
     */
    fun clearAll() {
        viewModelScope.launch {
            vectorDatabase.clearAll()
            _uiState.value = _uiState.value.copy(
                documentsCount = 0,
                chunksCount = 0,
                documents = emptyList(),
                ragMessages = emptyList(),
                noRagMessages = emptyList(),
                indexingProgress = "Всё очищено"
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        ollamaClient.close()
        yandexGptClient.close()
        vectorDatabase.close()
    }
}
