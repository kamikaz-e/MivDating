package dev.kamikaze.mivdating.data.network

import dev.kamikaze.mivdating.data.models.OllamaChatMessage
import dev.kamikaze.mivdating.data.models.OllamaChatOptions
import dev.kamikaze.mivdating.data.models.OllamaChatRequest
import dev.kamikaze.mivdating.data.models.OllamaChatResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.concurrent.TimeUnit

class OllamaClient(
    baseUrl: String = "http://10.0.2.2:11434",
    private val embeddingModel: String = "nomic-embed-text",
    private val chatModel: String = "tinyllama"
) : AutoCloseable {
    
    /**
     * Определяет, является ли URL удаленным Flask API сервером
     */
    private fun isRemoteServer(url: String): Boolean {
        return url.contains(":8000") || url.contains("130.49.153.154")
    }
    
    /**
     * Получает модель для использования в зависимости от типа сервера
     */
    private suspend fun getModelForServer(): String {
        return if (isRemoteServer(baseUrl)) {
            // Для удаленного сервера используем tinyllama
            "tinyllama"
        } else {
            // Для локального Ollama используем указанную модель
            chatModel
        }
    }

    // Делаем baseUrl изменяемым для возможности обновления из настроек
    private var baseUrl: String = baseUrl
        private set

    /**
     * Обновить URL Ollama сервера (для переключения между локальным и удаленным)
     */
    fun updateBaseUrl(newUrl: String) {
        baseUrl = newUrl.trimEnd('/')
        Timber.i("OllamaClient baseUrl updated to: $baseUrl")
        // Сбрасываем кеш модели при смене URL
        resolvedChatModel = null
    }

    // Кэш для найденной модели (чтобы не искать каждый раз)
    private var resolvedChatModel: String? = null

    private val httpClient: HttpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            })
        }

        install(Logging) {
            level = LogLevel.ALL
            logger = object : io.ktor.client.plugins.logging.Logger {
                override fun log(message: String) {
                    Timber.d("OllamaClient: $message")
                }
            }
        }

        install(HttpTimeout) {
            requestTimeoutMillis = TimeUnit.MINUTES.toMillis(5) // 5 minutes for LLM responses
            connectTimeoutMillis = TimeUnit.SECONDS.toMillis(30) // 30 seconds to connect
            socketTimeoutMillis = TimeUnit.MINUTES.toMillis(5) // 5 minutes for socket operations
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Парсит NDJSON ответ от Ollama (newline-delimited JSON)
     * Ollama возвращает NDJSON даже при stream=false
     * Собираем все токены из content и thinking полей
     */
    private fun parseNdjsonChatResponse(text: String): OllamaChatResponse {
        Timber.d("Raw response from Ollama, length: ${text.length}")

        val lines = text.trim().lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            Timber.e("Empty response from Ollama")
            throw IllegalStateException("Empty response from Ollama")
        }

        Timber.d("NDJSON parsing: total lines: ${lines.size}")

        // Собираем все части контента
        val contentBuilder = StringBuilder()
        val thinkingBuilder = StringBuilder()
        var lastResponse: OllamaChatResponse? = null

        for (line in lines) {
            try {
                val response = json.decodeFromString<OllamaChatResponse>(line)

                // Добавляем content если есть
                if (response.message.content.isNotEmpty()) {
                    contentBuilder.append(response.message.content)
                }

                // Собираем thinking если есть (для отладки)
                response.message.thinking?.let { thinking ->
                    if (thinking.isNotEmpty()) {
                        thinkingBuilder.append(thinking)
                    }
                }

                // Сохраняем последний ответ (с done=true будет финальная метаданные)
                if (response.done) {
                    lastResponse = response
                }
            } catch (e: Exception) {
                Timber.w("Failed to parse line, skipping: ${line.take(100)}")
            }
        }

        // Если нашли финальный ответ с done=true, используем его метаданные
        if (lastResponse != null) {
            val fullContent = contentBuilder.toString()
            Timber.d("Assembled content length: ${fullContent.length}")
            Timber.d("Thinking length: ${thinkingBuilder.length}")

            // Создаем финальный ответ с собранным контентом
            return lastResponse.copy(
                message = lastResponse.message.copy(
                    content = fullContent.ifEmpty {
                        // Если content пустой, возможно ответ был только в thinking
                        Timber.w("Content is empty, but we have thinking")
                        lastResponse.message.content
                    }
                )
            )
        }

        // Если не нашли done=true, берем последнюю строку
        Timber.w("No done=true found, using last line")
        val lastLine = lines.last()
        return json.decodeFromString<OllamaChatResponse>(lastLine).copy(
            message = OllamaChatMessage(
                role = "assistant",
                content = contentBuilder.toString()
            )
        )
    }

    suspend fun embed(text: String): List<Double> {
        return try {
            Timber.d("Sending embedding request to $baseUrl/api/embeddings with model: $embeddingModel")
            val response = httpClient.post("$baseUrl/api/embeddings") {
                contentType(ContentType.Application.Json)
                setBody(
                    OllamaEmbeddingsRequest(
                        prompt = text,
                        model = embeddingModel
                    )
                )
            }
            val result = response.body<OllamaEmbeddingsResponse>().embedding
            Timber.d("Embedding response received, size: ${result.size}")
            result
        } catch (e: Exception) {
            Timber.e(e, "Error getting embedding from Ollama")
            throw e
        }
    }

    suspend fun embedBatch(texts: List<String>): List<List<Double>> {
        return texts.map { embed(it) }
    }

    suspend fun isAvailable(): Boolean {
        return try {
            // Для удаленного сервера проверяем /health endpoint
            if (isRemoteServer(baseUrl)) {
                Timber.d("Checking remote server availability at $baseUrl/health")
                val response = httpClient.get("$baseUrl/health")
                Timber.d("Remote server is available, response status: ${response.status}")
                return response.status.value in 200..299
            } else {
                // Для локального Ollama проверяем /api/tags
                Timber.d("Checking Ollama availability at $baseUrl/api/tags")
                val response = httpClient.get("$baseUrl/api/tags")
                Timber.d("Ollama is available, response status: ${response.status}")
                return response.status.value in 200..299
            }
        } catch (e: Exception) {
            Timber.e(e, "Server is not available at $baseUrl")
            false
        }
    }

    /**
     * Получает список всех доступных моделей
     */
    suspend fun getAvailableModels(): List<String> {
        return try {
            // Для удаленного сервера и локального Ollama используется один endpoint
            val response = httpClient.get("$baseUrl/api/tags")
            val tagsResponse = response.body<OllamaTagsResponse>()
            val models = tagsResponse.models.map { it.name }
            Timber.d("Available models: $models")
            models
        } catch (e: Exception) {
            Timber.e(e, "Error getting available models")
            // Для удаленного сервера возвращаем дефолтную модель
            if (isRemoteServer(baseUrl)) {
                listOf("tinyllama")
            } else {
                emptyList()
            }
        }
    }

    /**
     * Находит подходящую модель qwen с размером 14b
     * Ищет модели, содержащие "qwen" и "14b"
     */
    suspend fun findQwen14bModel(): String? {
        return try {
            val models = getAvailableModels()
            val found = models.firstOrNull { modelName ->
                modelName.contains("qwen", ignoreCase = true) && 
                modelName.contains("14b", ignoreCase = true)
            }
            if (found != null) {
                Timber.d("Found Qwen 14b model: $found")
            } else {
                Timber.w("Qwen 14b model not found. Available models: $models")
            }
            found
        } catch (e: Exception) {
            Timber.e(e, "Error finding Qwen 14b model")
            null
        }
    }

    /**
     * Проверяет, доступна ли указанная модель чата
     * Также пытается найти альтернативную модель qwen:14b
     */
    suspend fun isModelAvailable(): Boolean {
        return try {
            Timber.d("Checking if model $chatModel is available")
            val models = getAvailableModels()
            
            // Точное совпадение
            val exactMatch = models.any { it == chatModel }
            if (exactMatch) {
                Timber.d("Model $chatModel found (exact match)")
                return true
            }
            
            // Поиск по части имени (до двоеточия)
            val modelBaseName = chatModel.split(":")[0]
            val partialMatch = models.any { 
                it.startsWith(modelBaseName, ignoreCase = true) || 
                it.contains(modelBaseName, ignoreCase = true)
            }
            
            if (partialMatch) {
                Timber.d("Model similar to $chatModel found")
                return true
            }
            
            // Поиск любой модели qwen с 14b
            val qwen14b = findQwen14bModel()
            if (qwen14b != null) {
                Timber.w("Model $chatModel not found, but found alternative: $qwen14b")
                Timber.w("Consider updating chatModel to: $qwen14b")
                return true
            }
            
            Timber.w("Model $chatModel not found. Available models: $models")
            false
        } catch (e: Exception) {
            Timber.e(e, "Error checking model availability")
            false
        }
    }

    /**
     * Получает имя модели для использования (с автоматическим определением)
     */
    private suspend fun getResolvedModelName(): String {
        // Для удаленного сервера всегда используем tinyllama
        if (isRemoteServer(baseUrl)) {
            val remoteModel = "tinyllama"
            Timber.d("Using remote server model: $remoteModel")
            return remoteModel
        }
        
        // Для локального Ollama используем кеширование и поиск
        if (resolvedChatModel != null) {
            return resolvedChatModel!!
        }
        
        // Проверяем точное совпадение
        val models = getAvailableModels()
        if (models.contains(chatModel)) {
            resolvedChatModel = chatModel
            return chatModel
        }
        
        // Ищем альтернативную модель qwen:14b
        val alternative = findQwen14bModel()
        if (alternative != null) {
            Timber.w("Using alternative model: $alternative instead of $chatModel")
            resolvedChatModel = alternative
            return alternative
        }
        
        // Если ничего не найдено, используем исходное имя (может быть ошибка, но попробуем)
        Timber.w("Model $chatModel not found, but will try to use it anyway")
        return chatModel
    }

    /**
     * Отправка сообщения в чат без RAG
     */
    suspend fun chat(
        userMessage: String,
        conversationHistory: List<OllamaChatMessage> = emptyList()
    ): OllamaChatResponse {
        return try {
            val modelToUse = getResolvedModelName()
            
            val messages = listOf(
                OllamaChatMessage(
                    role = "system",
                    content = "Ты — полезный AI-ассистент. Отвечай на вопросы пользователя четко и по существу."
                )
            ) + conversationHistory + listOf(
                OllamaChatMessage(role = "user", content = userMessage)
            )

            Timber.d("Sending chat request to $baseUrl/api/chat with model: $modelToUse")
            Timber.d("Message count: ${messages.size}, user message length: ${userMessage.length}")

            val request = OllamaChatRequest(
                model = modelToUse,
                messages = messages,
                stream = false,
                options = OllamaChatOptions(temperature = 0.3)
            )

            Timber.d("baseUrl : $baseUrl")
            val response = httpClient.post("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            val responseText = response.bodyAsText()
            Timber.d("Response status: ${response.status}, body length: ${responseText.length}")
            val result = parseNdjsonChatResponse(responseText)
            Timber.d("Chat response parsed successfully, done: ${result.done}, message length: ${result.message.content.length}")
            if (result.message.content.isBlank()) {
                Timber.w("Warning: Response content is empty!")
            }
            result
        } catch (e: Exception) {
            Timber.e(e, "Error in chat request to Ollama")
            throw e
        }
    }

    /**
     * Отправка сообщения в чат с RAG контекстом
     */
    suspend fun chatWithContext(
        userMessage: String,
        context: String,
        conversationHistory: List<OllamaChatMessage> = emptyList()
    ): OllamaChatResponse {
        return try {
            val systemPrompt = """
            Ты — AI-ассистент, который отвечает на вопросы
        """.trimIndent()

            val messages = listOf(
                OllamaChatMessage(role = "system", content = systemPrompt)
            ) + conversationHistory + listOf(
                OllamaChatMessage(role = "user", content = userMessage)
            )

            val modelToUse = getResolvedModelName()
            val request = OllamaChatRequest(
                model = modelToUse,
                messages = messages,
                stream = false,
                options = OllamaChatOptions(temperature = 0.3)
            )

            val response = httpClient.post("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            val responseText = response.bodyAsText()
            Timber.d("ChatWithContext response status: ${response.status}, body length: ${responseText.length}")
            val result = parseNdjsonChatResponse(responseText)
            Timber.d("ChatWithContext response parsed successfully, done: ${result.done}, message length: ${result.message.content.length}")
            if (!result.done) {
                Timber.w("Response marked as not done, but stream=false")
            }
            if (result.message.content.isBlank()) {
                Timber.w("Warning: ChatWithContext response content is empty!")
            }
            result
        } catch (e: Exception) {
            Timber.e(e, "Error in chatWithContext request to Ollama")
            throw e
        }
    }

    override fun close() = httpClient.close()
}

@Serializable
data class OllamaEmbeddingsRequest(
    val model: String,
    val prompt: String
)

@Serializable
data class OllamaEmbeddingsResponse(
    val embedding: List<Double>
)

@Serializable
data class OllamaTagsResponse(
    val models: List<OllamaModel>
)

@Serializable
data class OllamaModel(
    val name: String,
    val modified_at: String? = null,
    val size: Long? = null
)