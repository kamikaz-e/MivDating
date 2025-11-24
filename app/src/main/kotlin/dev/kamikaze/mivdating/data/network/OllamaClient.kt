package dev.kamikaze.mivdating.data.network

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
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class OllamaClient(
    private val baseUrl: String = "http://10.0.2.2:11434",
    private val embeddingModel: String = "nomic-embed-text"
) : AutoCloseable {

    private val httpClient: HttpClient = HttpClient(Android) {
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
            requestTimeoutMillis = 60_000  // Увеличено для эмбеддингов
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
    }

    suspend fun embed(text: String): List<Double> {
        val response = httpClient.post("$baseUrl/api/embeddings") {
            contentType(ContentType.Application.Json)
            setBody(
                OllamaEmbeddingsRequest(
                    prompt = text,
                    model = embeddingModel
                )
            )
        }
        return response.body<OllamaEmbeddingsResponse>().embedding
    }

    suspend fun embedBatch(texts: List<String>): List<List<Double>> {
        return texts.map { embed(it) }
    }

    suspend fun isAvailable(): Boolean {
        return try {
            httpClient.get("$baseUrl/api/tags")
            true
        } catch (e: Exception) {
            false
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