import os
import sys
import time
import logging
from qdrant_client import QdrantClient
from qdrant_client.http.models import Distance, VectorParams
from sentence_transformers import SentenceTransformer

# -------------------------------------------------------------------------
# Environment Variables
# -------------------------------------------------------------------------
INDEXER_ENABLED = os.getenv("INDEXER_ENABLED", "true").lower() in ("true", "1", "t")
QDRANT_URL = os.getenv("QDRANT_URL", "http://localhost:6333")
COLLECTION_NAME = os.getenv("QDRANT_COLLECTION_NAME", "prompts")
RECREATE_COLLECTION = os.getenv("RECREATE_COLLECTION", "False").lower() in ("true", "1", "t")
BATCH_SIZE = int(os.getenv("BATCH_SIZE", 16))
MODEL_PATH = os.getenv("EMBEDDING_MODEL_PATH")

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    handlers=[logging.StreamHandler(sys.stdout)]
)

# -------------------------------------------------------------------------
# Define Starter Prompts
# -------------------------------------------------------------------------
STARTER_PROMPTS = [
    {
        "item_path": "architect_prompts/basic",
        "chunk_id": "architect_prompt_1",
        "content": "You are an expert software architect.",
        "area": "Architect"
    },
    {
        "item_path": "architect_prompts/readme",
        "chunk_id": "architect_prompt_2",
        "content": "You are an expert software architect. Create a README.md based on the following code and documentation",
        "area": "Architect - Readme"
    },
    {
        "item_path": "architect_prompts/diagram",
        "chunk_id": "architect_prompt_3",
        "content": "You are an expert software architect. Create a mermaidjs diagram based on the following code and documentation",
        "area": "Architect - Diagram"
    },
    {
        "item_path": "architect_prompts/overview",
        "chunk_id": "architect_prompt_4",
        "content": "You are an expert software architect. Create a extensive and complete explanation of the system based on the following code and documentation",
        "area": "Architect - Docs"
    },
    {
        "item_path": "developer/add_feature",
        "chunk_id": "developer_prompt_1",
        "content": "You are a expert senior software developer which emphasizes readable and testable code. Based on the code below, show how to implement <description of feature>",
        "area": "Developer - Add feature"
    },
    {
        "item_path": "developer/refactor_code",
        "chunk_id": "developer_prompt_2",
        "content": "You are a expert senior software developer which emphasizes readable and testable code. Refactor the code below.",
        "area": "Developer - Refactor"
    },
    {
        "item_path": "security/analyst",
        "chunk_id": "security_prompt_1",
        "content": "You are a expert senior software penetration tester skilled in writing secure software. Analyze the code below for vulnerabilities and how to fix them.",
        "area": "Security - Find vulnerabilities"
    }
]

# -------------------------------------------------------------------------
# Qdrant Upsert Function
# -------------------------------------------------------------------------
def batch_upsert(client, collection_name, vectors, payloads, batch_size):
    """Upserts points into Qdrant in batches."""
    total_points = len(vectors)
    for start in range(0, total_points, batch_size):
        end = min(start + batch_size, total_points)
        batch_vectors = vectors[start:end]
        batch_payloads = payloads[start:end]

        logging.info(f"[batch_upsert] Upserting {len(batch_vectors)} vectors to '{collection_name}'...")
        client.upsert(
            collection_name=collection_name,
            points=[
                {
                    "id": start + i,
                    "vector": vec,
                    "payload": payload
                }
                for i, (vec, payload) in enumerate(zip(batch_vectors, batch_payloads))
            ]
        )
        logging.info(f"[batch_upsert] Successfully upserted {len(batch_vectors)} vectors.")

# -------------------------------------------------------------------------
# Main Function
# -------------------------------------------------------------------------
def main():
    if not INDEXER_ENABLED:
        logging.info("Indexer is disabled via INDEXER_ENABLED environment variable. Exiting.")
        sys.exit(0)

    logging.info(f"[main] Indexing Starter Prompts to Qdrant...")
    logging.info(f"[main] QDRANT_URL={QDRANT_URL}")
    logging.info(f"[main] COLLECTION_NAME={COLLECTION_NAME}")
    logging.info(f"[main] RECREATE_COLLECTION={RECREATE_COLLECTION}")

    # 1) Connect to Qdrant
    client = QdrantClient(url=QDRANT_URL)

    # 2) Load Embedding Model
    logging.info("[main] Loading sentence-transformers/all-MiniLM-L6-v2 model...")
    model = SentenceTransformer(MODEL_PATH)
    logging.info("[main] Model loaded successfully.")

    # 3) Create or Reset Collection
    dimension = model.get_sentence_embedding_dimension()
    if RECREATE_COLLECTION:
        logging.info(f"[main] Recreating collection '{COLLECTION_NAME}'...")
        client.recreate_collection(
            collection_name=COLLECTION_NAME,
            vectors_config=VectorParams(size=dimension, distance=Distance.COSINE),
        )
    else:
        collections = [col.name for col in client.get_collections().collections]
        if COLLECTION_NAME not in collections:
            logging.info(f"[main] Creating collection '{COLLECTION_NAME}'...")
            client.create_collection(
                collection_name=COLLECTION_NAME,
                vectors_config=VectorParams(size=dimension, distance=Distance.COSINE),
            )

    # 4) Convert Prompts into Vectors
    logging.info("[main] Generating embeddings for starter prompts...")
    vectors = model.encode([p["content"] for p in STARTER_PROMPTS])

    # 5) Prepare Payloads
    payloads = [{**p} for p in STARTER_PROMPTS]

    # 6) Upsert Data into Qdrant
    batch_upsert(client, COLLECTION_NAME, vectors, payloads, batch_size=BATCH_SIZE)

    logging.info("[main] Starter Prompts indexing complete!")

if __name__ == "__main__":
    main()
