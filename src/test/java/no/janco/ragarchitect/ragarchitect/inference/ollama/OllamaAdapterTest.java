package no.janco.ragarchitect.ragarchitect.inference.ollama;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OllamaAdapterTest {

    @Mock
    private RestTemplate restTemplate;

    private OllamaAdapter ollamaAdapter;

    private static final String TEST_SERVER_URL = "http://test-ollama-server";
    private static final String TEST_MODEL = "test-model";
    private static final String TEST_PROMPT = "Test prompt";
    private static final String TEST_RESPONSE = "Test response";
    private static final String EXPECTED_API_URL = TEST_SERVER_URL + "/api/generate";

    @BeforeEach
    void setUp() {
        ollamaAdapter = new OllamaAdapter(restTemplate);
        ReflectionTestUtils.setField(ollamaAdapter, "ollamaServerUrl", TEST_SERVER_URL);
        ReflectionTestUtils.setField(ollamaAdapter, "ollamaServerModel", TEST_MODEL);
    }

    @Test
    void infer_ShouldReturnResponse_WhenRequestIsSuccessful() {
        // Arrange
        OllamaResponse mockResponseBody = new OllamaResponse();
        mockResponseBody.setResponse(TEST_RESPONSE);
        ResponseEntity<OllamaResponse> mockResponse = new ResponseEntity<>(mockResponseBody, HttpStatus.OK);
        
        when(restTemplate.exchange(
            eq(EXPECTED_API_URL),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(OllamaResponse.class)
        )).thenReturn(mockResponse);

        // Act
        String result = ollamaAdapter.infer(TEST_PROMPT);

        // Assert
        assertEquals(TEST_RESPONSE, result);
        verify(restTemplate).exchange(
            eq(EXPECTED_API_URL),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(OllamaResponse.class)
        );
    }

    @Test
    void infer_ShouldReturnNoResponse_WhenResponseBodyIsNull() {
        // Arrange
        ResponseEntity<OllamaResponse> mockResponse = new ResponseEntity<>(null, HttpStatus.OK);
        
        when(restTemplate.exchange(
            eq(EXPECTED_API_URL),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(OllamaResponse.class)
        )).thenReturn(mockResponse);

        // Act
        String result = ollamaAdapter.infer(TEST_PROMPT);

        // Assert
        assertEquals("No response", result);
        verify(restTemplate).exchange(
            eq(EXPECTED_API_URL),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(OllamaResponse.class)
        );
    }

    @Test
    void infer_ShouldHandleRestTemplateException() {
        // Arrange
        when(restTemplate.exchange(
            eq(EXPECTED_API_URL),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(OllamaResponse.class)
        )).thenThrow(new RuntimeException("Test error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> ollamaAdapter.infer(TEST_PROMPT));
        verify(restTemplate).exchange(
            eq(EXPECTED_API_URL),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(OllamaResponse.class)
        );
    }

    @Test
    void getHandlerDetails_ShouldReturnClassName() {
        // Act
        String result = ollamaAdapter.getHandlerDetails();

        // Assert
        assertEquals(OllamaAdapter.class.getName(), result);
    }

    @Test
    void infer_ShouldSetCorrectHeadersAndRequestBody() {
        // Arrange
        OllamaResponse mockResponseBody = new OllamaResponse();
        mockResponseBody.setResponse(TEST_RESPONSE);
        ResponseEntity<OllamaResponse> mockResponse = new ResponseEntity<>(mockResponseBody, HttpStatus.OK);
        
        when(restTemplate.exchange(
            eq(EXPECTED_API_URL),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(OllamaResponse.class)
        )).thenReturn(mockResponse);

        // Act
        ollamaAdapter.infer(TEST_PROMPT);

        // Assert
        verify(restTemplate).exchange(
            eq(EXPECTED_API_URL),
            eq(HttpMethod.POST),
            argThat(entity -> {
                // Check headers
                boolean headersCorrect = entity.getHeaders().getFirst("Content-Type").equals("application/json");
                
                // Check request body
                boolean bodyCorrect = false;
                if (entity.getBody() instanceof OllamaRequest) {
                    OllamaRequest request = (OllamaRequest) entity.getBody();
                    bodyCorrect = request.getPrompt().equals(TEST_PROMPT) && 
                                 !request.isStream() && 
                                 request.getModel().equals(TEST_MODEL);
                }
                
                return headersCorrect && bodyCorrect;
            }),
            eq(OllamaResponse.class)
        );
    }
} 