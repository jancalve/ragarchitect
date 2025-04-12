# embedding_service.py
import os
from flask import Flask, request, jsonify
from sentence_transformers import SentenceTransformer

app = Flask(__name__)
MODEL_PATH = os.getenv("EMBEDDING_MODEL_PATH")
model = SentenceTransformer(MODEL_PATH)


@app.route('/embed', methods=['POST'])
def embed():
    data = request.get_json()
    sentences = data.get('sentences', [])
    if not sentences:
        return jsonify({'error': 'No sentences provided'}), 400
    embeddings = model.encode(sentences).tolist()
    return jsonify({'embeddings': embeddings})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080)
