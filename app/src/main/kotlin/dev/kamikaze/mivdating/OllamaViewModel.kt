// viewmodel/RAGViewModel.kt
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
import dev.kamikaze.mivdating.data.network.OllamaClient
import dev.kamikaze.mivdating.data.parser.DocumentParser
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
    val searchResults: List<SearchResult> = emptyList(),
    val error: String? = null,
    val ollamaAvailable: Boolean = false
)

class RAGViewModel(application: Application) : AndroidViewModel(application) {

    private val ollamaClient = OllamaClient()
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

    private val _uiState = MutableStateFlow(RAGUiState())
    val uiState: StateFlow<RAGUiState> = _uiState.asStateFlow()

    var searchQuery by mutableStateOf("")
        private set

    init {
        checkOllamaConnection()
        loadStats()
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
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

    private fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                documentsCount = vectorDatabase.getDocumentsCount(),
                chunksCount = vectorDatabase.getEmbeddingsCount()
            )
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
            val files = listOf("book1.txt", "book2.html")

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
                        _uiState.value = _uiState.value.copy(
                            isIndexing = false,
                            progress = "✅ Готово! ${progress.totalDocuments} документов, ${progress.totalChunks} чанков",
                            progressPercent = 1f,
                            documentsCount = progress.totalDocuments,
                            chunksCount = progress.totalChunks
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
                val results = indexingService.search(searchQuery, topK = 5)
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchResults = results
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = "Ошибка поиска: ${e.message}"
                )
            }
        }
    }

    fun clearIndex() {
        viewModelScope.launch {
            vectorDatabase.clearAll()
            _uiState.value = _uiState.value.copy(
                documentsCount = 0,
                chunksCount = 0,
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