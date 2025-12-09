# MivDating - Android RAG Application

Android приложение с интегрированной RAG (Retrieval-Augmented Generation) системой для работы с документацией и AI-ассистентом.

## Возможности

- **RAG система** - семантический поиск по документам с векторными эмбеддингами
- **Фильтрация результатов** - двухэтапная фильтрация с reranking
- **AI чат** - интеграция с Yandex GPT для ответов на основе контекста
- **Сравнительный режим** - визуализация результатов с фильтром и без

## Технологии

- **Kotlin** - основной язык разработки
- **Jetpack Compose** - современный UI toolkit
- **Ollama** - локальные эмбеддинги
- **Yandex GPT** - генерация ответов
- **SQLite** - векторная база данных
- **Coroutines & Flow** - асинхронность

## Быстрый старт

### Требования

- Android Studio Hedgehog+
- JDK 17+
- Android SDK 34+
- Ollama (для локальных эмбеддингов)

### Установка

1. Клонируйте репозиторий:
```bash
git clone <repository-url>
cd MivDating
```

2. Запустите Ollama:
```bash
./start-ollama.sh
```

3. Откройте проект в Android Studio

4. Синхронизируйте Gradle:
```bash
./gradlew build
```

5. Запустите приложение на эмуляторе или устройстве

## Структура проекта

```
MivDating/
├── app/src/main/kotlin/dev/kamikaze/mivdating/
│   ├── MainActivity.kt
│   ├── OllamaViewModel.kt      # RAG ViewModel
│   ├── ChatViewModel.kt        # Chat ViewModel
│   ├── data/
│   │   ├── indexing/          # Индексация документов
│   │   ├── storage/           # Векторная база
│   │   └── filtering/         # Фильтрация результатов
│   └── ui/
│       ├── OllamaRAGScreen.kt # RAG UI
│       └── ChatScreen.kt      # Chat UI
├── project/docs/              # Документация проекта
│   ├── PROJECT_STRUCTURE.md   # Структура проекта
│   ├── API_REFERENCE.md       # Справка по API
│   └── CODE_STYLE.md          # Стиль кода
├── RAG_COMPLETE_GUIDE.md      # Полное руководство по RAG
├── RAG_FILTERING_GUIDE.md     # Руководство по фильтрации
├── TESTING_GUIDE.md           # Руководство по тестированию
└── README.md                  # Этот файл
```

## Основные функции

### 1. Индексация документов

```kotlin
val documents = listOf(Document("book.txt", content))
indexingService.indexDocuments(documents)
```

Процесс:
- Разбивка на чанки (512 символов с перекрытием 128)
- Генерация эмбеддингов через Ollama
- Сохранение в SQLite базу

### 2. Семантический поиск

```kotlin
val results = indexingService.search("главный герой", limit = 10)
```

Возвращает топ-10 наиболее релевантных чанков с оценкой похожести.

### 3. Фильтрация результатов

```kotlin
val config = FilterConfig(
    threshold = 0.75f,      // Порог фильтрации
    useLengthBoost = true,  // Reranking по длине
    maxResults = 5          // Макс. результатов
)
val filtered = indexingService.searchWithFilter("запрос", config)
```

Применяет:
- Фильтрацию по порогу cosine similarity
- Опциональный reranking по длине контента
- Ограничение количества результатов

### 4. RAG с LLM

```kotlin
viewModel.askLLM("Кто главный герой книги?")
```

Процесс:
1. Поиск релевантных чанков
2. Формирование контекста
3. Отправка в Yandex GPT
4. Получение ответа на основе документов

## Режимы работы

### Без фильтра
- Обычный векторный поиск
- Топ-10 результатов
- Подходит для широких запросов

### С фильтром
- Только результаты выше порога
- Топ-5 лучших результатов
- Подходит для точных вопросов

### Режим сравнения
- Показывает оба варианта
- Статистика и метрики
- Полезен для настройки системы

## Настройка фильтра

### Рекомендуемые пороги

| Порог | Применение |
|-------|-----------|
| 0.75-0.90 | Точные вопросы, факты |
| 0.60-0.75 | Общие вопросы |
| 0.40-0.60 | Исследовательские запросы |

### Reranking по длине

Буст для чанков оптимальной длины (~500 символов):
- Слишком короткие: 0.95x
- Оптимальные (80-120%): 1.05x
- Слишком длинные: 0.95x

## Сборка

### Debug сборка
```bash
./gradlew assembleDebug
```

### Release сборка
```bash
./gradlew assembleRelease
```

### Запуск тестов
```bash
./gradlew test
```

## Документация

- [PROJECT_STRUCTURE.md](project/docs/PROJECT_STRUCTURE.md) - архитектура проекта
- [API_REFERENCE.md](project/docs/API_REFERENCE.md) - справка по API
- [CODE_STYLE.md](project/docs/CODE_STYLE.md) - стиль кода
- [RAG_COMPLETE_GUIDE.md](RAG_COMPLETE_GUIDE.md) - полное руководство по RAG
- [TESTING_GUIDE.md](TESTING_GUIDE.md) - руководство по тестированию

## Использование команды /help

Для получения справки о проекте используйте:
```
/help
```

Команда поддерживает вопросы о:
- Структуре проекта
- API и методах
- Стиле кода
- Архитектурных решениях
- Примерах использования

## Примеры вопросов для /help

- "Как работает RAG система?"
- "Где находится IndexingService?"
- "Какие параметры у FilterConfig?"
- "Как настроить порог фильтрации?"
- "Какой стиль именования для Composable функций?"

## Git workflow

Проверить текущую ветку:
```bash
git branch
```

Создать новую ветку:
```bash
git checkout -b feature/my-feature
```

## Архитектура

### MVVM
```
UI (Compose) → ViewModel → Service → Repository → Data Source
```

### Слои
- **UI Layer** - Composable функции
- **ViewModel Layer** - StateFlow, бизнес-логика
- **Service Layer** - IndexingService, фильтрация
- **Data Layer** - VectorDatabase, SQLite

## Метрики

Система предоставляет детальные метрики:
- Количество найденных результатов
- Средний/мин/макс score
- Количество отфильтрованных результатов
- Статистика токенов LLM

## Производительность

- Индексация: ~10-20 документов/сек
- Поиск: ~50-100ms
- Фильтрация: ~1-3ms
- LLM ответ: зависит от API

## Требования к системе

- minSdk: 24 (Android 7.0)
- targetSdk: 34 (Android 14)
- compileSdk: 34

## Лицензия

TODO: Указать лицензию

## Авторы

- Разработка RAG системы: 2024-2025
- Текущая версия: 1.0

## Поддержка

Для вопросов и обратной связи:
- Issues: создайте issue в репозитории
- Документация: см. project/docs/

## Roadmap

### Текущая версия (1.0)
- ✅ RAG система с индексацией
- ✅ Фильтрация и reranking
- ✅ Интеграция с Yandex GPT
- ✅ Режим сравнения

### Планируется
- [ ] Кросс-энкодер для reranking
- [ ] Гибридный поиск (векторный + текстовый)
- [ ] Персистентность настроек
- [ ] Экспорт метрик
- [ ] Unit & UI тесты
- [ ] CI/CD pipeline

## Благодарности

- Ollama - за локальные эмбеддинги
- Yandex Cloud - за GPT API
- Jetpack Compose - за современный UI

---

Для подробной информации см. [документацию](project/docs/).
