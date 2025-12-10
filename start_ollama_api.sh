#!/bin/bash
# Скрипт для запуска Ollama API сервера с проверками

echo "=== Запуск Ollama API сервера ==="
echo ""

# Проверка директории
if [ ! -f "ollama_api_server.py" ]; then
    echo "Ошибка: файл ollama_api_server.py не найден"
    echo "Убедитесь, что вы находитесь в директории ~/ollama-api"
    exit 1
fi

# Проверка виртуального окружения
if [ ! -d "venv" ]; then
    echo "Создание виртуального окружения..."
    python3 -m venv venv
fi

# Активация виртуального окружения
echo "Активация виртуального окружения..."
source venv/bin/activate

# Установка зависимостей (если нужно)
if [ ! -f "venv/.deps_installed" ]; then
    echo "Установка зависимостей..."
    pip install -r requirements.txt
    touch venv/.deps_installed
fi

# Проверка Ollama
echo "Проверка Ollama..."
if ! curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo "⚠️  Внимание: Ollama не отвечает на localhost:11434"
    echo "   Убедитесь, что Ollama запущен:"
    echo "   OLLAMA_HOST=0.0.0.0 ollama serve"
    echo ""
    echo "Продолжаем запуск сервера (Ollama может быть недоступен временно)..."
else
    echo "✓ Ollama доступен"
fi

# Проверка порта 8000
if netstat -tuln 2>/dev/null | grep -q ":8000" || ss -tuln 2>/dev/null | grep -q ":8000"; then
    echo "⚠️  Внимание: Порт 8000 уже занят"
    echo "   Остановите другой процесс или измените порт"
    exit 1
fi

# Запуск сервера
echo ""
echo "Запуск Flask API сервера на порту 8000..."
echo "Для остановки нажмите Ctrl+C"
echo ""

# Делаем скрипт исполняемым
chmod +x run_server.sh

# Запуск через gunicorn
exec ./run_server.sh

