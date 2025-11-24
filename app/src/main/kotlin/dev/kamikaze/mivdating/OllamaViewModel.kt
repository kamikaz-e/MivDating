package dev.kamikaze.mivdating

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kamikaze.mivdating.data.OllamaClient
import kotlinx.coroutines.launch

class OllamaViewModel : ViewModel() {
    private val ollamaClient = OllamaClient()
    
    var result by mutableStateOf("")
        private set
    
    var isLoading by mutableStateOf(false)
        private set

    fun testEmbedding() {
        viewModelScope.launch {
            isLoading = true
            result = try {
                val embedding = ollamaClient.embed("Hello, Ollama!")
                "Успех! Получен вектор размером ${embedding.size}\n" +
                "Первые 5 значений: ${embedding.take(5).joinToString(", ")}"
            } catch (e: Exception) {
                "Ошибка: ${e.message}"
            }
            isLoading = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        ollamaClient.close()
    }
}
