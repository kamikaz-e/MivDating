package dev.kamikaze.mivdating

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kamikaze.mivdating.data.analytics.AnalysisResult
import dev.kamikaze.mivdating.data.analytics.DataAnalyzer
import dev.kamikaze.mivdating.data.analytics.DataFile
import dev.kamikaze.mivdating.data.network.OllamaClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

data class DataAnalysisUiState(
    val availableFiles: List<DataFile> = emptyList(),
    val selectedFile: DataFile? = null,
    val currentQuestion: String = "",
    val analysisHistory: List<AnalysisResult> = emptyList(),
    val isAnalyzing: Boolean = false,
    val ollamaAvailable: Boolean = false,
    val error: String? = null,
    val suggestedQuestions: List<String> = emptyList()
)

class DataAnalysisViewModel(application: Application) : AndroidViewModel(application) {

    private val ollamaClient = OllamaClient()
    private val dataAnalyzer = DataAnalyzer(application, ollamaClient)

    private val _uiState = MutableStateFlow(DataAnalysisUiState())
    val uiState: StateFlow<DataAnalysisUiState> = _uiState.asStateFlow()

    init {
        checkOllamaConnection()
        loadAvailableFiles()
    }

    private fun checkOllamaConnection() {
        viewModelScope.launch {
            try {
                val available = ollamaClient.isAvailable()
                _uiState.value = _uiState.value.copy(ollamaAvailable = available)
                if (!available) {
                    _uiState.value = _uiState.value.copy(
                        error = "Ollama не доступен. Убедитесь, что Ollama запущен на http://10.0.2.2:11434"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking Ollama connection")
                _uiState.value = _uiState.value.copy(
                    ollamaAvailable = false,
                    error = "Ошибка подключения к Ollama: ${e.message}"
                )
            }
        }
    }

    private fun loadAvailableFiles() {
        viewModelScope.launch {
            try {
                val files = dataAnalyzer.getAvailableFiles()
                _uiState.value = _uiState.value.copy(availableFiles = files)
            } catch (e: Exception) {
                Timber.e(e, "Error loading available files")
                _uiState.value = _uiState.value.copy(
                    error = "Ошибка загрузки файлов: ${e.message}"
                )
            }
        }
    }

    fun selectFile(file: DataFile) {
        _uiState.value = _uiState.value.copy(
            selectedFile = file,
            suggestedQuestions = getSuggestedQuestions(file.name),
            analysisHistory = emptyList() // Очищаем историю при выборе нового файла
        )
    }

    fun updateQuestion(question: String) {
        _uiState.value = _uiState.value.copy(currentQuestion = question)
    }

    fun analyzeData() {
        val question = _uiState.value.currentQuestion.trim()
        val selectedFile = _uiState.value.selectedFile

        if (question.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Введите вопрос для анализа")
            return
        }

        if (selectedFile == null) {
            _uiState.value = _uiState.value.copy(error = "Выберите файл для анализа")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAnalyzing = true,
                error = null
            )

            try {
                val result = dataAnalyzer.analyzeFile(
                    fileName = selectedFile.name,
                    question = question
                )

                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    analysisHistory = _uiState.value.analysisHistory + result,
                    currentQuestion = ""
                )
            } catch (e: Exception) {
                Timber.e(e, "Error analyzing data")
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    error = "Ошибка анализа: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearHistory() {
        _uiState.value = _uiState.value.copy(analysisHistory = emptyList())
    }

    /**
     * Получить предложенные вопросы для файла
     */
    private fun getSuggestedQuestions(fileName: String): List<String> {
        return when (fileName) {
            "user_analytics.csv" -> listOf(
                "Какие действия пользователи выполняют чаще всего?",
                "На каком экране происходит больше всего ошибок?",
                "Какая самая частая ошибка в приложении?",
                "Сколько пользователей покинуло приложение во время регистрации?",
                "Какой процент действий завершается с ошибкой?"
            )
            "app_errors.json" -> listOf(
                "Какой тип ошибок встречается чаще всего?",
                "На каком экране происходит больше всего ошибок?",
                "Какие ошибки имеют высокий уровень серьезности?",
                "На каких устройствах чаще всего происходят ошибки?",
                "Какие ошибки связаны с сетью?"
            )
            "app_logs.txt" -> listOf(
                "Сколько раз произошли ошибки и предупреждения?",
                "Какие основные проблемы видны в логах?",
                "Где пользователи теряются (покидают приложение)?",
                "Какие операции чаще всего завершаются с retry?",
                "Есть ли паттерны в последовательности событий перед ошибками?"
            )
            else -> listOf(
                "Какие основные паттерны в данных?",
                "Есть ли аномалии в данных?",
                "Какие выводы можно сделать из этих данных?"
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        ollamaClient.close()
    }
}
