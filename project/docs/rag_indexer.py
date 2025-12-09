#!/usr/bin/env python3
"""
RAG индексатор для документации проекта MivDating
Индексирует README.md и файлы из project/docs/ для использования в ассистенте
"""

import os
import json
import requests
from pathlib import Path
from typing import List, Dict, Tuple

# Конфигурация
OLLAMA_URL = "http://localhost:11434"
EMBEDDING_MODEL = "nomic-embed-text"
CHUNK_SIZE = 512
CHUNK_OVERLAP = 128
DOCS_DIR = Path(__file__).parent
PROJECT_ROOT = DOCS_DIR.parent.parent
INDEX_FILE = DOCS_DIR / "rag_index.json"


class DocumentChunk:
    """Чанк документа с метаданными"""

    def __init__(self, content: str, source: str, chunk_index: int):
        self.content = content
        self.source = source
        self.chunk_index = chunk_index
        self.embedding = None

    def to_dict(self) -> dict:
        return {
            "content": self.content,
            "source": self.source,
            "chunk_index": self.chunk_index,
            "embedding": self.embedding
        }


def chunk_text(text: str, chunk_size: int = CHUNK_SIZE, overlap: int = CHUNK_OVERLAP) -> List[str]:
    """
    Разбивает текст на чанки с перекрытием

    Args:
        text: Исходный текст
        chunk_size: Размер чанка в символах
        overlap: Размер перекрытия

    Returns:
        Список чанков
    """
    chunks = []
    start = 0
    text_length = len(text)

    while start < text_length:
        end = start + chunk_size
        chunk = text[start:end]
        chunks.append(chunk)
        start += chunk_size - overlap

    return chunks


def get_embedding(text: str, model: str = EMBEDDING_MODEL) -> List[float]:
    """
    Получает эмбеддинг текста через Ollama API

    Args:
        text: Текст для эмбеддинга
        model: Название модели

    Returns:
        Вектор эмбеддинга
    """
    url = f"{OLLAMA_URL}/api/embeddings"
    payload = {
        "model": model,
        "prompt": text
    }

    try:
        response = requests.post(url, json=payload)
        response.raise_for_status()
        return response.json()["embedding"]
    except Exception as e:
        print(f"Ошибка получения эмбеддинга: {e}")
        return []


def load_documents() -> List[Tuple[str, str]]:
    """
    Загружает документы для индексации

    Returns:
        Список кортежей (имя_файла, содержимое)
    """
    documents = []

    # README.md из корня проекта
    readme_path = PROJECT_ROOT / "README.md"
    if readme_path.exists():
        with open(readme_path, 'r', encoding='utf-8') as f:
            content = f.read()
            documents.append(("README.md", content))
            print(f"✓ Загружен README.md ({len(content)} символов)")

    # Все .md файлы из project/docs/
    for md_file in DOCS_DIR.glob("*.md"):
        with open(md_file, 'r', encoding='utf-8') as f:
            content = f.read()
            documents.append((md_file.name, content))
            print(f"✓ Загружен {md_file.name} ({len(content)} символов)")

    # Дополнительные гайды из корня
    for guide_name in ["RAG_COMPLETE_GUIDE.md", "RAG_FILTERING_GUIDE.md",
                       "TESTING_GUIDE.md", "CHANGES_SUMMARY.md"]:
        guide_path = PROJECT_ROOT / guide_name
        if guide_path.exists():
            with open(guide_path, 'r', encoding='utf-8') as f:
                content = f.read()
                documents.append((guide_name, content))
                print(f"✓ Загружен {guide_name} ({len(content)} символов)")

    return documents


def index_documents() -> List[DocumentChunk]:
    """
    Индексирует все документы проекта

    Returns:
        Список проиндексированных чанков с эмбеддингами
    """
    print("=== Индексация документов проекта ===\n")

    # Загружаем документы
    documents = load_documents()
    print(f"\nВсего загружено документов: {len(documents)}\n")

    # Разбиваем на чанки и создаем эмбеддинги
    all_chunks = []
    total_chunks = 0

    for doc_name, content in documents:
        print(f"Обработка {doc_name}...")

        # Разбиваем на чанки
        text_chunks = chunk_text(content)
        print(f"  Создано чанков: {len(text_chunks)}")

        # Создаем эмбеддинги для каждого чанка
        for i, chunk_content in enumerate(text_chunks):
            chunk = DocumentChunk(chunk_content, doc_name, i)

            # Получаем эмбеддинг
            embedding = get_embedding(chunk_content)
            if embedding:
                chunk.embedding = embedding
                all_chunks.append(chunk)
                total_chunks += 1

                if (i + 1) % 5 == 0:
                    print(f"  Обработано чанков: {i + 1}/{len(text_chunks)}")

        print(f"  ✓ Готово\n")

    print(f"=== Индексация завершена ===")
    print(f"Всего проиндексировано чанков: {total_chunks}\n")

    return all_chunks


def save_index(chunks: List[DocumentChunk]):
    """
    Сохраняет индекс в JSON файл

    Args:
        chunks: Список чанков для сохранения
    """
    index_data = {
        "chunks": [chunk.to_dict() for chunk in chunks],
        "metadata": {
            "total_chunks": len(chunks),
            "chunk_size": CHUNK_SIZE,
            "chunk_overlap": CHUNK_OVERLAP,
            "embedding_model": EMBEDDING_MODEL
        }
    }

    with open(INDEX_FILE, 'w', encoding='utf-8') as f:
        json.dump(index_data, f, ensure_ascii=False, indent=2)

    print(f"✓ Индекс сохранен в {INDEX_FILE}")
    print(f"  Размер файла: {INDEX_FILE.stat().st_size / 1024:.2f} KB")


def cosine_similarity(v1: List[float], v2: List[float]) -> float:
    """
    Вычисляет косинусное сходство между двумя векторами

    Args:
        v1, v2: Векторы для сравнения

    Returns:
        Значение сходства от -1 до 1
    """
    dot_product = sum(a * b for a, b in zip(v1, v2))
    magnitude1 = sum(a * a for a in v1) ** 0.5
    magnitude2 = sum(b * b for b in v2) ** 0.5

    if magnitude1 == 0 or magnitude2 == 0:
        return 0.0

    return dot_product / (magnitude1 * magnitude2)


def search(query: str, top_k: int = 5) -> List[Dict]:
    """
    Выполняет поиск по индексу

    Args:
        query: Поисковый запрос
        top_k: Количество результатов

    Returns:
        Список наиболее релевантных чанков
    """
    # Загружаем индекс
    if not INDEX_FILE.exists():
        print("Ошибка: индекс не найден. Запустите индексацию сначала.")
        return []

    with open(INDEX_FILE, 'r', encoding='utf-8') as f:
        index_data = json.load(f)

    # Получаем эмбеддинг запроса
    print(f"Поиск: '{query}'")
    query_embedding = get_embedding(query)

    if not query_embedding:
        print("Ошибка получения эмбеддинга запроса")
        return []

    # Вычисляем сходство с каждым чанком
    results = []
    for chunk_data in index_data["chunks"]:
        similarity = cosine_similarity(query_embedding, chunk_data["embedding"])
        results.append({
            "content": chunk_data["content"],
            "source": chunk_data["source"],
            "chunk_index": chunk_data["chunk_index"],
            "score": similarity
        })

    # Сортируем по убыванию score
    results.sort(key=lambda x: x["score"], reverse=True)

    return results[:top_k]


def test_search():
    """Тестирует поиск по индексу"""
    print("\n=== Тестирование поиска ===\n")

    test_queries = [
        "Как работает RAG система?",
        "Где находится IndexingService?",
        "Какие параметры у FilterConfig?",
        "Стиль кода для Composable функций"
    ]

    for query in test_queries:
        print(f"\n📝 Запрос: {query}")
        results = search(query, top_k=3)

        for i, result in enumerate(results, 1):
            print(f"\n  [{i}] Score: {result['score']:.3f} | Источник: {result['source']}")
            preview = result['content'][:150].replace('\n', ' ')
            print(f"      {preview}...")


def main():
    """Главная функция"""
    import sys

    if len(sys.argv) > 1:
        command = sys.argv[1]

        if command == "index":
            # Индексация документов
            chunks = index_documents()
            save_index(chunks)

        elif command == "search":
            # Поиск по запросу
            if len(sys.argv) < 3:
                print("Использование: python rag_indexer.py search 'ваш запрос'")
                return

            query = sys.argv[2]
            results = search(query, top_k=5)

            print(f"\nНайдено результатов: {len(results)}\n")
            for i, result in enumerate(results, 1):
                print(f"[{i}] Score: {result['score']:.3f}")
                print(f"    Источник: {result['source']} (чанк {result['chunk_index']})")
                print(f"    {result['content'][:200]}...\n")

        elif command == "test":
            # Тестовый поиск
            test_search()

        else:
            print(f"Неизвестная команда: {command}")
            print("Доступные команды: index, search, test")

    else:
        print("RAG индексатор для документации MivDating")
        print("\nИспользование:")
        print("  python rag_indexer.py index           # Индексировать документы")
        print("  python rag_indexer.py search 'запрос' # Поиск по индексу")
        print("  python rag_indexer.py test            # Тестовый поиск")


if __name__ == "__main__":
    main()
