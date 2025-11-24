package dev.kamikaze.mivdating.data.models

import kotlinx.serialization.Serializable

@Serializable
data class OllamaEmbeddingsRequest(
    val model: String,
    val prompt: String
)
