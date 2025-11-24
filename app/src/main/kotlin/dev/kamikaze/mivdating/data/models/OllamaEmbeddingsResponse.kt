package dev.kamikaze.mivdating.data.models

import kotlinx.serialization.Serializable

@Serializable
data class OllamaEmbeddingsResponse(
    val embedding: List<Double>
)
