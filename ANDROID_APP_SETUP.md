# Настройка Android приложения для работы с удаленным сервером

## Обзор

Android приложение теперь поддерживает работу как с локальным Ollama, так и с удаленным API сервером.

## Режимы работы

### 1. Локальный режим (для разработки)

**Эмулятор:**
- URL: `http://10.0.2.2:11434`
- Ollama должен быть запущен на вашем компьютере
- Команды:
  ```bash
  ollama serve
  ollama pull qwen2.5:14b
  ollama pull nomic-embed-text
  ```

**Реальное устройство в локальной сети:**
- URL: `http://YOUR_COMPUTER_IP:11434`
- Найдите IP вашего компьютера:
  - Windows: `ipconfig`
  - Mac/Linux: `ifconfig` или `ip addr`
- Запустите Ollama с доступом из сети:
  ```bash
  export OLLAMA_HOST=0.0.0.0:11434
  ollama serve
  ```

### 2. Удаленный режим (production)

- URL: `http://130.49.153.154:8000`
- Используется Flask API сервер, который проксирует запросы к Ollama
- Настройка сервера описана в `REMOTE_SERVER_SETUP.md`

## Настройка URL в приложении

### Через код (для разработки)

Отредактируйте файл `OllamaSettings.kt` или передайте URL при создании `OllamaClient`:

```kotlin
val ollamaClient = OllamaClient(
    baseUrl = "http://130.49.153.154:8000"  // Удаленный сервер // "http://10.0.2.2:11434"      // Локальный Ollama
)
```

### Через UI (рекомендуется)

В приложении будет добавлен экран настроек, где можно выбрать:
- Локальный Ollama
- Удаленный сервер
- Пользовательский URL

## Изменения в коде

### 1. OllamaClient.kt

Добавлена возможность динамического изменения URL:

```kotlin
// Обновить URL
ollamaClient.updateBaseUrl("http://130.49.153.154:8000")
```

### 2. OllamaSettings.kt

Сохранение и загрузка URL из DataStore:

```kotlin
// Сохранить URL
ollamaSettings.setOllamaUrl("http://130.49.153.154:8000")

// Получить URL
ollamaSettings.ollamaUrl.collect { url ->
    // Используйте URL
}
```

### 3. RAGViewModel.kt

ViewModel автоматически загружает URL из настроек и применяет к клиенту:

```kotlin
// URL загружается автоматически при инициализации
// Для обновления URL:
viewModel.updateOllamaUrl("http://130.49.153.154:8000")
```

## Тестирование подключения

### 1. Проверка локального Ollama

```bash
# На компьютере
curl http://localhost:11434/api/tags

# С эмулятора
curl http://10.0.2.2:11434/api/tags
```

### 2. Проверка удаленного сервера

```bash
# Проверка здоровья
curl http://130.49.153.154:8000/health

# Проверка доступных моделей
curl http://130.49.153.154:8000/api/tags

# Тестовый запрос
curl -X POST http://130.49.153.154:8000/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen2.5:14b",
    "messages": [{"role": "user", "content": "Привет!"}],
    "stream": false
  }'
```

## Управление разрешениями

Убедитесь, что в `AndroidManifest.xml` присутствуют разрешения:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

Для cleartext traffic (HTTP) добавьте в `AndroidManifest.xml`:

```xml
<application
    android:usesCleartextTraffic="true"
/>
```

Или создайте `network_security_config.xml` для более точной настройки:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">10.0.2.2</domain>
        <domain includeSubdomains="true">192.168.0.0/16</domain>
        <domain includeSubdomains="true">130.49.153.154</domain>
    </domain-config>
</network-security-config>
```

## Построение и запуск

### Debug сборка (с локальным URL)

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Release сборка (с удаленным URL)

1. Обновите `baseUrl` в коде на удаленный сервер
2. Соберите APK:

```bash
./gradlew assembleRelease
```

## Логирование

Для отладки подключения используйте Logcat:

```bash
# Фильтр по тегу
adb logcat -s OllamaClient RAGViewModel

# Полные логи
adb logcat | grep -i ollama
```

## Troubleshooting

### Ошибка: "Connection refused"

**Причины:**
1. Ollama/API сервер не запущен
2. Неправильный URL
3. Файрвол блокирует подключение

**Решение:**
1. Проверьте, что сервер запущен
2. Проверьте URL в настройках
3. Отключите файрвол или добавьте правило

### Ошибка: "Timeout"

**Причины:**
1. Сервер слишком медленно отвечает
2. Модель не загружена
3. Недостаточно ресурсов на сервере

**Решение:**
1. Увеличьте таймаут в `OllamaClient.kt`
2. Проверьте, что модель загружена: `ollama list`
3. Проверьте ресурсы сервера

### Ошибка: "Model not found"

**Причины:**
1. Модель не загружена на сервере

**Решение:**
```bash
# На сервере
ollama pull qwen2.5:14b
ollama pull nomic-embed-text
```

### Ошибка: "Cleartext traffic not permitted"

**Причины:**
1. Android не разрешает HTTP трафик по умолчанию

**Решение:**
Добавьте `android:usesCleartextTraffic="true"` в `AndroidManifest.xml`

## Мониторинг производительности

Используйте Android Profiler для мониторинга:
- Сетевой активности
- Использования памяти
- CPU

## Рекомендации

1. **Для разработки**: используйте локальный Ollama (быстрее)
2. **Для тестирования**: используйте удаленный сервер
3. **Для production**: используйте HTTPS и добавьте аутентификацию
4. **Оптимизация**: кешируйте эмбеддинги локально
5. **Безопасность**: не храните API ключи в коде, используйте BuildConfig

## Следующие шаги

1. Добавить UI для выбора режима (локальный/удаленный)
2. Добавить аутентификацию через API ключи
3. Настроить HTTPS для production
4. Добавить кеширование ответов
5. Реализовать обработку ошибок сети
