package dev.kamikaze.mivdating.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.kamikaze.mivdating.utils.OllamaUrlHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.ollamaSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ollama_settings")

class OllamaSettings(private val context: Context) {
    
    companion object {
        private val OLLAMA_URL_KEY = stringPreferencesKey("ollama_url")
    }
    
    /**
     * Получает сохраненный адрес Ollama или использует дефолтный
     */
    val ollamaUrl: Flow<String> = context.ollamaSettingsDataStore.data.map { preferences ->
        preferences[OLLAMA_URL_KEY] ?: OllamaUrlHelper.getDefaultOllamaUrl(context)
    }
    
    /**
     * Сохраняет адрес Ollama
     */
    suspend fun setOllamaUrl(url: String) {
        context.ollamaSettingsDataStore.edit { preferences ->
            preferences[OLLAMA_URL_KEY] = url
        }
    }
    
    /**
     * Получает текущий адрес синхронно (для инициализации)
     */
    suspend fun getOllamaUrlSync(): String {
        val preferences = context.ollamaSettingsDataStore.data.first()
        return preferences[OLLAMA_URL_KEY] ?: OllamaUrlHelper.getDefaultOllamaUrl(context)
    }
}

