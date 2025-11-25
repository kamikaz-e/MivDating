package dev.kamikaze.mivdating.data.network

import dev.kamikaze.mivdating.BuildConfig
import dev.kamikaze.mivdating.data.network.MessageRequest.CompletionOptions
import dev.kamikaze.mivdating.data.network.MessageRequest.Message
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object YandexGptClient : AutoCloseable {

    private val apiKey: String = BuildConfig.YANDEX_API_KEY
    private val folderId: String = BuildConfig.YANDEX_FOLDER_ID

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            })
        }
        install(Logging) {
            level = LogLevel.BODY
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
    }

    /**
     * Отправка сообщения с историей диалога (NO RAG режим)
     */
    suspend fun sendMessage(
        userMessage: String,
        conversationHistory: List<Message> = emptyList()
    ): ApiResponse {
        return try {
            val response = client.post("https://llm.api.cloud.yandex.net/foundationModels/v1/completion") {
                header("Authorization", "Api-Key $apiKey")
                header("x-folder-id", folderId)
                contentType(ContentType.Application.Json)
                setBody(
                    MessageRequest(
                        modelUri = "gpt://$folderId/yandexgpt/latest",
                        completionOptions = CompletionOptions(temperature = 0.3f),
                        messages = listOf(
                            Message(
                                role = "system",
                                text = "Ты — полезный AI-ассистент. Отвечай на вопросы пользователя четко и по существу."
                            )
                        ) + conversationHistory + listOf(
                            Message(role = "user", text = userMessage)
                        )
                    )
                )
            }

            val messageResponse = response.body<MessageResponse>()

            // Проверяем на ошибки
            if (messageResponse.error != null) {
                return ApiResponse(
                    text = "Ошибка API: ${messageResponse.error.message ?: messageResponse.error.code}",
                    tokens = TokenStats()
                )
            }

            val text = messageResponse.result?.alternatives?.firstOrNull()?.message?.text
                ?: "Нет ответа"
            val tokens = createTokenStats(messageResponse.result?.usage)

            ApiResponse(text = text, tokens = tokens)

        } catch (e: Exception) {
            ApiResponse(
                text = "Ошибка: ${e.message}",
                tokens = TokenStats()
            )
        }
    }

    /**
     * Отправка сообщения с RAG контекстом
     */
    suspend fun sendMessageWithContext(
        userMessage: String,
        context: String,
        conversationHistory: List<Message> = emptyList()
    ): ApiResponse {
        return try {
            val systemPrompt = """
                Ты — AI-ассистент, который отвечает на вопросы на основе предоставленного контекста.

                КОНТЕКСТ ИЗ ДОКУМЕНТОВ:
                $context

                ИНСТРУКЦИИ:
                - Отвечай ТОЛЬКО на основе предоставленного контекста
                - Если в контексте нет информации для ответа, так и скажи
                - Будь точным и конкретным
                - Цитируй релевантные части контекста при необходимости
            """.trimIndent()

            val response = client.post("https://llm.api.cloud.yandex.net/foundationModels/v1/completion") {
                header("Authorization", "Api-Key $apiKey")
                header("x-folder-id", folderId)
                contentType(ContentType.Application.Json)
                setBody(
                    MessageRequest(
                        modelUri = "gpt://$folderId/yandexgpt/latest",
                        completionOptions = CompletionOptions(temperature = 0.3f),
                        messages = listOf(
                            Message(role = "system", text = systemPrompt)
                        ) + conversationHistory + listOf(
                            Message(role = "user", text = userMessage)
                        )
                    )
                )
            }

            val messageResponse = response.body<MessageResponse>()

            // Проверяем на ошибки
            if (messageResponse.error != null) {
                return ApiResponse(
                    text = "Ошибка API: ${messageResponse.error.message ?: messageResponse.error.code}",
                    tokens = TokenStats()
                )
            }

            val text = messageResponse.result?.alternatives?.firstOrNull()?.message?.text
                ?: "Нет ответа"
            val tokens = createTokenStats(messageResponse.result?.usage)

            ApiResponse(text = text, tokens = tokens)

        } catch (e: Exception) {
            ApiResponse(
                text = "Ошибка: ${e.message}",
                tokens = TokenStats()
            )
        }
    }

    override fun close() {
        client.close()
    }
}
