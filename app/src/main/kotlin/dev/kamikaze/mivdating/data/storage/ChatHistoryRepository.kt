package dev.kamikaze.mivdating.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.kamikaze.mivdating.data.models.ChatMessage
import dev.kamikaze.mivdating.data.models.ChunkEmbedding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Репозиторий для сохранения и загрузки истории чата
 */
class ChatHistoryRepository(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chat_history")

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    companion object {
        private val CHAT_HISTORY_KEY = stringPreferencesKey("chat_messages")
    }

    /**
     * Сохранить историю чата
     */
    suspend fun saveChatHistory(messages: List<ChatMessage>) {
        val serializable = messages.map { it.toSerializable() }
        val jsonString = json.encodeToString(serializable)

        context.dataStore.edit { preferences ->
            preferences[CHAT_HISTORY_KEY] = jsonString
        }
    }

    /**
     * Загрузить историю чата
     */
    suspend fun loadChatHistory(): List<ChatMessage> {
        return context.dataStore.data.map { preferences ->
            val jsonString = preferences[CHAT_HISTORY_KEY] ?: return@map emptyList()
            try {
                val serializable = json.decodeFromString<List<SerializableChatMessage>>(jsonString)
                serializable.map { it.toChatMessage() }
            } catch (e: Exception) {
                emptyList()
            }
        }.first()
    }

    /**
     * Очистить историю чата
     */
    suspend fun clearChatHistory() {
        context.dataStore.edit { preferences ->
            preferences.remove(CHAT_HISTORY_KEY)
        }
    }
}

/**
 * Сериализуемая версия ChatMessage (с источниками, но без embedding векторов)
 */
@Serializable
data class SerializableChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long,
    val sources: List<SerializableSearchResult> = emptyList()
)

/**
 * Сериализуемая версия SearchResult (без embedding векторов)
 */
@Serializable
data class SerializableSearchResult(
    val chunkId: String,
    val documentId: String,
    val content: String,
    val score: Double,
    val documentTitle: String
)

/**
 * Конвертация ChatMessage в сериализуемый формат
 */
private fun ChatMessage.toSerializable(): SerializableChatMessage {
    return SerializableChatMessage(
        id = id,
        text = text,
        isUser = isUser,
        timestamp = timestamp,
        sources = sources.map { it.toSerializable() }
    )
}

/**
 * Конвертация SearchResult в сериализуемый формат (без векторов)
 */
private fun SearchResult.toSerializable(): SerializableSearchResult {
    return SerializableSearchResult(
        chunkId = chunk.chunkId,
        documentId = chunk.documentId,
        content = chunk.content,
        score = score,
        documentTitle = documentTitle
    )
}

/**
 * Конвертация из сериализуемого формата обратно в ChatMessage
 */
private fun SerializableChatMessage.toChatMessage(): ChatMessage {
    return ChatMessage(
        id = id,
        text = text,
        isUser = isUser,
        timestamp = timestamp,
        sources = sources.map { it.toSearchResult() }
    )
}

/**
 * Конвертация из сериализуемого формата обратно в SearchResult
 * Векторы embedding будут пустыми, так как они не сохраняются
 */
private fun SerializableSearchResult.toSearchResult(): SearchResult {
    return SearchResult(
        chunk = ChunkEmbedding(
            chunkId = chunkId,
            documentId = documentId,
            content = content,
            embedding = emptyList() // Векторы не сохраняются
        ),
        score = score,
        documentTitle = documentTitle
    )
}
