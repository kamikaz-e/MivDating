package dev.kamikaze.mivdating.data.model

import kotlinx.serialization.Serializable

@Serializable
data class OllamaEmbeddingsResponse(
    val embedding: List<Double>
)
