import os
import sys
import requests
import pandas as pd
from bs4 import BeautifulSoup
from tqdm import tqdm
from dotenv import load_dotenv
import logging
import itertools  # For integer ID generation

# Qdrant / ML imports
from qdrant_client import QdrantClient
from qdrant_client.http.models import Distance, VectorParams
from sentence_transformers import SentenceTransformer

# --------------------------------------------------------------------
# Load environment variables
# --------------------------------------------------------------------
load_dotenv()
INDEXER_ENABLED = os.getenv("INDEXER_ENABLED", "true").lower() in ("true", "1", "t")
CONFLUENCE_DOMAIN = os.getenv("CONFLUENCE_DOMAIN", "https://yoursite.atlassian.net")
CONFLUENCE_SPACE_NAME = os.getenv("CONFLUENCE_SPACE", "YOUR_SPACE_NAME")
CONFLUENCE_TOKEN = os.getenv("CONFLUENCE_TOKEN", "")
CONFLUENCE_USER = os.getenv("CONFLUENCE_USER", "")
CONFLUENCE_SPACE_TYPE = os.getenv("CONFLUENCE_SPACE_TYPE", "global")

# Qdrant-related environment variables
QDRANT_URL = os.getenv("QDRANT_URL", "http://localhost:6333")
COLLECTION_NAME = os.getenv("QDRANT_COLLECTION_NAME", "confluence")
RECREATE_COLLECTION = os.getenv("RECREATE_COLLECTION", "False").lower() in ("true", "1", "t")
MODEL_PATH = os.getenv("EMBEDDING_MODEL_PATH")


# Label(s) to fetch from environment
PAGE_LABELS = os.getenv("PAGE_LABELS", "")

# Optional: define a maximum number of pages to retrieve per label
MAX_PAGES = int(os.getenv("MAX_PAGES", 0)) or None

# Define how many embeddings to upsert in one go
BATCH_SIZE = int(os.getenv("BATCH_SIZE", 16))

# Configure logging
logging.basicConfig(
    level=logging.DEBUG,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)]
)

# --------------------------------------------------------------------
# Confluence API Functions
# --------------------------------------------------------------------

def get_auth_header(email, token):
    credentials = f"{email}:{token}"
    return f"Basic {credentials}"

def api_call(url):
    logging.debug(f"[api_call] Attempting request: {url}")
    headers = {
        "Authorization": get_auth_header(CONFLUENCE_USER, CONFLUENCE_TOKEN)
    }
    try:
        response = requests.get(url, headers=headers)
        logging.debug(f"[api_call] Response status: {response.status_code}")
        if response.status_code == 200:
            return response.json()
        else:
            logging.error(f"[api_call] HTTP {response.status_code} - {response.text}")
    except requests.exceptions.RequestException as e:
        logging.error(f"[api_call] An error occurred: {e}")
    return None

def get_space_id_by_name(space_name):
    """
    Fetches the list of spaces (with paging) and finds the space ID for a given space name.
    """
    url = f"{CONFLUENCE_DOMAIN}/wiki/api/v2/spaces?limit=250&status=current&type={CONFLUENCE_SPACE_TYPE}&sort=name"
    logging.info(f"[get_space_id_by_name] Searching for space: '{space_name}'")

    while url:
        data = api_call(url)
        if not data or "results" not in data:
            logging.error("[get_space_id_by_name] Failed to retrieve spaces or invalid response.")
            return None

        for space in data["results"]:
            if space_name in space.get("name", ""):
                logging.info(f"[get_space_id_by_name] Found space '{space_name}' with ID {space['id']}.")
                return space["id"]

        paging_info = data.get("_links", {})
        next_link = paging_info.get("next")
        if next_link:
            url = f"{CONFLUENCE_DOMAIN}{next_link}"
        else:
            url = None

    logging.error(f"[get_space_id_by_name] Space '{space_name}' not found.")
    return None

def get_space_labels(space_id, limit_per_call=250):
    """
    Fetch the list of labels *in a given space*.
    Returns a dictionary: { label_name: label_id }.
    """
    labels_map = {}
    url = f"{CONFLUENCE_DOMAIN}/wiki/api/v2/spaces/{space_id}/content/labels?limit={limit_per_call}"

    logging.info(f"[get_space_labels] Fetching labels from space {space_id}...")
    with tqdm(desc="Fetching space labels", unit="label") as pbar:
        while url:
            data = api_call(url)
            if not data:
                logging.warning("[get_space_labels] No data returned or error occurred.")
                break

            results = data.get("results", [])
            for label_obj in results:
                # label_obj might have keys: prefix, name, id
                label_name = label_obj.get("name", "")
                label_id = label_obj.get("id", "")
                if label_name and label_id:
                    labels_map[label_name] = label_id
                pbar.update(1)

            # Check if there's a next page
            paging_links = data.get("_links", {})
            next_link = paging_links.get("next")
            if next_link:
                url = f"{CONFLUENCE_DOMAIN}{next_link}"
            else:
                url = None

    logging.info(f"[get_space_labels] Total labels found: {len(labels_map)}")
    return labels_map

def get_page_content_html(page_id):
    """
    Fetches the HTML content of a Confluence page using the v2 API with storage format.
    """
    endpoint = f"{CONFLUENCE_DOMAIN}/wiki/api/v2/pages/{page_id}?body-format=storage"
    data = api_call(endpoint)
    if not data:
        logging.warning(f"[get_page_content_html] No data returned for page_id={page_id}.")
        return ""
    body_storage = data.get("body", {}).get("storage", {})
    return body_storage.get("value", "")

# --------------------------------------------------------------------
# Label-based Page Fetching (Using Label ID)
# --------------------------------------------------------------------

def fetch_pages_by_label_id(label_id, space_id=None, max_pages=None, limit_per_call=50):
    """
    Fetches Confluence pages that have the given *label ID*.
    Optionally filter to a specific space (if space_id is not None).
    Respects pagination and an optional max_pages limit.

    Endpoint: GET /wiki/api/v2/labels/{label_id}/pages
    """
    all_pages = []

    if space_id:
        url = (f"{CONFLUENCE_DOMAIN}/wiki/api/v2/labels/{label_id}/pages?"
               f"spaceId={space_id}&limit={limit_per_call}")
    else:
        url = f"{CONFLUENCE_DOMAIN}/wiki/api/v2/labels/{label_id}/pages?limit={limit_per_call}"

    logging.info(f"[fetch_pages_by_label_id] Fetching pages for label_id='{label_id}' from: {url}")

    with tqdm(desc=f"Fetching labelID={label_id}", unit="page") as pbar:
        while url:
            data = api_call(url)
            if not data:
                logging.warning(f"[fetch_pages_by_label_id] No data returned or error for label_id={label_id}.")
                break

            results = data.get("results", [])
            for page in results:
                all_pages.append(page)
                pbar.update(1)

                if max_pages and len(all_pages) >= max_pages:
                    logging.info(f"[fetch_pages_by_label_id] Reached max_pages={max_pages}. Stopping fetch.")
                    url = None
                    break

            if url is None:  # max_pages reached
                break

            paging_info = data.get("paging", {})
            next_link = paging_info.get("next", None)
            url = f"{CONFLUENCE_DOMAIN}{next_link}" if next_link else None

    return all_pages

# --------------------------------------------------------------------
# DataFrame Construction
# --------------------------------------------------------------------

def create_dataframe():
    columns = ['id', 'type', 'status', 'title', 'webui', 'content']
    return pd.DataFrame(columns=columns)

def add_pages_to_dataframe(df, pages):
    """
    Adds a list of page dictionaries (v2) to the DataFrame.
    Captures the 'webui' link from '_links' for relative path usage.
    """
    for page in pages:
        page_id = page.get('id', '')
        page_type = page.get('type', '')
        page_status = page.get('status', '')
        webui = page.get('_links', {}).get('webui', '')
        page_title = page.get('title', '')

        new_record = [{
            'id': page_id,
            'type': page_type,
            'status': page_status,
            'title': page_title,
            'webui': webui,
            'content': ''
        }]
        df = pd.concat([df, pd.DataFrame(new_record)], ignore_index=True)

    return df

# --------------------------------------------------------------------
# Qdrant Indexing
# --------------------------------------------------------------------

_id_counter = itertools.count()

def upsert_to_qdrant(qdrant_client, collection_name, vectors, payloads):
    logging.debug(f"[upsert_to_qdrant] Upserting {len(vectors)} vectors to collection '{collection_name}'...")
    points = []
    for i, vec in enumerate(vectors):
        vector_id = next(_id_counter)
        points.append({
            "id": vector_id,
            "vector": vec,
            "payload": payloads[i]
        })
    qdrant_client.upsert(collection_name=collection_name, points=points)
    logging.debug("[upsert_to_qdrant] Batch upsert complete.")

def fetch_and_index_page_content(qdrant_client, model, collection_name, df, batch_size=16):
    """
    Fetches each page's content, converts it to text, then creates a SINGLE embedding
    for the entire page (i.e., one vector per page).
    """
    logging.info("[fetch_and_index_page_content] Starting content fetch and indexing...")

    all_vectors = []
    all_payloads = []

    for idx, row in df.iterrows():
        page_id = row['id']
        page_title = row['title']
        webui = row.get('webui', '')

        # 1) Get page HTML, convert to plain text
        html_content = get_page_content_html(page_id)
        text_content = BeautifulSoup(html_content, 'html.parser').get_text(separator="\n")
        df.loc[idx, 'content'] = text_content  # store in DataFrame if desired

        # 2) Encode the entire page as a single chunk/vector
        embedding = model.encode([text_content])  # shape: (1, embedding_dim)
        single_vector = embedding[0]

        # 3) Prepare upsert payload
        payload = {
            "item_path": webui,
            "chunk_id": page_id,
            "content": text_content,
            "area": page_title

        }
        all_vectors.append(single_vector)
        all_payloads.append(payload)

        # Upsert in batches
        if len(all_vectors) >= batch_size:
            upsert_to_qdrant(qdrant_client, collection_name, all_vectors, all_payloads)
            all_vectors.clear()
            all_payloads.clear()

    # Upsert any leftover vectors
    if all_vectors:
        upsert_to_qdrant(qdrant_client, collection_name, all_vectors, all_payloads)

# --------------------------------------------------------------------
# Main
# --------------------------------------------------------------------

def main():
    if not INDEXER_ENABLED:
        logging.info("Indexer is disabled via INDEXER_ENABLED environment variable. Exiting.")
        sys.exit(0)

    # 1) Parse the labels we want from environment
    label_list = [lbl.strip() for lbl in PAGE_LABELS.split(",") if lbl.strip()]
    if not label_list:
        logging.error("[main] No labels specified in PAGE_LABELS. Exiting.")
        sys.exit(1)

    # 2) Get space_id (if we want to restrict to a certain space)
    logging.info(f"[main] Retrieving space ID for space '{CONFLUENCE_SPACE_NAME}'...")
    space_id = get_space_id_by_name(CONFLUENCE_SPACE_NAME)
    if not space_id:
        logging.error("[main] Could not retrieve space ID. Exiting.")
        sys.exit(1)

    # 3) Fetch the label map for this space: { label_name -> label_id }
    space_labels_map = get_space_labels(space_id, limit_per_call=250)
    if not space_labels_map:
        logging.error("[main] Could not retrieve any labels in this space, or something went wrong.")
        sys.exit(1)

    # 4) For each label name the user wants, get the numeric label_id
    #    Then fetch pages that have that label ID (only in this space).
    all_pages_map = {}
    for label_name in label_list:
        label_id = space_labels_map.get(label_name)
        if not label_id:
            logging.warning(f"[main] Label '{label_name}' not found in space. Skipping.")
            continue

        pages_for_label = fetch_pages_by_label_id(label_id, space_id=space_id, max_pages=MAX_PAGES)
        for p in pages_for_label:
            pid = p.get("id")
            if pid not in all_pages_map:
                all_pages_map[pid] = p

    raw_pages = list(all_pages_map.values())
    logging.info(f"[main] Total unique pages fetched for labels {label_list}: {len(raw_pages)}")

    # 5) Create DataFrame
    df = create_dataframe()
    df = add_pages_to_dataframe(df, raw_pages)
    logging.info("[main] Created DataFrame of pages.")

    # 6) Setup Qdrant client + model
    qdrant_client = QdrantClient(url=QDRANT_URL)
    logging.info("[main] Loading sentence-transformers/all-MiniLM-L6-v2 model...")
    model = SentenceTransformer(MODEL_PATH)
    logging.info("[main] Model loaded successfully.")
    dimension = model.get_sentence_embedding_dimension()

    # 7) Recreate or create the collection in Qdrant
    if RECREATE_COLLECTION:
        logging.info(f"[main] Recreating collection '{COLLECTION_NAME}' in Qdrant...")
        qdrant_client.recreate_collection(
            collection_name=COLLECTION_NAME,
            vectors_config=VectorParams(size=dimension, distance=Distance.COSINE),
        )
    else:
        # Ensure collection exists
        collections = [col.name for col in qdrant_client.get_collections().collections]
        if COLLECTION_NAME not in collections:
            logging.info(f"[main] Creating collection '{COLLECTION_NAME}' in Qdrant...")
            qdrant_client.create_collection(
                collection_name=COLLECTION_NAME,
                vectors_config=VectorParams(size=dimension, distance=Distance.COSINE),
            )

    # 8) Index the pages into Qdrant (one vector per page)
    fetch_and_index_page_content(qdrant_client, model, COLLECTION_NAME, df, batch_size=BATCH_SIZE)

    logging.info("[main] Content indexing complete. Script finished successfully.")


if __name__ == "__main__":
    main()
