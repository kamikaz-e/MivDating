# Инструкция по развертыванию на удаленном сервере

## Требования

- Виртуальный сервер с доступом по SSH
- Python 3.8+
- Ollama установлен и запущен
- Модель `tinyllama` установлена

## Шаг 1: Подготовка сервера

### Установка Ollama (если еще не установлен)

```bash
curl -fsSL https://ollama.com/install.sh | sh
```

### Установка модели

```bash
ollama pull tinyllama
```

### Проверка установки

```bash
ollama list
# Должна быть модель tinyllama
```

## Шаг 2: Копирование файлов на сервер

```bash
# Создайте директорию на сервере
ssh user@130.49.153.154 "mkdir -p ~/ollama-api"

# Скопируйте файлы
scp ollama_api_server.py user@130.49.153.154:~/ollama-api/
scp requirements.txt user@130.49.153.154:~/ollama-api/
scp run_server.sh user@130.49.153.154:~/ollama-api/
```

## Шаг 3: Установка зависимостей

```bash
ssh user@130.49.153.154

cd ~/ollama-api
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

## Шаг 4: Настройка Ollama для работы в сети

По умолчанию Ollama слушает только localhost. Для работы с удаленным сервером нужно:

```bash
# Остановите текущий Ollama (если запущен)
pkill ollama

# Запустите Ollama с доступом из сети
OLLAMA_HOST=0.0.0.0 ollama serve
```

Или создайте systemd service для автозапуска:

```bash
sudo nano /etc/systemd/system/ollama.service
```

Содержимое файла:
```ini
[Unit]
Description=Ollama Service
After=network.target

[Service]
Type=simple
User=your_user
Environment="OLLAMA_HOST=0.0.0.0"
ExecStart=/usr/local/bin/ollama serve
Restart=always

[Install]
WantedBy=multi-user.target
```

Запуск:
```bash
sudo systemctl enable ollama
sudo systemctl start ollama
```

## Шаг 5: Запуск Flask API сервера

### Вариант 1: Запуск с проверками (рекомендуется)

```bash
cd ~/ollama-api
chmod +x start_ollama_api.sh
./start_ollama_api.sh
```

Этот скрипт автоматически:
- Проверяет наличие файлов
- Создает виртуальное окружение при необходимости
- Устанавливает зависимости
- Проверяет доступность Ollama
- Проверяет свободность порта
- Запускает сервер

### Вариант 2: Запуск через gunicorn (production)

```bash
cd ~/ollama-api
source venv/bin/activate
chmod +x run_server.sh
./run_server.sh
```

### Вариант 3: Запуск вручную (для тестирования)

```bash
cd ~/ollama-api
source venv/bin/activate
python3 ollama_api_server.py
```

### Вариант 3: Systemd service (автозапуск)

Создайте файл `/etc/systemd/system/ollama-api.service`:

```ini
[Unit]
Description=Ollama API Server
After=network.target ollama.service
Requires=ollama.service

[Service]
Type=simple
User=your_user
WorkingDirectory=/home/your_user/ollama-api
Environment="PATH=/home/your_user/ollama-api/venv/bin"
ExecStart=/home/your_user/ollama-api/venv/bin/gunicorn \
    --bind 0.0.0.0:8000 \
    --workers 4 \
    --timeout 600 \
    --access-logfile - \
    --error-logfile - \
    --log-level info \
    ollama_api_server:app
Restart=always

[Install]
WantedBy=multi-user.target
```

Запуск:
```bash
sudo systemctl enable ollama-api
sudo systemctl start ollama-api
sudo systemctl status ollama-api
```

## Шаг 6: Настройка firewall

Убедитесь, что порт 8000 открыт:

```bash
# UFW
sudo ufw allow 8000/tcp

# Или iptables
sudo iptables -A INPUT -p tcp --dport 8000 -j ACCEPT
```

## Шаг 7: Проверка работы

### Проверка health endpoint

```bash
curl http://130.49.153.154:8000/health
```

Ожидаемый ответ:
```json
{
  "status": "ok",
  "ollama_available": true,
  "ollama_url": "http://localhost:11434"
}
```

### Проверка списка моделей

```bash
curl http://130.49.153.154:8000/api/tags
```

### Тестовый запрос к чату

```bash
curl -X POST http://130.49.153.154:8000/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "model": "tinyllama",
    "messages": [
      {"role": "user", "content": "Привет!"}
    ],
    "stream": false
  }'
```

## Настройка в Android приложении

1. Откройте приложение
2. Нажмите на иконку настроек (⚙️) в верхней панели
3. Выберите "Удаленный сервер (tinyllama)" или введите вручную: `http://130.49.153.154:8000`
4. Нажмите "Сохранить"

## Мониторинг и логи

### Просмотр логов Ollama

```bash
journalctl -u ollama -f
```

### Просмотр логов API сервера

```bash
journalctl -u ollama-api -f
```

Или если запущен через gunicorn напрямую:
```bash
tail -f ~/ollama-api/logs/access.log
tail -f ~/ollama-api/logs/error.log
```

## Устранение проблем

### Ошибка: "Failed to connect to server"

Если вы получаете ошибку `curl: (7) Failed to connect to 130.49.153.154 port 8000`:

#### 1. Проверьте, запущен ли сервер

```bash
# На сервере выполните:
ps aux | grep gunicorn
# или
ps aux | grep python | grep ollama_api_server
```

Если процесс не найден, запустите сервер:
```bash
cd ~/ollama-api
source venv/bin/activate
./run_server.sh
```

#### 2. Проверьте, слушает ли сервер на порту 8000

```bash
# На сервере:
netstat -tuln | grep 8000
# или
ss -tuln | grep 8000
```

Если порт не слушается, проверьте:
- Запущен ли gunicorn
- Нет ли ошибок в логах
- Правильно ли указан порт в `run_server.sh`

#### 3. Проверьте firewall

```bash
# UFW
sudo ufw status
sudo ufw allow 8000/tcp

# iptables
sudo iptables -L -n | grep 8000
sudo iptables -A INPUT -p tcp --dport 8000 -j ACCEPT
```

#### 4. Проверьте, что сервер слушает на 0.0.0.0, а не только на localhost

В `run_server.sh` должно быть:
```bash
--bind 0.0.0.0:8000
```

#### 5. Используйте скрипт диагностики

```bash
# Скопируйте check_server.sh на сервер
scp check_server.sh user@130.49.153.154:~/ollama-api/

# На сервере:
chmod +x check_server.sh
./check_server.sh
```

### Ollama не отвечает

```bash
# Проверьте, запущен ли Ollama
ps aux | grep ollama

# Проверьте порт
netstat -tulpn | grep 11434

# Проверьте доступность
curl http://localhost:11434/api/tags

# Перезапустите Ollama
sudo systemctl restart ollama
# или
pkill ollama
OLLAMA_HOST=0.0.0.0 ollama serve
```

### API сервер не отвечает

```bash
# Проверьте, запущен ли сервер
ps aux | grep gunicorn

# Проверьте порт
netstat -tulpn | grep 8000

# Проверьте логи
journalctl -u ollama-api -n 50
# или если запущен вручную, смотрите вывод в терминале

# Проверьте доступность локально
curl http://localhost:8000/health
```

### Быстрый запуск с проверками

Используйте скрипт `start_ollama_api.sh`:

```bash
# Скопируйте на сервер
scp start_ollama_api.sh user@130.49.153.154:~/ollama-api/

# На сервере:
cd ~/ollama-api
chmod +x start_ollama_api.sh
./start_ollama_api.sh
```

### Модель не найдена

```bash
# Проверьте установленные модели
ollama list

# Если модели нет, установите
ollama pull tinyllama
```

## Безопасность

⚠️ **Важно**: Этот сервер открыт для всех. Для production используйте:

1. **HTTPS** с reverse proxy (nginx)
2. **Аутентификацию** (API ключи)
3. **Rate limiting**
4. **Firewall** правила

Пример настройки nginx:

```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```
