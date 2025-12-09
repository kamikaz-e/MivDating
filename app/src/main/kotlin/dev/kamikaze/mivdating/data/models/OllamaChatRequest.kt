package dev.kamikaze.mivdating.data.models

import kotlinx.serialization.Serializable

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatMessage>,
    val stream: Boolean = false,
    val options: OllamaChatOptions? = null
)

@Serializable
data class OllamaChatMessage(
    val role: String,  // "system", "user", "assistant"
    val content: String,
    val thinking: String? = null  // Reasoning mode для qwen3
)

@Serializable
data class OllamaChatOptions(
    val temperature: Double = 0.3,
    val num_predict: Int? = null
)
