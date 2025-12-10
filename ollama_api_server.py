#!/usr/bin/env python3
"""
Flask API Server для проксирования запросов к локальному Ollama
Этот сервер будет работать на удаленной машине и обрабатывать запросы от Android приложения
"""

from flask import Flask, request, jsonify, Response
import requests
import json
import logging
from typing import Optional

# Настройка логирования
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = Flask(__name__)

# URL локального Ollama сервера
OLLAMA_BASE_URL = "http://localhost:11434"

# Модели по умолчанию
# Используем tinyllama на удаленном сервере
DEFAULT_CHAT_MODEL = "tinyllama"
DEFAULT_EMBEDDING_MODEL = "nomic-embed-text"


@app.route('/health', methods=['GET'])
def health_check():
    """Проверка состояния сервера"""
    try:
        # Проверяем доступность Ollama
        response = requests.get(f"{OLLAMA_BASE_URL}/api/tags", timeout=5)
        ollama_available = response.status_code == 200

        return jsonify({
            "status": "ok",
            "ollama_available": ollama_available,
            "ollama_url": OLLAMA_BASE_URL
        }), 200
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        return jsonify({
            "status": "error",
            "ollama_available": False,
            "error": str(e)
        }), 503


@app.route('/api/tags', methods=['GET'])
def get_tags():
    """Получить список доступных моделей"""
    try:
        logger.info("Fetching available models from Ollama")
        response = requests.get(f"{OLLAMA_BASE_URL}/api/tags", timeout=10)
        response.raise_for_status()

        return Response(
            response.content,
            status=response.status_code,
            content_type='application/json'
        )
    except requests.RequestException as e:
        logger.error(f"Error fetching models: {e}")
        return jsonify({"error": str(e)}), 500


@app.route('/api/chat', methods=['POST'])
def chat():
    """
    Проксирование запросов к Ollama chat API
    Принимает тот же формат, что и Ollama API
    """
    try:
        # Получаем данные от клиента
        data = request.get_json()

        if not data:
            return jsonify({"error": "No JSON data provided"}), 400

        # Логируем запрос
        model = data.get('model', DEFAULT_CHAT_MODEL)
        messages = data.get('messages', [])
        stream = data.get('stream', False)

        logger.info(f"Chat request: model={model}, messages_count={len(messages)}, stream={stream}")

        # Отправляем запрос к Ollama
        response = requests.post(
            f"{OLLAMA_BASE_URL}/api/chat",
            json=data,
            timeout=300,  # 5 минут таймаут для LLM
            stream=stream
        )

        response.raise_for_status()

        # Если stream=true, возвращаем потоковый ответ
        if stream:
            def generate():
                for chunk in response.iter_content(chunk_size=8192):
                    if chunk:
                        yield chunk

            return Response(
                generate(),
                status=response.status_code,
                content_type='application/x-ndjson'
            )
        else:
            # Для не-потокового режима возвращаем весь ответ
            return Response(
                response.content,
                status=response.status_code,
                content_type='application/x-ndjson'
            )

    except requests.Timeout:
        logger.error("Ollama request timeout")
        return jsonify({"error": "Request to Ollama timed out"}), 504
    except requests.RequestException as e:
        logger.error(f"Error in chat request: {e}")
        return jsonify({"error": str(e)}), 500
    except Exception as e:
        logger.error(f"Unexpected error in chat: {e}")
        return jsonify({"error": str(e)}), 500


@app.route('/api/embeddings', methods=['POST'])
def embeddings():
    """
    Проксирование запросов к Ollama embeddings API
    """
    try:
        data = request.get_json()

        if not data:
            return jsonify({"error": "No JSON data provided"}), 400

        # Логируем запрос
        model = data.get('model', DEFAULT_EMBEDDING_MODEL)
        prompt = data.get('prompt', '')

        logger.info(f"Embeddings request: model={model}, prompt_length={len(prompt)}")

        # Отправляем запрос к Ollama
        response = requests.post(
            f"{OLLAMA_BASE_URL}/api/embeddings",
            json=data,
            timeout=60
        )

        response.raise_for_status()

        return Response(
            response.content,
            status=response.status_code,
            content_type='application/json'
        )

    except requests.Timeout:
        logger.error("Ollama embeddings request timeout")
        return jsonify({"error": "Request to Ollama timed out"}), 504
    except requests.RequestException as e:
        logger.error(f"Error in embeddings request: {e}")
        return jsonify({"error": str(e)}), 500
    except Exception as e:
        logger.error(f"Unexpected error in embeddings: {e}")
        return jsonify({"error": str(e)}), 500


@app.route('/api/generate', methods=['POST'])
def generate():
    """
    Проксирование запросов к Ollama generate API
    """
    try:
        data = request.get_json()

        if not data:
            return jsonify({"error": "No JSON data provided"}), 400

        model = data.get('model', DEFAULT_CHAT_MODEL)
        prompt = data.get('prompt', '')
        stream = data.get('stream', False)

        logger.info(f"Generate request: model={model}, prompt_length={len(prompt)}, stream={stream}")

        response = requests.post(
            f"{OLLAMA_BASE_URL}/api/generate",
            json=data,
            timeout=300,
            stream=stream
        )

        response.raise_for_status()

        if stream:
            def generate():
                for chunk in response.iter_content(chunk_size=8192):
                    if chunk:
                        yield chunk

            return Response(
                generate(),
                status=response.status_code,
                content_type='application/x-ndjson'
            )
        else:
            return Response(
                response.content,
                status=response.status_code,
                content_type='application/x-ndjson'
            )

    except requests.Timeout:
        logger.error("Ollama generate request timeout")
        return jsonify({"error": "Request to Ollama timed out"}), 504
    except requests.RequestException as e:
        logger.error(f"Error in generate request: {e}")
        return jsonify({"error": str(e)}), 500
    except Exception as e:
        logger.error(f"Unexpected error in generate: {e}")
        return jsonify({"error": str(e)}), 500


@app.errorhandler(404)
def not_found(error):
    return jsonify({"error": "Endpoint not found"}), 404


@app.errorhandler(500)
def internal_error(error):
    logger.error(f"Internal server error: {error}")
    return jsonify({"error": "Internal server error"}), 500


if __name__ == '__main__':
    # Запускаем сервер на всех интерфейсах
    # В production используйте gunicorn или uwsgi
    app.run(
        host='0.0.0.0',
        port=8000,
        debug=False,
        threaded=True
    )
