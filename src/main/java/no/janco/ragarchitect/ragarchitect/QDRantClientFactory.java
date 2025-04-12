package no.janco.ragarchitect.ragarchitect;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QDRantClientFactory {

    @Bean
    public QdrantClient qdrantClient() {
        return new QdrantClient(QdrantGrpcClient.newBuilder("qdrant", 6334, false).build());
    }
}
