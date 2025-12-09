# Миграция с Yandex GPT на Ollama qwen3:14b

## ✅ Выполнено

### 1. Удалены файлы Yandex GPT
- ❌ `YandexGptClient.kt` - клиент API Yandex
- ❌ `YandexGptModels.kt` - модели данных Yandex
- ❌ `ChatViewModel.kt` - неиспользуемый ViewModel

### 2. Обновлены конфигурации
- Удалены API ключи Yandex из `app/build.gradle.kts`
- Удалены `YANDEX_API_KEY` и `YANDEX_FOLDER_ID`

### 3. Расширен OllamaClient

**Добавлены методы для chat completion:**

```kotlin
// Обычный чат
suspend fun chat(
    userMessage: String,
    conversationHistory: List<OllamaChatMessage> = emptyList()
): OllamaChatResponse

// Чат с RAG контекстом
suspend fun chatWithContext(
    userMessage: String,
    context: String,
    conversationHistory: List<OllamaChatMessage> = emptyList()
): OllamaChatResponse
```

**Добавлена поддержка NDJSON:**
- Ollama возвращает ответы в формате NDJSON (newline-delimited JSON)
- Метод `parseNdjsonChatResponse()` собирает токены из всех строк
- Поддержка reasoning mode (поле `thinking` у qwen3:14b)

### 4. Обновлены ViewModels

**ChatViewModel (удален):**
- Был неиспользуемым

**OllamaViewModel (RAGViewModel):**
- Заменен `YandexGptClient` на `OllamaClient`
- Обновлены типы сообщений: `MessageRequest.Message` → `OllamaChatMessage`
- Обновлена обработка ответов: `response.text` → `response.message.content`

### 5. Созданы новые модели данных

**OllamaChatRequest.kt:**
```kotlin
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatMessage>,
    val stream: Boolean = false,
    val options: OllamaChatOptions? = null
)

data class OllamaChatMessage(
    val role: String,  // "system", "user", "assistant"
    val content: String,
    val thinking: String? = null  // Reasoning mode для qwen3
)

data class OllamaChatOptions(
    val temperature: Double = 0.3,
    val num_predict: Int? = null
)
```

**OllamaChatResponse.kt:**
```kotlin
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
```

## 🔧 Особенности реализации

### NDJSON парсинг
Ollama API возвращает стриминговые ответы даже при `stream: false`. Каждый токен приходит отдельной строкой JSON.

**Пример ответа:**
```json
{"model":"qwen3:14b","message":{"role":"assistant","content":"","thinking":"Okay"},"done":false}
{"model":"qwen3:14b","message":{"role":"assistant","content":"Android"},"done":false}
{"model":"qwen3:14b","message":{"role":"assistant","content":" —"},"done":false}
...
{"model":"qwen3:14b","message":{"role":"assistant","content":""},"done":true,"total_duration":12000000}
```

**Решение:**
1. Читаем весь ответ как текст: `response.bodyAsText()`
2. Разбиваем на строки
3. Парсим каждую строку как JSON
4. Собираем все части `content` в один текст
5. Берем метаданные из последней строки с `done: true`

### Reasoning Mode (qwen3:14b)
Модель qwen3:14b использует reasoning mode:
- Сначала "думает" (поле `thinking`)
- Потом генерирует ответ (поле `content`)

Мы собираем оба поля и используем `content` как финальный ответ.

## 📊 Производительность

**Модель: qwen3:14b**
- Размер: 9.3 GB
- Параметры: 14.8B (Q4_K_M quantization)
- RAM: ~12-16 GB

**Скорость:**
- Первый запрос: 30-60 сек (загрузка модели)
- Последующие: 5-30 сек (зависит от контекста)

**Timeout:**
- Request: 5 минут
- Connect: 30 секунд
- Socket: 5 минут

## 🎯 Итог

Приложение теперь полностью работает на локальном Ollama с моделью qwen3:14b:
- ✅ Embeddings: `nomic-embed-text`
- ✅ Chat: `qwen3:14b`
- ✅ RAG: `qwen3:14b` + vector search
- ✅ История чата сохраняется
- ✅ Источники цитируются в ответах
- ✅ NDJSON парсинг работает корректно
- ✅ Reasoning mode поддерживается

**Нет зависимостей от внешних API!** 🎉
