# Быстрая инструкция по настройке удаленного сервера

## Что уже готово

✅ Flask API сервер создан (`ollama_api_server.py`)
✅ Android приложение модифицировано
✅ UI для настройки подключения добавлен
✅ Документация подготовлена

## Что нужно сделать

### Шаг 1: Настройка сервера (130.49.153.154)

Подключитесь к серверу:
```bash
ssh user@130.49.153.154
```

Установите Ollama:
```bash
curl -fsSL https://ollama.com/install.sh | sh
```

Загрузите модели:
```bash
ollama pull qwen2.5:14b
ollama pull nomic-embed-text
```

Создайте директорию проекта:
```bash
mkdir -p ~/ollama-api
cd ~/ollama-api
```

Скопируйте файлы с локального компьютера на сервер:
```bash
# Выполните на ВАШЕМ компьютере (не на сервере)
cd /Users/admin/StudioProjects/MivDating

scp ollama_api_server.py user@130.49.153.154:~/ollama-api/
scp requirements.txt user@130.49.153.154:~/ollama-api/
scp run_server.sh user@130.49.153.154:~/ollama-api/
```

На сервере установите зависимости:
```bash
cd ~/ollama-api
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

Запустите Ollama:
```bash
# В одном терминале
OLLAMA_HOST=0.0.0.0:11434 ollama serve
```

Запустите Flask API (в другом терминале):
```bash
cd ~/ollama-api
source venv/bin/activate
python3 ollama_api_server.py
```

Откройте порт 8000:
```bash
sudo ufw allow 8000/tcp
```

Проверьте работу:
```bash
curl http://130.49.153.154:8000/health
```

### Шаг 2: Настройка приложения

1. Соберите и запустите приложение:
   ```bash
   cd /Users/admin/StudioProjects/MivDating
   ./gradlew assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

2. В приложении:
   - Откройте чат
   - Нажмите на иконку настроек (⚙️) в верхнем правом углу
   - Выберите "Удаленный сервер"
   - Нажмите "Сохранить"

3. Отправьте тестовое сообщение в чат

### Шаг 3: Проверка

Если подключение успешно, вы увидите:
- ✅ Зеленый индикатор подключения
- Адрес сервера: http://130.49.153.154:8000

Если есть проблемы:
- ❌ Красный индикатор
- Проверьте логи на сервере: `journalctl -f`
- Проверьте логи приложения: `adb logcat -s OllamaClient`

## Автозапуск сервера (опционально)

Для автоматического запуска при перезагрузке сервера, следуйте инструкциям в `REMOTE_SERVER_SETUP.md` (раздел "Настройка systemd").

## Полезные команды

**На сервере:**
```bash
# Проверка статуса Ollama
curl http://localhost:11434/api/tags

# Проверка статуса API
curl http://localhost:8000/health

# Просмотр логов
journalctl -f

# Остановка процессов
pkill ollama
pkill python3
```

**На локальном компьютере:**
```bash
# Проверка доступности сервера
curl http://130.49.153.154:8000/health

# Просмотр логов приложения
adb logcat -s OllamaClient RAGViewModel

# Пересборка приложения
./gradlew clean assembleDebug
```

## Режимы работы

Приложение поддерживает 3 режима:

1. **Локальный Ollama (Эмулятор)** - `http://10.0.2.2:11434`
   - Для разработки на эмуляторе
   - Ollama на вашем компьютере

2. **Удаленный сервер** - `http://130.49.153.154:8000`
   - Для production
   - Flask API на удаленной машине

3. **Пользовательский URL**
   - Введите любой адрес
   - Например, для реального устройства в локальной сети

## Troubleshooting

### Сервер не отвечает

1. Проверьте, что Ollama запущен:
   ```bash
   ps aux | grep ollama
   ```

2. Проверьте, что Flask API запущен:
   ```bash
   ps aux | grep python
   ```

3. Проверьте порт:
   ```bash
   sudo netstat -tlnp | grep 8000
   ```

### Приложение не подключается

1. Проверьте URL в настройках приложения
2. Проверьте, что устройство имеет доступ к интернету
3. Проверьте файрвол на сервере
4. Проверьте логи: `adb logcat -s OllamaClient`

### Модель не найдена

На сервере:
```bash
ollama list  # Проверить установленные модели
ollama pull qwen2.5:14b  # Установить модель
```

## Дополнительная документация

- `REMOTE_SERVER_SETUP.md` - Подробная настройка сервера
- `ANDROID_APP_SETUP.md` - Настройка Android приложения
- `INTEGRATION_COMPLETE.md` - Обзор интеграции

## Поддержка

Если возникли проблемы:
1. Проверьте логи на сервере
2. Проверьте логи приложения
3. Убедитесь, что все порты открыты
4. Проверьте, что модели загружены
