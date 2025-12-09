# Стиль кода - MivDating

## Общие принципы

### 1. Kotlin Conventions
Следуем [официальному стилю Kotlin](https://kotlinlang.org/docs/coding-conventions.html)

### 2. Android Best Practices
- MVVM архитектура
- Single Activity с Compose Navigation
- StateFlow для управления состоянием
- Coroutines для асинхронности

## Именование

### Классы и интерфейсы
```kotlin
// ✅ Хорошо
class IndexingService
data class FilterConfig
interface SearchRepository

// ❌ Плохо
class indexing_service
class filterConfig
```

### Функции
```kotlin
// ✅ Хорошо - глаголы в camelCase
fun searchDocuments()
suspend fun loadEmbeddings()
private fun calculateScore()

// ❌ Плохо
fun DocumentSearch()
fun search_documents()
```

### Переменные
```kotlin
// ✅ Хорошо
val searchResults: List<SearchResult>
var filterThreshold = 0.7f
private val _uiState = MutableStateFlow(...)

// ❌ Плохо
val SearchResults: List<SearchResult>
var filter_threshold = 0.7f
```

### Константы
```kotlin
// ✅ Хорошо - UPPER_SNAKE_CASE
const val CHUNK_SIZE = 512
const val DEFAULT_THRESHOLD = 0.7f

// ❌ Плохо
const val chunkSize = 512
const val defaultThreshold = 0.7f
```

## Структура файлов

### Порядок элементов в классе
```kotlin
class ExampleClass {
    // 1. Companion object
    companion object {
        const val TAG = "ExampleClass"
    }

    // 2. Properties
    private val repository: Repository
    private var state: State

    // 3. Init блоки
    init {
        // ...
    }

    // 4. Public методы
    fun publicMethod() { }

    // 5. Private методы
    private fun privateMethod() { }
}
```

### Imports
```kotlin
// 1. Android imports
import android.util.Log
import androidx.compose.runtime.*

// 2. Third-party imports
import kotlinx.coroutines.*

// 3. Project imports
import dev.kamikaze.mivdating.data.*
```

## Composable функции

### Именование
```kotlin
// ✅ Хорошо - PascalCase
@Composable
fun SearchResultCard(result: SearchResult) { }

@Composable
fun FilterSettingsSection(config: FilterConfig) { }

// ❌ Плохо
@Composable
fun searchResultCard() { }
```

### Параметры
```kotlin
// ✅ Хорошо - сначала обязательные, потом опциональные
@Composable
fun MyComponent(
    title: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) { }

// ❌ Плохо - хаотичный порядок
@Composable
fun MyComponent(
    modifier: Modifier = Modifier,
    title: String,
    enabled: Boolean = true,
    onButtonClick: () -> Unit
) { }
```

### Модификаторы
```kotlin
// ✅ Хорошо - modifier как первый опциональный параметр
@Composable
fun Card(
    content: String,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier) {
        Text(content)
    }
}
```

## Data Classes

### Структура
```kotlin
// ✅ Хорошо
data class FilterConfig(
    val threshold: Float = 0.7f,
    val useLengthBoost: Boolean = false,
    val maxResults: Int = 5
)

// С документацией для сложных классов
/**
 * Конфигурация фильтра релевантности
 * @property threshold Порог фильтрации (0.0-1.0)
 * @property useLengthBoost Применять reranking по длине
 * @property maxResults Максимальное количество результатов
 */
data class FilterConfig(
    val threshold: Float = 0.7f,
    val useLengthBoost: Boolean = false,
    val maxResults: Int = 5
)
```

## ViewModel

### StateFlow паттерн
```kotlin
class MyViewModel : ViewModel() {
    // ✅ Хорошо - private mutable, public read-only
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // ❌ Плохо - публичный MutableStateFlow
    val uiState = MutableStateFlow(UiState())
}
```

### Обновление state
```kotlin
// ✅ Хорошо - через copy
_uiState.update { currentState ->
    currentState.copy(
        isLoading = true,
        results = newResults
    )
}

// ❌ Плохо - прямое присваивание
_uiState.value.isLoading = true
```

## Coroutines

### Scope
```kotlin
class MyViewModel : ViewModel() {
    // ✅ Хорошо - viewModelScope для ViewModel
    fun loadData() {
        viewModelScope.launch {
            // ...
        }
    }
}
```

### Обработка ошибок
```kotlin
// ✅ Хорошо - с try-catch
viewModelScope.launch {
    try {
        val result = repository.getData()
        _uiState.update { it.copy(data = result) }
    } catch (e: Exception) {
        _uiState.update { it.copy(error = e.message) }
    }
}

// ❌ Плохо - без обработки ошибок
viewModelScope.launch {
    val result = repository.getData()
    _uiState.update { it.copy(data = result) }
}
```

## Функции

### Размер
```kotlin
// ✅ Хорошо - короткие, фокусированные функции
fun calculateScore(text: String, optimal: Int): Float {
    val ratio = text.length.toFloat() / optimal
    return when {
        ratio < 0.5 -> 0.95f
        ratio > 2.0 -> 0.95f
        ratio in 0.8..1.2 -> 1.05f
        else -> 1.0f
    }
}

// ❌ Плохо - слишком длинная функция (>50 строк)
fun processEverything() {
    // 100+ строк кода
}
```

### Single Expression Functions
```kotlin
// ✅ Хорошо - для простых функций
fun isValid(score: Float): Boolean = score >= 0.7f

fun formatScore(score: Float): String = "%.2f".format(score)

// ❌ Плохо - излишне сложная одностроковая функция
fun complexLogic(a: Int, b: Int): Int =
    if (a > b) a * 2 else if (b > a) b * 2 else (a + b) / 2
```

## Комментарии

### KDoc для публичного API
```kotlin
/**
 * Фильтрует результаты поиска по порогу релевантности
 *
 * @param results Список результатов для фильтрации
 * @param config Конфигурация фильтра
 * @return Отфильтрованные результаты с метриками
 */
fun filter(
    results: List<SearchResult>,
    config: FilterConfig
): FilteredResults
```

### Inline комментарии
```kotlin
// ✅ Хорошо - объясняет ПОЧЕМУ, а не ЧТО
// Используем 512 символов для оптимального баланса между
// контекстом и производительностью эмбеддингов
const val CHUNK_SIZE = 512

// ❌ Плохо - очевидный комментарий
// Устанавливаем размер чанка в 512
const val CHUNK_SIZE = 512
```

## Null Safety

### Предпочитайте non-null типы
```kotlin
// ✅ Хорошо
data class Config(
    val threshold: Float = 0.7f,
    val results: List<SearchResult> = emptyList()
)

// ❌ Плохо - без необходимости nullable
data class Config(
    val threshold: Float? = null,
    val results: List<SearchResult>? = null
)
```

### Safe calls
```kotlin
// ✅ Хорошо - safe call с elvis operator
val score = result?.score ?: 0.0f

// ✅ Хорошо - let для non-null обработки
result?.let { r ->
    processResult(r)
}

// ❌ Плохо - !! без проверки
val score = result!!.score
```

## Типы

### Type inference
```kotlin
// ✅ Хорошо - inference для очевидных типов
val threshold = 0.7f
val results = listOf<SearchResult>()

// ✅ Хорошо - явный тип для публичного API
val threshold: Float = 0.7f
```

## Форматирование

### Длина строки
- Максимум 120 символов
- Перенос на новую строку при превышении

### Отступы
- 4 пробела (не табы)

### Пустые строки
```kotlin
// ✅ Хорошо - логические блоки разделены
class MyClass {
    private val property1 = 1
    private val property2 = 2

    fun method1() {
        // ...
    }

    fun method2() {
        // ...
    }
}
```

## Специфика проекта

### RAG компоненты
```kotlin
// Именование для RAG
class IndexingService          // Сервисы индексации
class VectorDatabase          // Хранилище
data class SearchResult       // Результаты поиска
data class FilterConfig       // Конфигурация

// Prefix для UI состояний
data class RAGUiState
data class ChatUiState
```

### Метрики и логирование
```kotlin
// ✅ Хорошо - используйте Log с TAG
companion object {
    private const val TAG = "IndexingService"
}

Log.d(TAG, "Indexed ${count} documents")

// Для метрик используйте data classes
data class QualityMetrics(
    val avgScore: Float,
    val minScore: Float,
    val maxScore: Float
)
```

## Тестирование (TODO)

### Именование тестов
```kotlin
class IndexingServiceTest {
    @Test
    fun `search returns results sorted by score`() {
        // Given
        val service = IndexingService()

        // When
        val results = service.search("query")

        // Then
        assertTrue(results.isSortedByDescending { it.score })
    }
}
```

## Code Review Checklist

Перед коммитом проверьте:
- [ ] Код следует стилю проекта
- [ ] Нет предупреждений компилятора
- [ ] Все публичные API документированы
- [ ] Нет TODO в production коде
- [ ] StateFlow используется корректно (private mutable)
- [ ] Обработка ошибок присутствует
- [ ] Null safety соблюден
- [ ] Имена переменных понятные и описательные

## Инструменты

### ktlint
Автоматическая проверка стиля:
```bash
./gradlew ktlintCheck
./gradlew ktlintFormat
```

### Android Lint
```bash
./gradlew lint
```

### Detekt
Статический анализ:
```bash
./gradlew detekt
```
