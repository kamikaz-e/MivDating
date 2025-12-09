# Шпаргалка команд AI-ассистента

## Важно: Откуда запускать команды

**Всегда запускайте из корня проекта:**
```bash
cd /Users/admin/StudioProjects/MivDating
```

Проверьте, что вы в правильной директории:
```bash
pwd
# Должно быть: /Users/admin/StudioProjects/MivDating
```

---

## 1. Команда /help (Claude Code)

**Запускается прямо в чате Claude Code:**

```
/help Как работает RAG система?
/help Где находится IndexingService?
/help Какие параметры у FilterConfig?
/help Как настроить порог фильтрации?
/help Какой стиль кода для Composable?
```

**Не требует установки - работает сразу! ✅**

---

## 2. MCP Git Server

### Из корня проекта:
```bash
# Тест сервера
python3 project/docs/mcp_git_server.py test

# Вывод:
# Текущая ветка: lesson_19
# Последний коммит: 485e5fc ...
```

### Или перейдите в папку:
```bash
cd project/docs
python3 mcp_git_server.py test
cd ../..  # вернуться в корень
```

---

## 3. RAG Индексатор

### Первый раз - индексация (разово):

```bash
# 1. Проверьте Ollama
curl http://localhost:11434/api/version

# Если не запущен:
ollama serve
# или
./start-ollama.sh

# 2. Загрузите модель (один раз)
ollama pull nomic-embed-text

# 3. Индексируйте документацию
python3 project/docs/rag_indexer.py index
```

Вывод будет:
```
=== Индексация документов проекта ===

✓ Загружен README.md (9145 символов)
✓ Загружен PROJECT_STRUCTURE.md (6123 символов)
...

Обработка README.md...
  Создано чанков: 18
  ✓ Готово

=== Индексация завершена ===
Всего проиндексировано чанков: 87

✓ Индекс сохранен в rag_index.json
```

### Поиск по документации:

```bash
# Поиск конкретной информации
python3 project/docs/rag_indexer.py search "как работает фильтрация"

# Вывод покажет топ-5 релевантных частей документации
```

### Тестовые запросы:

```bash
python3 project/docs/rag_indexer.py test
```

---

## Быстрый старт (копируй-вставь)

### Если Ollama уже запущен:

```bash
cd /Users/admin/StudioProjects/MivDating

# Индексация (один раз)
python3 project/docs/rag_indexer.py index

# Поиск
python3 project/docs/rag_indexer.py search "IndexingService"

# Git info
python3 project/docs/mcp_git_server.py test
```

### Если Ollama не запущен:

Откройте новый терминал:
```bash
ollama serve
```

Затем в основном терминале:
```bash
cd /Users/admin/StudioProjects/MivDating
ollama pull nomic-embed-text
python3 project/docs/rag_indexer.py index
```

---

## Алиасы для удобства

Добавьте в `~/.zshrc`:

```bash
# MivDating проект
alias miv='cd /Users/admin/StudioProjects/MivDating'

# RAG команды (запускаются из любой директории)
alias rag-index='python3 /Users/admin/StudioProjects/MivDating/project/docs/rag_indexer.py index'
alias rag-search='python3 /Users/admin/StudioProjects/MivDating/project/docs/rag_indexer.py search'
alias rag-test='python3 /Users/admin/StudioProjects/MivDating/project/docs/rag_indexer.py test'

# MCP команды
alias miv-git='python3 /Users/admin/StudioProjects/MivDating/project/docs/mcp_git_server.py test'
```

Применить изменения:
```bash
source ~/.zshrc
```

Теперь можно использовать из любой директории:
```bash
rag-search "фильтрация"
miv-git
```

---

## Проверка статуса компонентов

```bash
# 1. Проверка Ollama
curl -s http://localhost:11434/api/version
# Должен вернуть: {"version":"0.13.0"}

# 2. Проверка индекса RAG
ls -lh project/docs/rag_index.json
# Должен быть файл ~4-5 MB

# 3. Проверка команды /help
ls -la .claude/commands/help.md
# Должен существовать

# 4. Проверка MCP
python3 project/docs/mcp_git_server.py test | grep current_branch
# Должен вывести: "current_branch": "lesson_19"
```

---

## Частые ошибки

### ❌ Ошибка: No such file or directory

```bash
# Неправильно (из любой папки):
python3 mcp_git_server.py test

# Правильно:
cd /Users/admin/StudioProjects/MivDating
python3 project/docs/mcp_git_server.py test
```

### ❌ Ошибка: Ollama connection refused

```bash
# Решение: запустите Ollama в отдельном терминале
ollama serve

# Проверьте:
curl http://localhost:11434/api/version
```

### ❌ Ошибка: индекс не найден

```bash
# Решение: создайте индекс
python3 project/docs/rag_indexer.py index
```

---

## Примеры использования

### Пример 1: Найти информацию об API

```bash
cd /Users/admin/StudioProjects/MivDating
python3 project/docs/rag_indexer.py search "методы IndexingService"
```

### Пример 2: Узнать стиль кода

```
/help Как именовать Composable функции?
```

### Пример 3: Проверить текущую ветку

```bash
python3 project/docs/mcp_git_server.py test
```

---

## Что дальше?

1. **Попробуйте /help** прямо в этом чате
2. **Проиндексируйте документацию** один раз
3. **Добавьте алиасы** для удобства
4. **Создайте свою документацию** в `project/docs/`

---

## Нужна помощь?

```
/help Как добавить свою документацию?
/help Что такое RAG система?
/help Где находится VectorDatabase?
```

Команда `/help` ответит на основе документации проекта!
