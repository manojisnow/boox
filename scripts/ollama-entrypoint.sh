#!/bin/sh

MODEL_NAME=llama2
MODEL_PATH="/root/.ollama/models/${MODEL_NAME}"

# Check if the model directory exists and is not empty
if [ ! -d "$MODEL_PATH" ] || [ -z "$(ls -A $MODEL_PATH 2>/dev/null)" ]; then
  echo "Model $MODEL_NAME not found, pulling..."
  ollama pull $MODEL_NAME
else
  echo "Model $MODEL_NAME already present. Skipping pull."
fi

exec ollama serve

