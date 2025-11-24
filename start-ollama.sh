#!/bin/bash

# Остановить текущий процесс Ollama
pkill ollama

# Запустить Ollama с возможностью принимать соединения от эмулятора Android
export OLLAMA_HOST=0.0.0.0:11434
ollama serve
