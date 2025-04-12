package no.janco.ragarchitect.ragarchitect.vector;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.GetPoints;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
public class QdrantService {

    private final QdrantClient qdrantClient;

    public QdrantService(QdrantClient qdrantClient) {
        this.qdrantClient = qdrantClient;
    }

    // Fetches available indexes (collections)
    public List<String> getIndexes() throws ExecutionException, InterruptedException {
        return qdrantClient.listCollectionsAsync().get();
    }

    public List<IndexContent> getIndexContents(String collectionName) throws ExecutionException, InterruptedException {
        Points.ScrollPoints request = Points.ScrollPoints.newBuilder()
                .setCollectionName(collectionName)
                .setLimit(150) // Fetch up to 150 items
                .build();

        Points.ScrollResponse response = qdrantClient.scrollAsync(request).get();

        return response.getResultList().stream()
                .map(point -> {
                    String projectName = point.getPayloadMap().get("area").getStringValue();
                    String itemPath = point.getPayloadMap().get("item_path").getStringValue();
                    String chunkId = point.getPayloadMap().get("chunk_id").getStringValue();
                    String content = point.getPayloadMap().get("content").getStringValue();
                    return new IndexContent(projectName, itemPath, chunkId, content);
                })
                .collect(Collectors.toList());
    }



    // Fetch details of a specific item
    public String getItemDetails(String collectionName, String itemId) throws ExecutionException, InterruptedException {
        Points.PointId pointId = Points.PointId.newBuilder().setUuid(itemId).build();
        GetPoints pointToGet = GetPoints.newBuilder().setCollectionName(collectionName).setIds(0, pointId).build();

        List<Points.RetrievedPoint> response = qdrantClient.retrieveAsync(pointToGet, Duration.ofSeconds(10)).get();

        return response.isEmpty() ? "No details found" : response.get(0).toString(); // Convert to string for now
    }
}
