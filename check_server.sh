#!/bin/bash
# Скрипт для проверки состояния сервера

echo "=== Проверка состояния Ollama API сервера ==="
echo ""

# Проверка процесса gunicorn
echo "1. Проверка процесса gunicorn:"
if pgrep -f "gunicorn.*ollama_api_server" > /dev/null; then
    echo "   ✓ Gunicorn запущен"
    ps aux | grep gunicorn | grep -v grep
else
    echo "   ✗ Gunicorn не запущен"
fi
echo ""

# Проверка порта 8000
echo "2. Проверка порта 8000:"
if netstat -tuln | grep -q ":8000"; then
    echo "   ✓ Порт 8000 открыт"
    netstat -tuln | grep ":8000"
else
    echo "   ✗ Порт 8000 не слушается"
fi
echo ""

# Проверка Ollama
echo "3. Проверка Ollama:"
if pgrep ollama > /dev/null; then
    echo "   ✓ Ollama запущен"
    ps aux | grep ollama | grep -v grep | head -1
else
    echo "   ✗ Ollama не запущен"
fi
echo ""

# Проверка порта Ollama (11434)
echo "4. Проверка порта Ollama (11434):"
if netstat -tuln | grep -q ":11434"; then
    echo "   ✓ Порт 11434 открыт"
    netstat -tuln | grep ":11434"
else
    echo "   ✗ Порт 11434 не слушается"
fi
echo ""

# Проверка доступности Ollama API
echo "5. Проверка Ollama API:"
if curl -s http://localhost:11434/api/tags > /dev/null; then
    echo "   ✓ Ollama API доступен"
    curl -s http://localhost:11434/api/tags | head -20
else
    echo "   ✗ Ollama API недоступен"
fi
echo ""

# Проверка доступности Flask API
echo "6. Проверка Flask API:"
if curl -s http://localhost:8000/health > /dev/null; then
    echo "   ✓ Flask API доступен"
    curl -s http://localhost:8000/health
else
    echo "   ✗ Flask API недоступен"
fi
echo ""

# Проверка firewall
echo "7. Проверка firewall (iptables):"
if command -v iptables > /dev/null; then
    iptables -L -n | grep -E "8000|11434" || echo "   Правила для портов 8000/11434 не найдены"
fi
echo ""

# Проверка firewall (ufw)
echo "8. Проверка firewall (ufw):"
if command -v ufw > /dev/null; then
    ufw status | grep -E "8000|11434" || echo "   UFW не настроен для портов 8000/11434"
fi
echo ""

echo "=== Рекомендации ==="
echo ""
echo "Если сервер не запущен:"
echo "  cd ~/ollama-api"
echo "  source venv/bin/activate"
echo "  ./run_server.sh"
echo ""
echo "Если Ollama не запущен:"
echo "  OLLAMA_HOST=0.0.0.0 ollama serve"
echo ""
echo "Если порт закрыт в firewall:"
echo "  sudo ufw allow 8000/tcp"
echo "  sudo ufw allow 11434/tcp"

