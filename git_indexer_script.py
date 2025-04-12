import os
import subprocess
import glob
import time
import logging
import sys
import shutil  # Needed for directory removal

from qdrant_client import QdrantClient
from qdrant_client.http.models import Distance, VectorParams
from sentence_transformers import SentenceTransformer

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout)
    ]
)

# -------------------------------------------------------------------------
# Environment Variables
# -------------------------------------------------------------------------
INDEXER_ENABLED = os.getenv("INDEXER_ENABLED", "true").lower() in ("true", "1", "t")
QDRANT_URL = os.getenv("QDRANT_URL", "")
GIT_REPO_URL = os.getenv("GIT_REPO_URL", "")
GIT_TOKEN = os.getenv("GIT_TOKEN", "")
PROJECT_NAME = os.getenv("PROJECT_NAME", "default_project")
MODEL_PATH = os.getenv("EMBEDDING_MODEL_PATH")

# Where to clone the repo locally
CLONE_DIR = "/tmp/repo"

# Qdrant-related
COLLECTION_NAME = "code"
CHUNK_SIZE = int(os.getenv("CHUNK_SIZE", "2000"))  # number of lines per chunk
BATCH_SIZE = int(os.getenv("BATCH_SIZE", "500"))
RECREATE_COLLECTION = os.getenv("RECREATE_COLLECTION", "False").lower() in ("true", "1", "t")

# File extensions to process, read from env. e.g. "java,md,bpmn,xml"
FILE_EXTENSIONS = os.getenv("FILE_EXTENSIONS", "java,md,bpmn,xml,py,yml")
FILE_EXTENSIONS_LIST = [("." + ext.strip().lower()) for ext in FILE_EXTENSIONS.split(",") if ext.strip()]

# New: environment variable for ignoring certain paths, e.g. IGNORE_PATHS=src/test,docs/old
IGNORE_PATHS = os.getenv("IGNORE_PATHS", "")
IGNORE_PATHS_LIST = [p.strip() for p in IGNORE_PATHS.split(",") if p.strip()]


# -------------------------------------------------------------------------
# Git Cloning
# -------------------------------------------------------------------------
def clone_repo():
    """
    Clones the repo into CLONE_DIR.

    If RECREATE_COLLECTION=True, remove the CLONE_DIR (if it exists) to ensure a fresh clone.
    Otherwise, if the directory already exists and is non-empty, we skip cloning.
    """
    if RECREATE_COLLECTION and os.path.exists(CLONE_DIR) and os.path.isdir(CLONE_DIR):
        logging.info(f"[clone_repo] RECREATE_COLLECTION=True -> removing old directory: {CLONE_DIR}")
        shutil.rmtree(CLONE_DIR)

    # If the directory still exists and is not empty, skip
    if os.path.exists(CLONE_DIR) and os.path.isdir(CLONE_DIR):
        if os.listdir(CLONE_DIR):  # This means it is not empty
            logging.info(f"[clone_repo] Directory '{CLONE_DIR}' is not empty. "
                         "It looks like we've already cloned this repo previously. Stopping.")
            sys.exit(0)

    if not GIT_REPO_URL:
        logging.info("[clone_repo] No GIT_REPO_URL provided. Skipping clone.")
        return

    repo_url = GIT_REPO_URL
    if GIT_TOKEN:
        logging.info("[clone_repo] Private repo token detected, modifying repo URL.")
        protocol_prefix = "https://"
        if repo_url.startswith("http://"):
            protocol_prefix = "http://"
        repo_url = protocol_prefix + f"{GIT_TOKEN}@" + repo_url[len(protocol_prefix):]

    logging.info(f"[clone_repo] Cloning repository from {repo_url} into {CLONE_DIR}...")
    try:
        subprocess.run(["git", "clone", repo_url, CLONE_DIR], check=True)
        logging.info("[clone_repo] Clone completed successfully.")
    except subprocess.CalledProcessError as e:
        logging.error(f"[clone_repo] ERROR during cloning: {e}")
        raise


# -------------------------------------------------------------------------
# File Processing
# -------------------------------------------------------------------------
def split_large_code(file_content, max_chunk_size):
    """
    Splits a large file into smaller chunks of `max_chunk_size` lines each.
    If the file is shorter than `max_chunk_size`, return it as a single chunk.
    We preserve the line structure by splitting on newline, then rejoining
    for each chunk with `\n`.
    """
    lines = file_content.split('\n')
    num_lines = len(lines)

    if num_lines <= max_chunk_size:
        return [file_content]

    chunks = []
    start = 0
    while start < num_lines:
        end = min(start + max_chunk_size, num_lines)
        chunk = "\n".join(lines[start:end])
        chunks.append(chunk)
        start = end

    return chunks

def gather_files_to_process():
    """
    Gathers all files matching the specified extensions in FILE_EXTENSIONS_LIST
    under the CLONE_DIR, excluding any that match IGNORE_PATHS_LIST.
    Returns a list of absolute paths.
    """
    logging.info(f"[gather_files_to_process] Searching for file extensions: {FILE_EXTENSIONS_LIST}")

    all_files = []
    for ext in FILE_EXTENSIONS_LIST:
        pattern = os.path.join(CLONE_DIR, "**", f"*{ext}")
        matched = glob.glob(pattern, recursive=True)
        all_files.extend(matched)

    # Filter out files whose path contains any segment in IGNORE_PATHS_LIST
    if IGNORE_PATHS_LIST:
        before_count = len(all_files)

        def should_ignore(path):
            return any(ignore_seg in path for ignore_seg in IGNORE_PATHS_LIST)

        all_files = [f for f in all_files if not should_ignore(f)]
        after_count = len(all_files)
        logging.info(
            f"[gather_files_to_process] Ignored {before_count - after_count} file(s) "
            f"due to IGNORE_PATHS_LIST={IGNORE_PATHS_LIST}"
        )

    return all_files


def chunk_files():
    """
    Walk through files with the configured extensions, create one or more chunks per file
    based on CHUNK_SIZE (line-based).
    """
    code_chunks = []
    files_processed = 0

    all_files = gather_files_to_process()
    logging.info(f"[chunk_files] Found {len(all_files)} files to process.")

    for fpath in all_files:
        files_processed += 1
        with open(fpath, "r", encoding="utf-8", errors="replace") as f:
            code = f.read()

        logging.info(f"[chunk_files] Processing file #{files_processed}: {fpath}")

        # Split the file into chunks if it's large
        file_chunks = split_large_code(code, CHUNK_SIZE)

        # Strip "/tmp/repo/" prefix if present, for a cleaner item_path
        display_path = fpath
        prefix = "/tmp/repo/"
        if display_path.startswith(prefix):
            display_path = display_path[len(prefix):]

        # Create chunk objects
        filename = os.path.basename(fpath)

        for idx, chunk in enumerate(file_chunks):
            chunk_id = f"{filename}_chunk_{idx}"
            code_chunks.append({
                "item_path": display_path,
                "chunk_id": chunk_id,
                "content": chunk,
                "area": PROJECT_NAME
            })
            logging.info(f"[chunk_files]  -> Created chunk {chunk_id}")

    logging.info(f"[chunk_files] Total chunks created: {len(code_chunks)}")
    return code_chunks


# -------------------------------------------------------------------------
# Qdrant Upsert
# -------------------------------------------------------------------------
def batch_upsert(client, collection_name, points, batch_size):
    """Upserts points into Qdrant in batches."""
    total_points = len(points)
    for start in range(0, total_points, batch_size):
        end = min(start + batch_size, total_points)
        batch = points[start:end]
        logging.info(f"[batch_upsert] Upserting points {start} to {end} (Batch size: {len(batch)})...")
        try:
            client.upsert(collection_name=collection_name, points=batch)
            logging.info(f"[batch_upsert] Successfully upserted points {start} to {end}.")
        except Exception as e:
            logging.error(f"[batch_upsert] ERROR upserting points {start} to {end}: {e}")
            # Optionally implement retry logic or skip to next batch


# -------------------------------------------------------------------------
# Main
# -------------------------------------------------------------------------
def main():
    if not INDEXER_ENABLED:
        logging.info("Indexer is disabled via INDEXER_ENABLED environment variable. Exiting.")
        sys.exit(0)

    logging.info(f"[main] Indexer starting... QDRANT_URL={QDRANT_URL}")
    logging.info(f"[main] GIT_REPO_URL={GIT_REPO_URL}")
    logging.info(f"[main] GIT_TOKEN={'<REDACTED>' if GIT_TOKEN else '(None)'}")
    logging.info(f"[main] COLLECTION_NAME={COLLECTION_NAME}")
    logging.info(f"[main] CHUNK_SIZE={CHUNK_SIZE}")
    logging.info(f"[main] BATCH_SIZE={BATCH_SIZE}")
    logging.info(f"[main] RECREATE_COLLECTION={RECREATE_COLLECTION}")
    logging.info(f"[main] PROJECT_NAME={PROJECT_NAME}")
    logging.info(f"[main] FILE_EXTENSIONS_LIST={FILE_EXTENSIONS_LIST}")
    logging.info(f"[main] IGNORE_PATHS_LIST={IGNORE_PATHS_LIST}")

    # 1) Clone the repo (may remove the old CLONE_DIR if RECREATE_COLLECTION=True)
    start_time = time.time()
    clone_repo()
    logging.info(f"[main] Clone step took {time.time() - start_time:.2f} seconds.")

    # 2) Chunk
    start_time = time.time()
    chunks = chunk_files()
    logging.info(f"[main] Created {len(chunks)} total chunks.")
    logging.info(f"[main] Chunking step took {time.time() - start_time:.2f} seconds.")

    # 3) Connect to Qdrant
    start_time = time.time()
    logging.info(f"[main] Connecting to Qdrant at {QDRANT_URL}...")
    client = QdrantClient(url=QDRANT_URL)
    logging.info("[main] QdrantClient initialized successfully.")
    logging.info(f"[main] Qdrant connection took {time.time() - start_time:.2f} seconds.")

    # 4) Create or verify collection
    start_time = time.time()
    logging.info(f"[main] Checking if collection '{COLLECTION_NAME}' exists.")

    try:
        existing_collections = client.get_collections().collections
        collection_exists = any(col.name == COLLECTION_NAME for col in existing_collections)
    except Exception as e:
        logging.error(f"[main] ERROR fetching collections: {e}")
        raise

    if collection_exists:
        logging.info(f"[main] Collection '{COLLECTION_NAME}' already exists.")
        if RECREATE_COLLECTION:
            logging.info(f"[main] RECREATE_COLLECTION is True. Deleting and recreating the collection.")
            try:
                client.delete_collection(COLLECTION_NAME)
                logging.info(f"[main] Collection '{COLLECTION_NAME}' deleted successfully.")
            except Exception as e:
                logging.error(f"[main] ERROR deleting collection '{COLLECTION_NAME}': {e}")
                raise
            # Recreate the collection
            try:
                client.create_collection(
                    collection_name=COLLECTION_NAME,
                    vectors_config=VectorParams(size=384, distance=Distance.COSINE)
                )
                logging.info(f"[main] Collection '{COLLECTION_NAME}' recreated successfully.")
            except Exception as e:
                logging.error(f"[main] ERROR creating collection '{COLLECTION_NAME}': {e}")
                raise
        else:
            logging.info(f"[main] RECREATE_COLLECTION is False. Using the existing collection.")
    else:
        logging.info(f"[main] Collection '{COLLECTION_NAME}' does not exist. Creating it.")
        try:
            client.create_collection(
                collection_name=COLLECTION_NAME,
                vectors_config=VectorParams(size=384, distance=Distance.COSINE)
            )
            logging.info(f"[main] Collection '{COLLECTION_NAME}' created successfully.")
        except Exception as e:
            logging.error(f"[main] ERROR creating collection '{COLLECTION_NAME}': {e}")
            raise

    logging.info(f"[main] Collection setup step took {time.time() - start_time:.2f} seconds.")

    # 5) Load embedding model
    start_time = time.time()
    logging.info("[main] Loading sentence-transformers model...")
    model = SentenceTransformer(MODEL_PATH)
    logging.info("[main] Model loaded successfully.")
    logging.info(f"[main] Model loading step took {time.time() - start_time:.2f} seconds.")

    # 6) Embed & upsert
    start_time = time.time()
    logging.info(f"[main] Starting embedding + upsert for {len(chunks)} chunks...")

    points = []
    for i, c in enumerate(chunks):
        if i % 50 == 0 and i > 0:
            logging.info(f"[main] Embedded {i} chunks so far...")
        emb = model.encode(c["content"]).tolist()
        points.append({
            "id": i,  # This ID is also unique, used by Qdrant to track the point
            "vector": emb,
            "payload": {
                "item_path": c["item_path"],
                "chunk_id": c["chunk_id"],
                "content": c["content"],
                "area": c["area"]
            }
        })

    logging.info(f"[main] Preparing to upsert {len(points)} chunks into Qdrant.")
    batch_upsert(client, COLLECTION_NAME, points, BATCH_SIZE)
    logging.info(f"[main] All chunks upserted into Qdrant.")
    logging.info(f"[main] Embedding + Upsert step took {time.time() - start_time:.2f} seconds.")


if __name__ == "__main__":
    main()
