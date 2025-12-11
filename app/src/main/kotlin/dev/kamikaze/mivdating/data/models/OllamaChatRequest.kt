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
    val num_predict: Int? = null,
    val num_ctx: Int? = null,         // Размер контекстного окна
    val top_p: Double? = null,         // Nucleus sampling
    val top_k: Int? = null,            // Top-k sampling
    val repeat_penalty: Double? = null // Штраф за повторения
)
