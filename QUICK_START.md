# Быстрый старт - AI Ассистент разработчика

## Что уже работает СЕЙЧАС

### 1. Команда /help - работает БЕЗ настройки ✅

Вы уже можете использовать команду `/help` прямо в этом чате с Claude Code!

**Попробуйте сейчас:**
```
/help Где находится IndexingService?
```

**Как это работает:**
- Claude Code автоматически видит файл `.claude/commands/help.md`
- При вызове `/help` он читает нужные документы из `project/docs/`
- Находит информацию и отвечает на основе документации

**Примеры вопросов:**
```
/help Какие параметры у FilterConfig?
/help Как работает фильтрация в RAG?
/help Какой стиль кода для Composable?
/help Где находится VectorDatabase?
```

---

## 2. RAG индексатор - нужна разовая настройка

### Шаг 1: Проверьте Ollama

```bash
# Проверьте, запущен ли Ollama
curl http://localhost:11434/api/version

# Если нет - запустите
ollama serve

# Или используйте готовый скрипт
./start-ollama.sh
```

### Шаг 2: Установите модель для эмбеддингов

```bash
ollama pull nomic-embed-text
```

Вывод должен быть:
```
pulling manifest
pulling 970aa74c0a90... 100%
success
```

### Шаг 3: Проиндексируйте документацию

```bash
cd project/docs
python3 rag_indexer.py index
```

Вывод:
```
=== Индексация документов проекта ===

✓ Загружен README.md (9145 символов)
✓ Загружен PROJECT_STRUCTURE.md (6123 символов)
✓ Загружен API_REFERENCE.md (14856 символов)
✓ Загружен CODE_STYLE.md (12345 символов)
...

Обработка README.md...
  Создано чанков: 18
  Обработано чанков: 18/18
  ✓ Готово

=== Индексация завершена ===
Всего проиндексировано чанков: 87

✓ Индекс сохранен в rag_index.json
  Размер файла: 4582.34 KB
```

### Шаг 4: Протестируйте поиск

```bash
python3 rag_indexer.py search "Как работает фильтрация"
```

Вывод:
```
Поиск: 'Как работает фильтрация'

Найдено результатов: 5

[1] Score: 0.856
    Источник: API_REFERENCE.md (чанк 15)
    Фильтрует результаты поиска по порогу релевантности

    Параметры:
    - results: List<SearchResult> - список результатов для фильтрации
    - config: FilterConfig - конфигурация фильтра
    ...

[2] Score: 0.782
    Источник: RAG_FILTERING_GUIDE.md (чанк 8)
    ...
```

### Шаг 5: Используйте в коде

```python
from rag_indexer import search

# Поиск информации
results = search("Где находится IndexingService?", top_k=3)

for r in results:
    print(f"{r['source']}: {r['content'][:100]}...")
```

---

## 3. MCP Git Server - работает локально

MCP сервер уже работает в тестовом режиме:

```bash
cd project/docs
python3 mcp_git_server.py test
```

Вывод:
```
=== Тест MCP Git Server ===

1. Текущая ветка:
   lesson_19

2. Информация о ветке:
{
  "current_branch": "lesson_19",
  "last_commit": "485e5fc Поправил неправильные номера...",
  "all_branches": ["lesson_15", "lesson_16", ..., "lesson_19", "master"]
}
```

---

## Как подключить как плагин?

### Вариант 1: Локальное использование (уже работает)

**Что уже доступно БЕЗ настройки:**

1. **Команда /help** - просто используйте в Claude Code
   ```
   /help ваш вопрос о проекте
   ```

2. **RAG через Python** - после индексации
   ```bash
   python3 project/docs/rag_indexer.py search "запрос"
   ```

3. **MCP локально** - для скриптов
   ```bash
   python3 project/docs/mcp_git_server.py test
   ```

---

### Вариант 2: Интеграция MCP с Claude Desktop (расширенная)

Если вы хотите, чтобы MCP работал как полноценный плагин в Claude Desktop:

#### Шаг 1: Создайте конфигурацию MCP

Создайте файл `~/.config/claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "git-info": {
      "command": "python3",
      "args": [
        "/Users/admin/StudioProjects/MivDating/project/docs/mcp_git_server.py"
      ]
    }
  }
}
```

#### Шаг 2: Перезапустите Claude Desktop

После перезапуска Claude Desktop автоматически подключит MCP сервер.

#### Шаг 3: Используйте инструменты

В чате с Claude Desktop:
```
Какая у меня текущая ветка в проекте MivDating?
```

Claude автоматически вызовет `git_current_branch` и ответит.

---

### Вариант 3: Создание custom Claude Code hook

Вы можете создать hook, который автоматически использует RAG при определенных вопросах.

Создайте `.claude/hooks/rag_search.py`:

```python
#!/usr/bin/env python3
import sys
import json
sys.path.append('/Users/admin/StudioProjects/MivDating/project/docs')

from rag_indexer import search

# Читаем входные данные
input_data = json.loads(sys.stdin.read())
query = input_data.get('query', '')

# Выполняем RAG поиск
results = search(query, top_k=3)

# Возвращаем результаты
print(json.dumps({
    'results': results
}))
```

---

## Практические сценарии использования

### Сценарий 1: Вопрос о коде во время разработки

```
Вы: /help Какие методы есть у IndexingService?

Claude: Читаю API_REFERENCE.md...

IndexingService имеет следующие методы:

1. indexDocuments(documents: List<Document>)
   - Индексирует документы в векторную базу

2. search(query: String, limit: Int = 10): List<SearchResult>
   - Выполняет векторный поиск

3. searchWithFilter(query: String, config: FilterConfig): FilteredResults
   - Поиск с фильтрацией

Источник: project/docs/API_REFERENCE.md
```

### Сценарий 2: Поиск примера кода

```
Вы: /help Покажи пример использования FilterConfig

Claude: Вот пример из API_REFERENCE.md:

```kotlin
val config = FilterConfig(
    threshold = 0.75f,
    useLengthBoost = true,
    maxResults = 5
)
val filtered = indexingService.searchWithFilter("запрос", config)
```

Источник: project/docs/API_REFERENCE.md:123
```

### Сценарий 3: RAG поиск по всей документации

```bash
# Ищем информацию о reranking
python3 project/docs/rag_indexer.py search "что такое reranking"

# Результат покажет все релевантные части из разных документов
# с указанием score и источника
```

### Сценарий 4: Проверка git статуса

```bash
# Быстрая проверка текущей ветки
python3 project/docs/mcp_git_server.py test | grep "current_branch"

# Вывод: "current_branch": "lesson_19"
```

---

## Автоматизация с помощью алиасов

Добавьте в `~/.zshrc` или `~/.bashrc`:

```bash
# Переход в проект
alias miv='cd /Users/admin/StudioProjects/MivDating'

# RAG поиск
alias rag-search='python3 /Users/admin/StudioProjects/MivDating/project/docs/rag_indexer.py search'

# Переиндексация
alias rag-index='python3 /Users/admin/StudioProjects/MivDating/project/docs/rag_indexer.py index'

# Git info через MCP
alias miv-branch='python3 /Users/admin/StudioProjects/MivDating/project/docs/mcp_git_server.py test'
```

Использование:
```bash
# Быстрый поиск
rag-search "как работает фильтрация"

# Проверка ветки
miv-branch
```

---

## Интеграция в IDE (Android Studio)

### External Tools в Android Studio

1. **Settings → Tools → External Tools → Add**

2. **RAG Search:**
   - Name: `RAG Search`
   - Program: `python3`
   - Arguments: `$ProjectFileDir$/project/docs/rag_indexer.py search "$Prompt$"`
   - Working directory: `$ProjectFileDir$`

3. **MCP Git Info:**
   - Name: `Git Branch Info`
   - Program: `python3`
   - Arguments: `$ProjectFileDir$/project/docs/mcp_git_server.py test`
   - Working directory: `$ProjectFileDir$`

Теперь вы можете вызывать эти команды из меню **Tools → External Tools**.

---

## Troubleshooting

### Проблема: /help не работает

**Решение:**
```bash
# Проверьте, что файл существует
ls -la .claude/commands/help.md

# Убедитесь, что вы в правильной директории
pwd
# Должно быть: /Users/admin/StudioProjects/MivDating
```

### Проблема: RAG не находит документы

**Решение:**
```bash
cd project/docs

# Переиндексируйте
python3 rag_indexer.py index

# Проверьте индекс
ls -lh rag_index.json
# Должен быть ~4-5 MB
```

### Проблема: Ollama недоступен

**Решение:**
```bash
# Запустите Ollama
ollama serve

# Или в отдельном терминале
./start-ollama.sh

# Проверьте
curl http://localhost:11434/api/version
```

### Проблема: MCP сервер не запускается

**Решение:**
```bash
# Сделайте исполняемым
chmod +x project/docs/mcp_git_server.py

# Проверьте Python
which python3
python3 --version  # Должен быть 3.7+
```

---

## Что дальше?

### 1. Попробуйте команду /help прямо сейчас
```
/help Как работает RAG система?
```

### 2. Проиндексируйте документацию
```bash
cd project/docs
python3 rag_indexer.py index
```

### 3. Протестируйте поиск
```bash
python3 rag_indexer.py test
```

### 4. Добавьте свою документацию

Создайте новый `.md` файл в `project/docs/`:
```markdown
# Моя документация

Здесь описание вашего компонента...
```

Переиндексируйте:
```bash
python3 rag_indexer.py index
```

Теперь `/help` будет отвечать и по вашей документации!

---

## Резюме: Что работает прямо сейчас

| Компонент | Статус | Как использовать |
|-----------|--------|------------------|
| Команда /help | ✅ Готово | `/help ваш вопрос` |
| RAG поиск | ⚙️ Нужна индексация | `python3 rag_indexer.py index` |
| MCP сервер | ✅ Готово | `python3 mcp_git_server.py test` |
| Документация | ✅ Готово | Читайте через `/help` |

**Начните с `/help` - он работает БЕЗ настройки!**

---

Есть вопросы? Попробуйте:
```
/help Как добавить свою документацию в RAG?
```
