#!/bin/bash
huggingface-cli download sentence-transformers/all-MiniLM-L6-v2 --local-dir "$(pwd)/models/all-MiniLM-L6-v2"
#huggingface-cli download mistralai/Mistral-7B-Instruct-v0.1 --local-dir "$(pwd)/app/models/Mistral-7B"

# Run Ollama container with volume mount to persist models
docker run -d \
  --name ollama-download \
  -p 11434:11434 \
   --mount type=bind,source="$(pwd)/app/models",target=/root/.ollama/models \
  ollama/ollama

echo "Saving to $(pwd)/models/"

# Wait for the container to become ready
echo "Waiting for Ollama to become ready..."
until curl -s http://localhost:11434/api/tags >/dev/null; do
  sleep 1
done

# Download the model (e.g., llama3)
MODEL_NAME="llama3.2:3b"
echo "Downloading model: $MODEL_NAME"
curl -s http://localhost:11434/api/pull -d "{\"name\":\"$MODEL_NAME\"}"

docker rm -f ollama-download