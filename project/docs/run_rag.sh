#!/bin/bash
# Wrapper для запуска RAG индексатора с venv

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
VENV_PYTHON="$SCRIPT_DIR/venv/bin/python"

# Если venv существует, используем его
if [ -f "$VENV_PYTHON" ]; then
    "$VENV_PYTHON" "$SCRIPT_DIR/rag_indexer.py" "$@"
else
    # Иначе используем системный Python (может не работать без requests)
    python3 "$SCRIPT_DIR/rag_indexer.py" "$@"
fi
