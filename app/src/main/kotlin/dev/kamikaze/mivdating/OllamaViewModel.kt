package dev.kamikaze.mivdating

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kamikaze.mivdating.data.chunking.ChunkingConfig
import dev.kamikaze.mivdating.data.filtering.FilterConfig
import dev.kamikaze.mivdating.data.filtering.FilteredResults
import dev.kamikaze.mivdating.data.indexing.IndexingProgress
import dev.kamikaze.mivdating.data.indexing.IndexingService
import dev.kamikaze.mivdating.data.models.Document
import dev.kamikaze.mivdating.data.network.OllamaClient
import dev.kamikaze.mivdating.data.parser.DocumentParser
import dev.kamikaze.mivdating.data.storage.ChatHistoryRepository
import dev.kamikaze.mivdating.data.storage.OllamaSettings
import dev.kamikaze.mivdating.data.storage.SearchResult
import dev.kamikaze.mivdating.data.storage.VectorDatabase
import dev.kamikaze.mivdating.utils.OllamaUrlHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RAGUiState(
    val isIndexing: Boolean = false,
    val isSearching: Boolean = false,
    val progress: String = "",
    val progressPercent: Float = 0f,
    val documentsCount: Int = 0,
    val chunksCount: Int = 0,
    val documents: List<Document> = emptyList(),

    // Результаты без фильтрации
    val searchResults: List<SearchResult> = emptyList(),

    // Результаты с фильтрацией
    val filteredResults: FilteredResults? = null,

    // Настройки фильтра
    val filterThreshold: Float = 0.5f,
    val useFilter: Boolean = false,
    val useLengthBoost: Boolean = false,

    // Режим сравнения
    val comparisonMode: Boolean = false,

    // Чат с историей и RAG
    val chatMessages: List<dev.kamikaze.mivdating.data.models.ChatMessage> = emptyList(),
    val currentInput: String = "",
    val isGenerating: Boolean = false,

    // Диалог с источником
    val showSourceDialog: Boolean = false,
    val selectedSource: SearchResult? = null,

    // Диалог настроек
    val showSettingsDialog: Boolean = false,

    // Режим оптимизации для Jetpack Compose
    val useOptimizedComposeMode: Boolean = true,

    val error: String? = null,
    val ollamaAvailable: Boolean = false,
    val ollamaUrl: String = "http://130.49.153.154:8000",
    val connectionInstructions: String = ""
)

class RAGViewModel(application: Application) : AndroidViewModel(application) {

    private val ollamaSettings = OllamaSettings(application)
    private val ollamaClient = OllamaClient() // URL будет обновлен после загрузки настроек
    private val documentParser = DocumentParser(application)
    private val vectorDatabase = VectorDatabase(application)
    private val chatHistoryRepository = ChatHistoryRepository(application)

    private val indexingService = IndexingService(
        documentParser = documentParser,
        ollamaClient = ollamaClient,
        vectorDatabase = vectorDatabase,
        chunkingConfig = ChunkingConfig(
            chunkSize = 512,
            chunkOverlap = 128
        )
    )

    private val _uiState = MutableStateFlow(RAGUiState())
    val uiState: StateFlow<RAGUiState> = _uiState.asStateFlow()

    var searchQuery by mutableStateOf("")
        private set

    init {
        loadOllamaSettings()
        checkOllamaConnection()
        loadChunksAndVectors()
        loadChatHistory()
        autoIndexIfNeeded()
    }

    /**
     * Загружает настройки Ollama и обновляет UI и клиент
     */
    private fun loadOllamaSettings() {
        viewModelScope.launch {
            ollamaSettings.ollamaUrl.collect { url ->
                // Обновляем URL в клиенте
                ollamaClient.updateBaseUrl(url)

                val instructions = OllamaUrlHelper.getConnectionInstructions(getApplication())
                _uiState.value = _uiState.value.copy(
                    ollamaUrl = url,
                    connectionInstructions = instructions
                )
                android.util.Log.d("RAGViewModel", "Ollama URL updated: $url")

                // Перепроверяем подключение после смены URL
                checkOllamaConnection()
            }
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    fun updateCurrentInput(input: String) {
        _uiState.value = _uiState.value.copy(currentInput = input)
    }

    /**
     * Обновляет URL Ollama сервера
     */
    fun updateOllamaUrl(newUrl: String) {
        viewModelScope.launch {
            try {
                // Сохраняем URL в настройки
                ollamaSettings.setOllamaUrl(newUrl)
                android.util.Log.d("RAGViewModel", "Ollama URL saved: $newUrl")
            } catch (e: Exception) {
                android.util.Log.e("RAGViewModel", "Error saving Ollama URL", e)
                _uiState.value = _uiState.value.copy(
                    error = "Ошибка сохранения URL: ${e.message}"
                )
            }
        }
    }

    private fun checkOllamaConnection() {
        viewModelScope.launch {
            val available = try {
                val isOllamaAvailable = ollamaClient.isAvailable()
                if (isOllamaAvailable) {
                    // Проверяем доступность модели и пытаемся найти альтернативу
                    val isModelAvailable = ollamaClient.isModelAvailable()
                    if (!isModelAvailable) {
                        android.util.Log.w(
                            "RAGViewModel",
                            "Ollama is available but model qwen3:14b is not found"
                        )
                        // Пытаемся найти альтернативную модель
                        val alternative = ollamaClient.findQwen14bModel()
                        if (alternative != null) {
                            android.util.Log.i(
                                "RAGViewModel",
                                "Found alternative model: $alternative"
                            )
                        } else {
                            val allModels = ollamaClient.getAvailableModels()
                            android.util.Log.w("RAGViewModel", "Available models: $allModels")
                        }
                    }
                    isOllamaAvailable
                } else {
                    false
                }
            } catch (e: Exception) {
                android.util.Log.e("RAGViewModel", "Error checking Ollama connection", e)
                false
            }
            _uiState.value = _uiState.value.copy(ollamaAvailable = available)
        }
    }

    private fun loadChunksAndVectors() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                documentsCount = vectorDatabase.getDocumentsCount(),
                chunksCount = vectorDatabase.getEmbeddingsCount(),
                documents = vectorDatabase.getAllDocuments()
            )
        }
    }

    /**
     * Автоматически запускает индексацию, если документы ещё не проиндексированы
     */
    private fun autoIndexIfNeeded() {
        viewModelScope.launch {
            // Проверяем, есть ли уже проиндексированные документы
            val documentsCount = vectorDatabase.getDocumentsCount()
            if (documentsCount == 0) {
                indexBooks()
            }
        }
    }

    /**
     * Загрузить историю чата из хранилища
     */
    private fun loadChatHistory() {
        viewModelScope.launch {
            try {
                val messages = chatHistoryRepository.loadChatHistory()
                _uiState.value = _uiState.value.copy(chatMessages = messages)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Ошибка загрузки истории: ${e.message}"
                )
            }
        }
    }

    /**
     * Сохранить историю чата в хранилище
     */
    private fun saveChatHistory() {
        viewModelScope.launch {
            try {
                chatHistoryRepository.saveChatHistory(_uiState.value.chatMessages)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Ошибка сохранения истории: ${e.message}"
                )
            }
        }
    }

    /**
     * Индексирует книги из assets
     */
    fun indexBooks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isIndexing = true,
                error = null,
                progress = "Начинаем индексацию..."
            )

            // Файлы книг в assets
            val files =
                listOf("android_book_1.html")
            indexingService.indexDocuments(files).collect { progress ->
                when (progress) {
                    is IndexingProgress.Parsing -> {
                        _uiState.value = _uiState.value.copy(
                            progress = "📖 Парсинг: ${progress.fileName}"
                        )
                    }

                    is IndexingProgress.Chunking -> {
                        _uiState.value = _uiState.value.copy(
                            progress = "✂️ Разбивка: ${progress.chunksCount} чанков"
                        )
                    }

                    is IndexingProgress.Embedding -> {
                        _uiState.value = _uiState.value.copy(
                            progress = "🧠 Эмбеддинг: ${progress.current}/${progress.total}",
                            progressPercent = progress.current.toFloat() / progress.total
                        )
                    }

                    is IndexingProgress.Saving -> {
                        _uiState.value = _uiState.value.copy(
                            progress = "💾 Сохранение ${progress.chunksCount} векторов..."
                        )
                    }

                    is IndexingProgress.Completed -> {
                        // Загружаем актуальные значения из базы
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
                            progress = message,
                            progressPercent = 1f,
                            documentsCount = actualDocsCount,
                            chunksCount = actualChunksCount,
                            documents = actualDocuments
                        )
                    }

                    is IndexingProgress.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isIndexing = false,
                            error = progress.message,
                            progress = ""
                        )
                    }
                }
            }
        }
    }

    fun search() {
        if (searchQuery.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearching = true,
                error = null
            )

            try {
                if (_uiState.value.comparisonMode) {
                    // В режиме сравнения получаем оба результата
                    searchBoth()
                } else if (_uiState.value.useFilter) {
                    // Только с фильтром
                    searchWithFilter()
                } else {
                    // Только без фильтра
                    val results = indexingService.search(searchQuery, topK = 10)
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        searchResults = results,
                        filteredResults = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = "Ошибка поиска: ${e.message}"
                )
            }
        }
    }

    private suspend fun searchWithFilter() {
        val filterConfig = FilterConfig(
            minScoreThreshold = _uiState.value.filterThreshold.toDouble(),
            useLengthBoost = _uiState.value.useLengthBoost,
            maxResults = 5
        )

        val filtered = indexingService.searchWithFilter(
            query = searchQuery,
            topK = 10,
            filterConfig = filterConfig
        )

        _uiState.value = _uiState.value.copy(
            isSearching = false,
            filteredResults = filtered,
            searchResults = emptyList()
        )
    }

    private suspend fun searchBoth() {
        // Поиск без фильтра
        val rawResults = indexingService.search(searchQuery, topK = 10)

        // Поиск с фильтром
        val filterConfig = FilterConfig(
            minScoreThreshold = _uiState.value.filterThreshold.toDouble(),
            useLengthBoost = _uiState.value.useLengthBoost,
            maxResults = 5
        )
        val filtered = indexingService.searchWithFilter(
            query = searchQuery,
            topK = 10,
            filterConfig = filterConfig
        )

        _uiState.value = _uiState.value.copy(
            isSearching = false,
            searchResults = rawResults,
            filteredResults = filtered
        )
    }

    // Управление настройками фильтра
    fun updateFilterThreshold(threshold: Float) {
        _uiState.value = _uiState.value.copy(filterThreshold = threshold)
    }

    fun toggleFilter() {
        _uiState.value = _uiState.value.copy(useFilter = !_uiState.value.useFilter)
    }

    fun toggleLengthBoost() {
        _uiState.value = _uiState.value.copy(useLengthBoost = !_uiState.value.useLengthBoost)
    }

    fun toggleComparisonMode() {
        _uiState.value = _uiState.value.copy(
            comparisonMode = !_uiState.value.comparisonMode,
            useFilter = false  // Сбрасываем при включении сравнения
        )
    }

    /**
     * Отправить сообщение в чат (без RAG, прямой запрос к модели)
     * Использует оптимизированный режим для Jetpack Compose если включен
     */
    fun sendChatMessage() {
        val userMessage = _uiState.value.currentInput.trim()
        if (userMessage.isBlank()) return

        viewModelScope.launch {
            // Добавить сообщение пользователя в чат
            val userChatMessage = dev.kamikaze.mivdating.data.models.ChatMessage(
                id = java.util.UUID.randomUUID().toString(),
                text = userMessage,
                isUser = true
            )

            _uiState.value = _uiState.value.copy(
                chatMessages = _uiState.value.chatMessages + userChatMessage,
                currentInput = "",
                isGenerating = true,
                error = null
            )

            try {
                // Выбираем метод в зависимости от режима
                val answer = if (_uiState.value.useOptimizedComposeMode) {
                    ollamaClient.generateJetpackComposeCode(
                        userPrompt = userMessage
                    )
                } else {
                    ollamaClient.chat(
                        userMessage = userMessage
                    )
                }
                // Добавить ответ AI в чат
                val aiChatMessage = dev.kamikaze.mivdating.data.models.ChatMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    text = answer.message.content,
                    isUser = false,
                    sources = emptyList() // Без источников, так как RAG отключен
                )

                _uiState.value = _uiState.value.copy(
                    chatMessages = _uiState.value.chatMessages + aiChatMessage,
                    isGenerating = false
                )

                // Автоматически сохраняем историю после получения ответа
                saveChatHistory()

            } catch (e: Exception) {
                android.util.Log.e("RAGViewModel", "Error sending chat message", e)

                // Пытаемся получить список доступных моделей для более информативного сообщения
                val availableModels = try {
                    ollamaClient.getAvailableModels()
                } catch (ex: Exception) {
                    emptyList()
                }

                val errorMessage = when {
                    e.message?.contains("timeout", ignoreCase = true) == true ->
                        "Превышено время ожидания ответа от Ollama (5 минут). Проверьте, что Ollama запущен и модель доступна."

                    e.message?.contains("connection", ignoreCase = true) == true ||
                            e.message?.contains("failed to connect", ignoreCase = true) == true ->
                        "Не удалось подключиться к Ollama. Убедитесь, что:\n" +
                                "1. Ollama запущен на вашем компьютере\n" +
                                "2. Для эмулятора: используйте адрес http://130.49.153.154:8000\n" +
                                "3. Для реального устройства: используйте IP вашего компьютера"

                    e.message?.contains("model", ignoreCase = true) == true ||
                            e.message?.contains("not found", ignoreCase = true) == true -> {
                        val baseMsg = "Модель qwen3:14b не найдена."
                        if (availableModels.isNotEmpty()) {
                            baseMsg + "\n\nДоступные модели:\n" +
                                    availableModels.joinToString("\n") { "  • $it" } +
                                    "\n\nПопробуйте:\n  ollama pull qwen3:14b\nили используйте одну из доступных моделей."
                        } else {
                            baseMsg + "\n\nВыполните: ollama pull qwen3:14b"
                        }
                    }

                    e.message?.contains("404", ignoreCase = true) == true ->
                        "Эндпоинт не найден. Проверьте, что Ollama запущен и доступен на http://130.49.153.154:8000"

                    else -> {
                        val baseMsg =
                            "Ошибка отправки сообщения: ${e.message ?: e.javaClass.simpleName}"
                        if (availableModels.isNotEmpty()) {
                            baseMsg + "\n\nДоступные модели: ${availableModels.joinToString(", ")}"
                        } else {
                            baseMsg + "\n\nПроверьте логи в Logcat для подробностей."
                        }
                    }
                }
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    error = errorMessage
                )
            }
        }
    }

    /**
     * Переключить режим оптимизации для Jetpack Compose
     */
    fun toggleOptimizedComposeMode() {
        _uiState.value = _uiState.value.copy(
            useOptimizedComposeMode = !_uiState.value.useOptimizedComposeMode
        )
    }

    /**
     * Очистить историю чата
     */
    fun clearChat() {
        _uiState.value = _uiState.value.copy(
            chatMessages = emptyList(),
            currentInput = ""
        )
        // Также очищаем сохраненную историю
        viewModelScope.launch {
            chatHistoryRepository.clearChatHistory()
        }
    }

    /**
     * Показать диалог с источником
     */
    fun showSourceDialog(source: SearchResult) {
        _uiState.value = _uiState.value.copy(
            selectedSource = source,
            showSourceDialog = true
        )
    }

    /**
     * Закрыть диалог с источником
     */
    fun closeSourceDialog() {
        _uiState.value = _uiState.value.copy(
            showSourceDialog = false,
            selectedSource = null
        )
    }

    /**
     * Показать диалог настроек
     */
    fun showSettings() {
        _uiState.value = _uiState.value.copy(showSettingsDialog = true)
    }

    /**
     * Закрыть диалог настроек
     */
    fun closeSettings() {
        _uiState.value = _uiState.value.copy(showSettingsDialog = false)
    }

    fun clearIndex() {
        viewModelScope.launch {
            vectorDatabase.clearAll()
            _uiState.value = _uiState.value.copy(
                documentsCount = 0,
                chunksCount = 0,
                documents = emptyList(),
                searchResults = emptyList(),
                progress = "Индекс очищен"
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        ollamaClient.close()
        vectorDatabase.close()
    }
}