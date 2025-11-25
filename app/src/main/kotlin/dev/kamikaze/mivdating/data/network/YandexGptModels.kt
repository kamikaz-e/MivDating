package dev.kamikaze.mivdating.data.network

import kotlinx.serialization.Serializable

@Serializable
data class MessageRequest(
    val modelUri: String,
    val completionOptions: CompletionOptions,
    val messages: List<Message>
) {
    @Serializable
    data class CompletionOptions(
        val stream: Boolean = false,
        val temperature: Float = 0.3f
    )

    @Serializable
    data class Message(
        val role: String,
        val text: String
    )
}

@Serializable
data class YandexUsage(
    val inputTextTokens: String? = null,
    val completionTokens: String? = null,
    val totalTokens: String? = null
)

@Serializable
data class YandexErrorInfo(
    val code: String? = null,
    val message: String? = null
)

@Serializable
data class YandexAlternativeMessage(
    val role: String,
    val text: String
)

@Serializable
data class YandexAlternative(
    val message: YandexAlternativeMessage,
    val status: String = "ALTERNATIVE_STATUS_OK"
)

@Serializable
data class YandexResult(
    val alternatives: List<YandexAlternative> = emptyList(),
    val usage: YandexUsage? = null,
    val modelVersion: String? = null
)

@Serializable
data class MessageResponse(
    val result: YandexResult? = null,
    val error: YandexErrorInfo? = null,
    val code: Int? = null,
    val message: String? = null
)

data class ApiResponse(
    val text: String,
    val tokens: TokenStats = TokenStats()
)

data class TokenStats(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0
)

fun createTokenStats(usage: YandexUsage?): TokenStats {
    if (usage == null) return TokenStats()
    return TokenStats(
        inputTokens = usage.inputTextTokens?.toIntOrNull() ?: 0,
        outputTokens = usage.completionTokens?.toIntOrNull() ?: 0,
        totalTokens = usage.totalTokens?.toIntOrNull() ?: 0
    )
}
