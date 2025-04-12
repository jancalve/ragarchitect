package no.janco.ragarchitect.ragarchitect.vector;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static io.qdrant.client.QueryFactory.nearest;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;

@Component
public class VectorSearchService {

    private static final Logger LOG = LoggerFactory.getLogger(VectorSearchService.class);

    private QdrantClient qdrantClient;

    private final EmbeddingService embeddingService;

    public VectorSearchService(EmbeddingService embeddingService, QdrantClient qdRantClient) {
        this.embeddingService = embeddingService;
        this.qdrantClient = qdRantClient;
    }

    public String searchVector(String message) throws ExecutionException, InterruptedException {

        List<Float> vector = embeddingService.getEmbedding(message);
        LOG.info("Received vector {}", vector);

        // Get available indexes
        List<String> availableIndexes = qdrantClient.listCollectionsAsync().get();
        LOG.info("Available indexes: {}", availableIndexes);

        StringBuilder srb = new StringBuilder();
        
        // Only query code index if it exists
        if (availableIndexes.contains("code")) {
            LOG.info("Querying code index");
            List<Points.ScoredPoint> codePoints = qdrantClient.queryAsync(Points.QueryPoints.newBuilder()
                    .setCollectionName("code")
                    .setQuery(nearest(vector))
                    .setWithPayload(enable(true))
                    .setLimit(30)
                    .setScoreThreshold(0.3f)
                    .build()).get();
            
            for (Points.ScoredPoint point : codePoints) {
                String code = point.getPayloadMap().get("content").getStringValue();
                srb.append("\nCode snippet: ").append(code);
            }
        } else {
            LOG.info("Code index not found, skipping");
        }
        
        // Only query confluence index if it exists
        if (availableIndexes.contains("confluence")) {
            LOG.info("Querying confluence index");
            List<Points.ScoredPoint> confluencePoints = qdrantClient.queryAsync(Points.QueryPoints.newBuilder()
                    .setCollectionName("confluence")
                    .setQuery(nearest(vector))
                    .setWithPayload(enable(true))
                    .setLimit(10)
                    .setScoreThreshold(0.2f)
                    .build()).get();
            
            for (Points.ScoredPoint point : confluencePoints) {
                String textChunk = point.getPayloadMap().get("content").getStringValue();
                srb.append("\nConfluence snippet: ").append(textChunk);
            }
        } else {
            LOG.info("Confluence index not found, skipping");
        }

        return srb.toString();
    }

    public List<IndexContent> searchContents(String index, String query) throws ExecutionException, InterruptedException {
        List<Float> vector = embeddingService.getEmbedding(query);
        LOG.info("Searching in all files for: {}", query);

        List<IndexContent> results = new ArrayList<>();

            List<Points.ScoredPoint> points = qdrantClient.queryAsync(Points.QueryPoints.newBuilder()
                    .setCollectionName(index)
                    .setQuery(nearest(vector))
                    .setWithPayload(enable(true))
                    .setLimit(20) // Adjust limit as needed
                    .setScoreThreshold(0.1f) // Adjust similarity threshold
                    .build()).get();

            for (Points.ScoredPoint point : points) {
                String projectName = point.getPayloadMap().get("area").getStringValue();
                String itemPath = point.getPayloadMap().get("item_path").getStringValue();
                String chunkId = point.getPayloadMap().get("chunk_id").getStringValue();
                String content = point.getPayloadMap().get("content").getStringValue();

                results.add(new IndexContent(projectName, itemPath, chunkId, content));

        }

        return results;
    }


}
