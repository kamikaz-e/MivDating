package dev.kamikaze.mivdating.data.analytics

import android.content.Context
import dev.kamikaze.mivdating.data.network.OllamaClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Сервис для анализа данных (CSV, JSON, логи) с помощью локальной LLM
 */
class DataAnalyzer(
    private val context: Context,
    private val ollamaClient: OllamaClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Загружает и анализирует данные из файла в assets
     */
    suspend fun analyzeFile(
        fileName: String,
        question: String
    ): AnalysisResult {
        return try {
            Timber.d("Analyzing file: $fileName with question: $question")

            // Определяем тип файла и парсим его
            val dataContent = when {
                fileName.endsWith(".csv") -> parseCsvFile(fileName)
                fileName.endsWith(".json") -> parseJsonFile(fileName)
                fileName.endsWith(".txt") || fileName.endsWith(".log") -> parseTextFile(fileName)
                else -> throw IllegalArgumentException("Неподдерживаемый тип файла: $fileName")
            }

            // Создаем промпт для анализа
            val prompt = buildAnalysisPrompt(dataContent, question)

            // Отправляем запрос в LLM
            val response = ollamaClient.chat(userMessage = prompt)

            AnalysisResult(
                question = question,
                answer = response.message.content,
                dataSource = fileName,
                success = true
            )
        } catch (e: Exception) {
            Timber.e(e, "Error analyzing file: $fileName")
            AnalysisResult(
                question = question,
                answer = "Ошибка анализа: ${e.message}",
                dataSource = fileName,
                success = false,
                error = e.message
            )
        }
    }

    /**
     * Парсит CSV файл и возвращает структурированное представление
     */
    private fun parseCsvFile(fileName: String): String {
        val inputStream = context.assets.open(fileName)
        val reader = BufferedReader(InputStreamReader(inputStream))

        val lines = reader.readLines()
        if (lines.isEmpty()) {
            return "Файл пуст"
        }

        val header = lines.first()
        val dataRows = lines.drop(1)

        // Создаем статистику
        val stats = buildString {
            appendLine("=== CSV DATA ANALYSIS ===")
            appendLine("Файл: $fileName")
            appendLine("Количество строк: ${dataRows.size}")
            appendLine("Столбцы: $header")
            appendLine()
            appendLine("=== SAMPLE DATA (первые 20 строк) ===")
            dataRows.take(20).forEach { appendLine(it) }

            if (dataRows.size > 20) {
                appendLine()
                appendLine("... и еще ${dataRows.size - 20} строк")
            }

            // Добавляем агрегированную статистику для CSV с логами
            if (header.contains("error") || header.contains("action")) {
                appendLine()
                appendLine("=== AGGREGATED STATISTICS ===")

                // Парсим CSV и считаем статистику
                val headerColumns = header.split(",")
                val errorIndex = headerColumns.indexOfFirst { it.trim() == "error" }
                val actionIndex = headerColumns.indexOfFirst { it.trim() == "action" }
                val screenIndex = headerColumns.indexOfFirst { it.trim() == "screen" }

                if (errorIndex >= 0) {
                    val errorCounts = mutableMapOf<String, Int>()
                    dataRows.forEach { row ->
                        val columns = row.split(",")
                        if (columns.size > errorIndex) {
                            val error = columns[errorIndex].trim()
                            if (error.isNotEmpty()) {
                                errorCounts[error] = errorCounts.getOrDefault(error, 0) + 1
                            }
                        }
                    }
                    appendLine("Ошибки:")
                    errorCounts.entries.sortedByDescending { it.value }.forEach {
                        appendLine("  ${it.key}: ${it.value} раз")
                    }
                }

                if (actionIndex >= 0) {
                    val actionCounts = mutableMapOf<String, Int>()
                    dataRows.forEach { row ->
                        val columns = row.split(",")
                        if (columns.size > actionIndex) {
                            val action = columns[actionIndex].trim()
                            if (action.isNotEmpty()) {
                                actionCounts[action] = actionCounts.getOrDefault(action, 0) + 1
                            }
                        }
                    }
                    appendLine()
                    appendLine("Действия:")
                    actionCounts.entries.sortedByDescending { it.value }.forEach {
                        appendLine("  ${it.key}: ${it.value} раз")
                    }
                }

                if (screenIndex >= 0) {
                    val screenCounts = mutableMapOf<String, Int>()
                    dataRows.forEach { row ->
                        val columns = row.split(",")
                        if (columns.size > screenIndex) {
                            val screen = columns[screenIndex].trim()
                            if (screen.isNotEmpty()) {
                                screenCounts[screen] = screenCounts.getOrDefault(screen, 0) + 1
                            }
                        }
                    }
                    appendLine()
                    appendLine("Экраны:")
                    screenCounts.entries.sortedByDescending { it.value }.forEach {
                        appendLine("  ${it.key}: ${it.value} событий")
                    }
                }
            }
        }

        inputStream.close()
        return stats
    }

    /**
     * Парсит JSON файл и возвращает структурированное представление
     */
    private fun parseJsonFile(fileName: String): String {
        val inputStream = context.assets.open(fileName)
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        inputStream.close()

        // Форматируем JSON для лучшей читаемости
        return buildString {
            appendLine("=== JSON DATA ANALYSIS ===")
            appendLine("Файл: $fileName")
            appendLine()
            appendLine("=== CONTENT ===")
            appendLine(jsonString)
        }
    }

    /**
     * Парсит текстовый файл (логи) и возвращает структурированное представление
     */
    private fun parseTextFile(fileName: String): String {
        val inputStream = context.assets.open(fileName)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val lines = reader.readLines()
        inputStream.close()

        // Создаем статистику по логам
        val errorLines = lines.filter { it.contains("ERROR", ignoreCase = true) }
        val warnLines = lines.filter { it.contains("WARN", ignoreCase = true) }
        val infoLines = lines.filter { it.contains("INFO", ignoreCase = true) }

        return buildString {
            appendLine("=== LOG FILE ANALYSIS ===")
            appendLine("Файл: $fileName")
            appendLine("Всего строк: ${lines.size}")
            appendLine("ERROR: ${errorLines.size}")
            appendLine("WARN: ${warnLines.size}")
            appendLine("INFO: ${infoLines.size}")
            appendLine()

            appendLine("=== ERROR MESSAGES ===")
            errorLines.forEach { appendLine(it) }

            appendLine()
            appendLine("=== WARNING MESSAGES ===")
            warnLines.forEach { appendLine(it) }

            appendLine()
            appendLine("=== SAMPLE INFO MESSAGES (первые 10) ===")
            infoLines.take(10).forEach { appendLine(it) }

            if (infoLines.size > 10) {
                appendLine("... и еще ${infoLines.size - 10} INFO сообщений")
            }
        }
    }

    /**
     * Создает промпт для анализа данных
     */
    private fun buildAnalysisPrompt(dataContent: String, question: String): String {
        return """
Ты — аналитик данных. Твоя задача — проанализировать предоставленные данные и ответить на вопрос пользователя.

ДАННЫЕ ДЛЯ АНАЛИЗА:
$dataContent

ВОПРОС ПОЛЬЗОВАТЕЛЯ:
$question

ИНСТРУКЦИИ:
1. Внимательно изучи данные
2. Ответь на вопрос пользователя на основе этих данных
3. Предоставь конкретные цифры и факты
4. Если есть паттерны или тренды - укажи их
5. Будь кратким и точным
6. Если данных недостаточно для ответа, так и скажи

ОТВЕТ:
        """.trimIndent()
    }

    /**
     * Получить список доступных файлов для анализа
     */
    fun getAvailableFiles(): List<DataFile> {
        return try {
            val files = context.assets.list("") ?: emptyArray()
            files.filter {
                it.endsWith(".csv") || it.endsWith(".json") ||
                it.endsWith(".txt") || it.endsWith(".log")
            }.map { fileName ->
                DataFile(
                    name = fileName,
                    type = when {
                        fileName.endsWith(".csv") -> DataFileType.CSV
                        fileName.endsWith(".json") -> DataFileType.JSON
                        else -> DataFileType.TEXT
                    },
                    description = getFileDescription(fileName)
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting available files")
            emptyList()
        }
    }

    /**
     * Получить описание файла
     */
    private fun getFileDescription(fileName: String): String {
        return when (fileName) {
            "user_analytics.csv" -> "Аналитика действий пользователей (логин, просмотры, чат, ошибки)"
            "app_errors.json" -> "Журнал ошибок приложения с деталями"
            "app_logs.txt" -> "Текстовые логи приложения"
            else -> "Файл данных"
        }
    }
}

/**
 * Результат анализа данных
 */
@Serializable
data class AnalysisResult(
    val question: String,
    val answer: String,
    val dataSource: String,
    val success: Boolean,
    val error: String? = null
)

/**
 * Информация о файле данных
 */
data class DataFile(
    val name: String,
    val type: DataFileType,
    val description: String
)

/**
 * Тип файла данных
 */
enum class DataFileType {
    CSV,
    JSON,
    TEXT
}
