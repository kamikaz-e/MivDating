# Быстрый запуск сервера - Решение проблемы подключения

## Проблема: "Failed to connect to server"

Если вы получаете ошибку `curl: (7) Failed to connect to 130.49.153.154 port 8000`, выполните следующие шаги:

## Шаг 1: Проверка на сервере

Подключитесь к серверу:
```bash
ssh root@130.49.153.154
```

## Шаг 2: Проверка текущего состояния

```bash
cd ~/ollama-api

# Проверьте, запущен ли сервер
ps aux | grep gunicorn

# Проверьте порт
netstat -tuln | grep 8000
# или
ss -tuln | grep 8000
```

## Шаг 3: Запуск сервера

### Вариант A: Используйте скрипт с проверками (рекомендуется)

```bash
cd ~/ollama-api
chmod +x start_ollama_api.sh
./start_ollama_api.sh
```

### Вариант B: Ручной запуск

```bash
cd ~/ollama-api

# 1. Активируйте виртуальное окружение
source venv/bin/activate

# 2. Убедитесь, что зависимости установлены
pip install -r requirements.txt

# 3. Запустите сервер
chmod +x run_server.sh
./run_server.sh
```

## Шаг 4: Проверка firewall

```bash
# Проверьте статус UFW
sudo ufw status

# Если UFW активен, откройте порт
sudo ufw allow 8000/tcp
sudo ufw reload

# Или для iptables
sudo iptables -A INPUT -p tcp --dport 8000 -j ACCEPT
sudo iptables-save
```

## Шаг 5: Проверка Ollama

Убедитесь, что Ollama запущен:

```bash
# Проверьте процесс
ps aux | grep ollama

# Проверьте доступность
curl http://localhost:11434/api/tags

# Если не работает, запустите:
OLLAMA_HOST=0.0.0.0 ollama serve
```

## Шаг 6: Тестирование

### На сервере (локально):
```bash
curl http://localhost:8000/health
```

### С вашего компьютера:
```bash
curl http://130.49.153.154:8000/health
```

## Шаг 7: Запуск в фоне (если нужно)

Если хотите запустить сервер в фоне:

```bash
cd ~/ollama-api
source venv/bin/activate
nohup ./run_server.sh > server.log 2>&1 &
```

Проверка логов:
```bash
tail -f server.log
```

## Шаг 8: Автозапуск через systemd (опционально)

Создайте файл `/etc/systemd/system/ollama-api.service`:

```ini
[Unit]
Description=Ollama API Server
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/root/ollama-api
Environment="PATH=/root/ollama-api/venv/bin"
ExecStart=/root/ollama-api/venv/bin/gunicorn \
    --bind 0.0.0.0:8000 \
    --workers 4 \
    --timeout 600 \
    --access-logfile - \
    --error-logfile - \
    --log-level info \
    ollama_api_server:app
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Запуск:
```bash
sudo systemctl daemon-reload
sudo systemctl enable ollama-api
sudo systemctl start ollama-api
sudo systemctl status ollama-api
```

## Диагностика проблем

### Сервер не запускается

1. Проверьте логи:
```bash
cd ~/ollama-api
source venv/bin/activate
python3 ollama_api_server.py
```

2. Проверьте зависимости:
```bash
pip list | grep -E "flask|gunicorn|requests"
```

### Порт занят

```bash
# Найдите процесс, использующий порт 8000
lsof -i :8000
# или
netstat -tulpn | grep 8000

# Остановите процесс
kill -9 <PID>
```

### Ollama недоступен

```bash
# Проверьте, запущен ли Ollama
systemctl status ollama
# или
ps aux | grep ollama

# Запустите Ollama
OLLAMA_HOST=0.0.0.0 ollama serve
```

## Быстрая проверка всех компонентов

Используйте скрипт диагностики:

```bash
cd ~/ollama-api
chmod +x check_server.sh
./check_server.sh
```

Этот скрипт проверит:
- Запущен ли gunicorn
- Открыт ли порт 8000
- Запущен ли Ollama
- Открыт ли порт 11434
- Доступность API endpoints
- Настройки firewall

