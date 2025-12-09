# Структура проекта MivDating

## Обзор
MivDating - Android приложение на Kotlin с RAG (Retrieval-Augmented Generation) системой для работы с документацией и AI-ассистентом.

## Основные компоненты

### 1. UI Layer (app/src/main/kotlin/dev/kamikaze/mivdating/ui/)
- `OllamaRAGScreen.kt` - Экран RAG поиска и фильтрации
- `ChatScreen.kt` - Экран чата с AI
- Composable функции для отображения результатов

### 2. ViewModel Layer (app/src/main/kotlin/dev/kamikaze/mivdating/)
- `OllamaViewModel.kt` - ViewModel для RAG функционала
- `ChatViewModel.kt` - ViewModel для чата с AI
- Управление состоянием UI через StateFlow

### 3. Data Layer (app/src/main/kotlin/dev/kamikaze/mivdating/data/)

#### Индексация (data/indexing/)
- `IndexingService.kt` - Сервис индексации документов
- `IndexingProgress.kt` - Отслеживание прогресса индексации
- Разбивка на чанки (512 символов с перекрытием 128)

#### Хранилище (data/storage/)
- `VectorDatabase.kt` - SQLite база для векторных эмбеддингов
- Методы поиска: `search()`, `searchSimilarWithFilter()`

#### Фильтрация (data/filtering/)
- `RelevanceFilter.kt` - Фильтр релевантности результатов
- `FilterConfig` - Конфигурация фильтрации
- `FilteredResults` - Результаты с метриками
- Reranking по длине контента

## Технологический стек

### Android
- Kotlin
- Jetpack Compose (Material3)
- Coroutines & Flow
- ViewModel & StateFlow

### AI/ML
- Ollama (локальные эмбеддинги)
- Yandex GPT API (генерация ответов)
- Векторный поиск (cosine similarity)

### Storage
- SQLite для векторной базы
- Room (опционально)

## Архитектурные паттерны

### MVVM (Model-View-ViewModel)
```
UI (Composable) → ViewModel → Service → Data Layer
```

### Repository Pattern
- `IndexingService` как репозиторий для RAG операций
- `VectorDatabase` как источник данных

### Strategy Pattern
- `RelevanceFilter` с разными стратегиями фильтрации
- Опциональный reranking

## Основные функции

### 1. RAG система
- Индексация документов в векторную базу
- Семантический поиск по запросу
- Фильтрация результатов (threshold 0.0-1.0)
- Reranking по длине контента
- Интеграция с LLM для формирования ответов

### 2. Чат с AI
- История сообщений
- Сохранение контекста
- Интеграция с RAG для точных ответов

### 3. Режимы работы
- Без фильтра (топ-10 результатов)
- С фильтром (только релевантные)
- Режим сравнения (оба варианта)

## Конфигурация

### Gradle
- `build.gradle.kts` - основная конфигурация
- `settings.gradle.kts` - настройки проекта
- `app/build.gradle.kts` - конфигурация приложения

### Скрипты
- `start-ollama.sh` - запуск Ollama сервера

## Документация

### Основные файлы
- `RAG_COMPLETE_GUIDE.md` - полное руководство по RAG
- `RAG_FILTERING_GUIDE.md` - руководство по фильтрации
- `TESTING_GUIDE.md` - руководство по тестированию
- `CHANGES_SUMMARY.md` - сводка изменений

### project/docs/
- `PROJECT_STRUCTURE.md` (этот файл) - структура проекта
- `API_REFERENCE.md` - справка по API
- `CODE_STYLE.md` - стиль кода

## Git workflow

### Текущая ветка
Используйте `git branch` для проверки текущей ветки.

### Основные ветки
- `main` - основная ветка
- `lesson_XX` - ветки для уроков

## Сборка проекта

### Debug сборка
```bash
./gradlew assembleDebug
```

### Компиляция Kotlin
```bash
./gradlew compileDebugKotlin
```

### Полная сборка
```bash
./gradlew build
```

## Зависимости

### Android
- compileSdk: 34+
- minSdk: 24+
- Kotlin 1.9+

### Библиотеки
- Jetpack Compose BOM
- Material3
- Coroutines
- kotlinx.serialization

## Следующие шаги

1. Ознакомьтесь с RAG_COMPLETE_GUIDE.md
2. Изучите структуру кода в data/
3. Посмотрите примеры UI в ui/
4. Прочитайте TESTING_GUIDE.md для тестирования
