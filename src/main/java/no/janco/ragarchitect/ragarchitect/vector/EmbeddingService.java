package no.janco.ragarchitect.ragarchitect.vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class EmbeddingService {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingService.class);
    private final RestTemplate restTemplate;
    private static final String EMBEDDING_URL = "http://embedding-service:8080/embed";

    public EmbeddingService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<Float> getEmbedding(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestJson = "{\"sentences\": [\"" + prompt.replace("\"", "\\\"") + "\"]}";

        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

        ResponseEntity<EmbeddingResponse> response = restTemplate.postForEntity(
                EMBEDDING_URL, entity, EmbeddingResponse.class
        );

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            LOG.info("Amount of embeddings {}", response.getBody().getEmbeddings().size());
            return response.getBody().getEmbeddings().get(0);
        } else {
            throw new RuntimeException("Failed to get embedding");
        }
    }

    public static class EmbeddingResponse {
        private List<List<Float>> embeddings;

        public List<List<Float>> getEmbeddings() {
            return embeddings;
        }

        public void setEmbeddings(List<List<Float>> embeddings) {
            this.embeddings = embeddings;
        }
    }
}

