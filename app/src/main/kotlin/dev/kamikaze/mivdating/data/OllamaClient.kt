package dev.kamikaze.mivdating.data

import dev.kamikaze.mivdating.data.model.OllamaEmbeddingsRequest
import dev.kamikaze.mivdating.data.model.OllamaEmbeddingsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class OllamaClient(
    private val baseUrl: String = "http://10.0.2.2:11434"
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
            level = LogLevel.ALL
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 15000
        }
    }

    suspend fun embed(text: String): List<Double> {
        val response = httpClient.post("$baseUrl/api/embeddings") {
            contentType(ContentType.Application.Json)
            setBody(
                OllamaEmbeddingsRequest(
                    prompt = text,
                    model = "nomic-embed-text"
                )
            )
        }
        val rawText = response.body<OllamaEmbeddingsResponse>()

        return rawText.embedding
    }

    override fun close() = httpClient.close()
}