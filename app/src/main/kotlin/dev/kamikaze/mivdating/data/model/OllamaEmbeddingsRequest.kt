package dev.kamikaze.mivdating.data.model

import kotlinx.serialization.Serializable

@Serializable
data class OllamaEmbeddingsRequest(
    val model: String,
    val prompt: String
)
