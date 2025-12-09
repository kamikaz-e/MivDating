package dev.kamikaze.mivdating.data.models

import kotlinx.serialization.Serializable

@Serializable
data class OllamaChatResponse(
    val model: String,
    val created_at: String,
    val message: OllamaChatMessage,
    val done: Boolean,
    val total_duration: Long? = null,
    val load_duration: Long? = null,
    val prompt_eval_count: Int? = null,
    val eval_count: Int? = null
)
