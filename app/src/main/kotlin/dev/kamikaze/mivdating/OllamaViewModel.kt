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
import dev.kamikaze.mivdating.data.network.YandexGptClient
import dev.kamikaze.mivdating.data.parser.DocumentParser
import dev.kamikaze.mivdating.data.storage.ChatHistoryRepository
import dev.kamikaze.mivdating.data.storage.SearchResult
import dev.kamikaze.mivdating.data.storage.VectorDatabase
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

    val error: String? = null,
    val ollamaAvailable: Boolean = false
)

class RAGViewModel(application: Application) : AndroidViewModel(application) {

    private val ollamaClient = OllamaClient()
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

    private val yandexGptClient = YandexGptClient

    init {
        checkOllamaConnection()
        loadChunksAndVectors()
        loadChatHistory()
        autoIndexIfNeeded()
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    fun updateCurrentInput(input: String) {
        _uiState.value = _uiState.value.copy(currentInput = input)
    }

    private fun checkOllamaConnection() {
        viewModelScope.launch {
            val available = try {
                ollamaClient.isAvailable()
            } catch (e: Exception) {
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
                listOf("book1.txt", "book2.html", "android_book_1.html", "android_book_2.html")
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
     * Отправить сообщение в чат с RAG
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
                // Шаг 1: Выполнить семантический поиск по RAG базе
                val chunks = if (_uiState.value.useFilter) {
                    val filterConfig = FilterConfig(
                        minScoreThreshold = _uiState.value.filterThreshold.toDouble(),
                        useLengthBoost = _uiState.value.useLengthBoost,
                        maxResults = 5
                    )
                    val filtered = indexingService.searchWithFilter(
                        query = userMessage,
                        topK = 10,
                        filterConfig = filterConfig
                    )
                    filtered.results
                } else {
                    indexingService.search(userMessage, topK = 5)
                }

                // Шаг 2: Собрать контекст из найденных чанков
                val context = chunks.mapIndexed { index, chunk ->
                    val sourceNum = index + 1
                    "[Источник $sourceNum]\n" +
                            "Документ: ${chunk.documentTitle}\n" +
                            "Релевантность: ${String.format("%.3f", chunk.score)}\n" +
                            "Текст: ${chunk.chunk.content}"
                }.joinToString("\n\n")

                // Шаг 3: Собрать историю диалога для Yandex GPT
                val conversationHistory = _uiState.value.chatMessages
                    .dropLast(1) // Убираем только что добавленное сообщение пользователя
                    .map { msg ->
                        dev.kamikaze.mivdating.data.network.MessageRequest.Message(
                            role = if (msg.isUser) "user" else "assistant",
                            text = msg.text
                        )
                    }

                // Шаг 4: Отправить запрос в Yandex GPT с контекстом и историей
                val answer = yandexGptClient.sendMessageWithContext(
                    userMessage = userMessage,
                    context = context,
                    conversationHistory = conversationHistory
                )

                // Шаг 5: Фильтруем источники и перенумеровываем ссылки
                // Используем регулярное выражение для точного поиска упоминаний источников
                val sourcePattern = Regex("""\[Источник\s+(\d+)(?:\]|,|\s)""")
                val mentionedSourceNumbers = sourcePattern.findAll(answer.text)
                    .map { it.groupValues[1].toInt() }
                    .toSet() // Убираем дубликаты

                // Создаём карту: старый номер -> новый номер
                val sourceMapping = mutableMapOf<Int, Int>()
                val usedSources = mutableListOf<SearchResult>()

                // Фильтруем только те источники, которые действительно упомянуты
                chunks.forEachIndexed { index, chunk ->
                    val oldSourceNum = index + 1
                    if (oldSourceNum in mentionedSourceNumbers) {
                        val newSourceNum = usedSources.size + 1
                        sourceMapping[oldSourceNum] = newSourceNum
                        usedSources.add(chunk)
                    }
                }

                // Перенумеровываем ссылки в тексте
                var updatedText = answer.text
                // Сортируем в обратном порядке, чтобы не сбить номера при замене
                sourceMapping.entries.sortedByDescending { it.key }.forEach { (oldNum, newNum) ->
                    // Заменяем все варианты: [Источник N], [Источник N,], "Источник N:"
                    // Используем границы слова для точности
                    updatedText = updatedText.replace(
                        Regex("""Источник\s+$oldNum(?=[\]:,\s])"""),
                        "Источник $newNum"
                    )
                }

                // Шаг 6: Добавить ответ AI в чат
                val aiChatMessage = dev.kamikaze.mivdating.data.models.ChatMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    text = updatedText,
                    isUser = false,
                    sources = usedSources
                )

                _uiState.value = _uiState.value.copy(
                    chatMessages = _uiState.value.chatMessages + aiChatMessage,
                    isGenerating = false
                )

                // Автоматически сохраняем историю после получения ответа
                saveChatHistory()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    error = "Ошибка отправки сообщения: ${e.message}"
                )
            }
        }
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