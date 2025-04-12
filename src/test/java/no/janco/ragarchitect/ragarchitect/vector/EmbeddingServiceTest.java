package no.janco.ragarchitect.ragarchitect.vector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private EmbeddingService embeddingService;

    private static final String EMBEDDING_URL = "http://embedding-service:8080/embed";

    @Test
    void getEmbedding_ShouldReturnEmbeddingList_WhenSuccessful() {
        // Arrange
        String prompt = "Test prompt";
        List<Float> expectedEmbedding = Arrays.asList(0.1f, 0.2f, 0.3f);
        EmbeddingService.EmbeddingResponse response = new EmbeddingService.EmbeddingResponse();
        response.setEmbeddings(Arrays.asList(expectedEmbedding));

        ResponseEntity<EmbeddingService.EmbeddingResponse> responseEntity = 
            new ResponseEntity<>(response, HttpStatus.OK);

        when(restTemplate.postForEntity(
            eq(EMBEDDING_URL),
            ArgumentMatchers.<HttpEntity<String>>any(),
            eq(EmbeddingService.EmbeddingResponse.class)
        )).thenReturn(responseEntity);

        // Act
        List<Float> result = embeddingService.getEmbedding(prompt);

        // Assert
        assertNotNull(result);
        assertEquals(expectedEmbedding, result);
    }

    @Test
    void getEmbedding_ShouldEscapeQuotes_InPrompt() {
        // Arrange
        String prompt = "Test \"quoted\" prompt";
        List<Float> expectedEmbedding = Arrays.asList(0.1f, 0.2f, 0.3f);
        EmbeddingService.EmbeddingResponse response = new EmbeddingService.EmbeddingResponse();
        response.setEmbeddings(Arrays.asList(expectedEmbedding));

        ResponseEntity<EmbeddingService.EmbeddingResponse> responseEntity = 
            new ResponseEntity<>(response, HttpStatus.OK);

        when(restTemplate.postForEntity(
            eq(EMBEDDING_URL),
            ArgumentMatchers.<HttpEntity<String>>any(),
            eq(EmbeddingService.EmbeddingResponse.class)
        )).thenReturn(responseEntity);

        // Act
        List<Float> result = embeddingService.getEmbedding(prompt);

        // Assert
        assertNotNull(result);
        assertEquals(expectedEmbedding, result);
    }

    @Test
    void getEmbedding_ShouldThrowException_WhenServiceReturnsError() {
        // Arrange
        String prompt = "Test prompt";
        when(restTemplate.postForEntity(
            eq(EMBEDDING_URL),
            ArgumentMatchers.<HttpEntity<String>>any(),
            eq(EmbeddingService.EmbeddingResponse.class)
        )).thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> embeddingService.getEmbedding(prompt));
    }

    @Test
    void getEmbedding_ShouldThrowException_WhenResponseBodyIsNull() {
        // Arrange
        String prompt = "Test prompt";
        ResponseEntity<EmbeddingService.EmbeddingResponse> responseEntity = 
            new ResponseEntity<>(null, HttpStatus.OK);

        when(restTemplate.postForEntity(
            eq(EMBEDDING_URL),
            ArgumentMatchers.<HttpEntity<String>>any(),
            eq(EmbeddingService.EmbeddingResponse.class)
        )).thenReturn(responseEntity);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> embeddingService.getEmbedding(prompt));
    }
} 