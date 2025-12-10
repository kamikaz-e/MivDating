#!/bin/bash
# Скрипт для запуска Flask API сервера с помощью gunicorn

# Количество воркеров (обычно 2-4 * количество CPU)
WORKERS=4

# Таймаут для запросов (в секундах) - для LLM нужен большой таймаут
TIMEOUT=600

# Порт
PORT=8000

# Запуск через gunicorn для production
gunicorn \
    --bind 0.0.0.0:8000 \
    --workers $WORKERS \
    --timeout $TIMEOUT \
    --access-logfile - \
    --error-logfile - \
    --log-level info \
    ollama_api_server:app
