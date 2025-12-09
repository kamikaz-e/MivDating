# API Reference - MivDating

## IndexingService

Сервис для индексации и поиска документов.

### Методы

#### `indexDocuments(documents: List<Document>)`
Индексирует документы в векторную базу.

**Параметры:**
- `documents: List<Document>` - список документов для индексации

**Процесс:**
1. Разбивка документов на чанки (512 символов, перекрытие 128)
2. Генерация эмбеддингов через Ollama
3. Сохранение в VectorDatabase

**Пример:**
```kotlin
val documents = listOf(
    Document("book1.txt", content)
)
indexingService.indexDocuments(documents)
```

#### `search(query: String, limit: Int = 10): List<SearchResult>`
Выполняет векторный поиск по запросу.

**Параметры:**
- `query: String` - поисковый запрос
- `limit: Int` - максимальное количество результатов (по умолчанию 10)

**Возвращает:**
- `List<SearchResult>` - список результатов с score

**Пример:**
```kotlin
val results = indexingService.search("главный герой", limit = 5)
results.forEach { result ->
    println("${result.document}: ${result.score}")
}
```

#### `searchWithFilter(query: String, config: FilterConfig): FilteredResults`
Выполняет поиск с применением фильтра релевантности.

**Параметры:**
- `query: String` - поисковый запрос
- `config: FilterConfig` - конфигурация фильтра

**Возвращает:**
- `FilteredResults` - отфильтрованные результаты с метриками

**Пример:**
```kotlin
val config = FilterConfig(
    threshold = 0.75f,
    useLengthBoost = true
)
val filtered = indexingService.searchWithFilter("главный герой", config)
println("Найдено: ${filtered.results.size}")
println("Средний score: ${filtered.metrics.avgScore}")
```

## VectorDatabase

База данных для хранения векторных эмбеддингов.

### Методы

#### `insert(chunk: DocumentChunk, embedding: FloatArray)`
Вставляет чанк и его эмбеддинг в базу.

**Параметры:**
- `chunk: DocumentChunk` - чанк документа
- `embedding: FloatArray` - векторное представление

#### `searchSimilar(queryEmbedding: FloatArray, limit: Int): List<SearchResult>`
Поиск похожих чанков по cosine similarity.

**Параметры:**
- `queryEmbedding: FloatArray` - эмбеддинг запроса
- `limit: Int` - количество результатов

**Возвращает:**
- `List<SearchResult>` - результаты поиска

#### `searchSimilarWithFilter(queryEmbedding: FloatArray, config: FilterConfig): FilteredResults`
Поиск с фильтрацией.

**Параметры:**
- `queryEmbedding: FloatArray` - эмбеддинг запроса
- `config: FilterConfig` - настройки фильтра

**Возвращает:**
- `FilteredResults` - отфильтрованные результаты

## RelevanceFilter

Фильтр релевантности результатов поиска.

### Классы данных

#### `FilterConfig`
```kotlin
data class FilterConfig(
    val threshold: Float = 0.7f,        // Порог фильтрации (0.0-1.0)
    val useLengthBoost: Boolean = false, // Reranking по длине
    val maxResults: Int = 5              // Макс. результатов
)
```

#### `FilteredResults`
```kotlin
data class FilteredResults(
    val results: List<SearchResult>,     // Отфильтрованные результаты
    val metrics: QualityMetrics,         // Метрики качества
    val originalCount: Int               // Исходное количество
)
```

#### `QualityMetrics`
```kotlin
data class QualityMetrics(
    val avgScore: Float,      // Средний score
    val minScore: Float,      // Минимальный score
    val maxScore: Float,      // Максимальный score
    val filteredCount: Int,   // Количество после фильтра
    val rejectedCount: Int    // Количество отброшенных
)
```

### Методы

#### `filter(results: List<SearchResult>, config: FilterConfig): FilteredResults`
Применяет фильтр к результатам.

**Алгоритм:**
1. Фильтрация по порогу (score >= threshold)
2. Опциональный reranking по длине контента
3. Сортировка по обновленному score
4. Ограничение по maxResults
5. Расчет метрик

**Пример:**
```kotlin
val filter = RelevanceFilter()
val config = FilterConfig(threshold = 0.75f, useLengthBoost = true)
val filtered = filter.filter(searchResults, config)
```

## OllamaViewModel (RAG)

ViewModel для RAG функционала.

### State

#### `RAGUiState`
```kotlin
data class RAGUiState(
    val results: List<SearchResult>,
    val filteredResults: FilteredResults?,
    val llmResponse: String,
    val filterThreshold: Float,
    val useFilter: Boolean,
    val useLengthBoost: Boolean,
    val comparisonMode: Boolean,
    val isLoading: Boolean
)
```

### Методы

#### `search(query: String)`
Выполняет поиск с учетом текущих настроек.

**Поведение:**
- Если `comparisonMode = true`: показывает результаты с фильтром и без
- Если `useFilter = true`: применяет фильтр
- Если `useFilter = false`: обычный поиск

#### `updateFilterThreshold(threshold: Float)`
Обновляет порог фильтрации.

**Параметры:**
- `threshold: Float` - новый порог (0.0-1.0)

#### `toggleFilter()`
Переключает использование фильтра.

#### `toggleLengthBoost()`
Переключает reranking по длине.

#### `toggleComparisonMode()`
Переключает режим сравнения.

#### `askLLM(question: String)`
Отправляет вопрос в LLM с контекстом из найденных чанков.

**Параметры:**
- `question: String` - вопрос пользователя

**Процесс:**
1. Выполняет поиск релевантных чанков
2. Формирует контекст из топ-5 чанков
3. Отправляет в Yandex GPT API
4. Обновляет UI с ответом и метриками

## ChatViewModel

ViewModel для чата с AI.

### State

#### `ChatUiState`
```kotlin
data class ChatUiState(
    val messages: List<Message>,
    val isLoading: Boolean,
    val currentInput: String
)
```

### Методы

#### `sendMessage(text: String)`
Отправляет сообщение в чат.

**Параметры:**
- `text: String` - текст сообщения

#### `clearHistory()`
Очищает историю чата.

## Data Classes

### `SearchResult`
```kotlin
data class SearchResult(
    val document: String,      // Имя документа
    val content: String,       // Содержимое чанка
    val score: Float,          // Релевантность (0.0-1.0)
    val chunkIndex: Int        // Индекс чанка
)
```

### `DocumentChunk`
```kotlin
data class DocumentChunk(
    val documentName: String,  // Имя документа
    val content: String,       // Текст чанка
    val chunkIndex: Int,       // Порядковый номер
    val startPosition: Int,    // Начальная позиция
    val endPosition: Int       // Конечная позиция
)
```

## Константы

### Размеры чанков
```kotlin
const val CHUNK_SIZE = 512          // Размер чанка в символах
const val CHUNK_OVERLAP = 128       // Перекрытие чанков
```

### Настройки поиска по умолчанию
```kotlin
const val DEFAULT_SEARCH_LIMIT = 10
const val DEFAULT_THRESHOLD = 0.7f
const val DEFAULT_MAX_RESULTS = 5
```

### Оптимальная длина для reranking
```kotlin
const val OPTIMAL_CHUNK_LENGTH = 500
```

## Примеры использования

### Базовый RAG поток
```kotlin
// 1. Индексация
val documents = loadDocuments()
indexingService.indexDocuments(documents)

// 2. Поиск
val results = indexingService.search("вопрос пользователя")

// 3. Отправка в LLM
val context = results.take(5).joinToString("\n") { it.content }
val response = llmService.ask(question, context)
```

### Поиск с фильтрацией
```kotlin
// Настройка фильтра
val config = FilterConfig(
    threshold = 0.75f,
    useLengthBoost = true,
    maxResults = 3
)

// Поиск
val filtered = indexingService.searchWithFilter("запрос", config)

// Проверка качества
if (filtered.metrics.avgScore > 0.8f) {
    println("Высокое качество результатов")
}
```

### Режим сравнения
```kotlin
// Без фильтра
val withoutFilter = indexingService.search("запрос", limit = 10)

// С фильтром
val config = FilterConfig(threshold = 0.7f)
val withFilter = indexingService.searchWithFilter("запрос", config)

// Сравнение
println("Без фильтра: ${withoutFilter.size}")
println("С фильтром: ${withFilter.results.size}")
println("Улучшение score: ${withFilter.metrics.avgScore}")
```
