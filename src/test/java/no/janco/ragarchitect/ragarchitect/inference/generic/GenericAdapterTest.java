package no.janco.ragarchitect.ragarchitect.inference.generic;

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
class GenericAdapterTest {

    @Mock
    private RestTemplate restTemplate;

    private GenericAdapter genericAdapter;

    private static final String TEST_INFERENCE_URL = "http://test-server/inference";
    private static final String TEST_PROMPT = "Test prompt";
    private static final String TEST_RESPONSE = "Test response";

    @BeforeEach
    void setUp() {
        genericAdapter = new GenericAdapter(restTemplate);
        ReflectionTestUtils.setField(genericAdapter, "inferenceUrl", TEST_INFERENCE_URL);
    }

    @Test
    void infer_ShouldReturnResponse_WhenRequestIsSuccessful() {
        // Arrange
        ResponseEntity<String> mockResponse = new ResponseEntity<>(TEST_RESPONSE, HttpStatus.OK);
        when(restTemplate.exchange(
            eq(TEST_INFERENCE_URL),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(mockResponse);

        // Act
        String result = genericAdapter.infer(TEST_PROMPT);

        // Assert
        assertEquals(TEST_RESPONSE, result);
        verify(restTemplate).exchange(
            eq(TEST_INFERENCE_URL),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    @Test
    void infer_ShouldReturnNoResponse_WhenResponseBodyIsNull() {
        // Arrange
        ResponseEntity<String> mockResponse = new ResponseEntity<>(null, HttpStatus.OK);
        when(restTemplate.exchange(
            eq(TEST_INFERENCE_URL),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(mockResponse);

        // Act
        String result = genericAdapter.infer(TEST_PROMPT);

        // Assert
        assertEquals("No response", result);
        verify(restTemplate).exchange(
            eq(TEST_INFERENCE_URL),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    @Test
    void infer_ShouldHandleRestTemplateException() {
        // Arrange
        when(restTemplate.exchange(
            eq(TEST_INFERENCE_URL),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(String.class)
        )).thenThrow(new RuntimeException("Test error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> genericAdapter.infer(TEST_PROMPT));
        verify(restTemplate).exchange(
            eq(TEST_INFERENCE_URL),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(String.class)
        );
    }

    @Test
    void getHandlerDetails_ShouldReturnClassName() {
        // Act
        String result = genericAdapter.getHandlerDetails();

        // Assert
        assertEquals(GenericAdapter.class.getName(), result);
    }

    @Test
    void infer_ShouldSetCorrectHeaders() {
        // Arrange
        ResponseEntity<String> mockResponse = new ResponseEntity<>(TEST_RESPONSE, HttpStatus.OK);
        when(restTemplate.exchange(
            eq(TEST_INFERENCE_URL),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(mockResponse);

        // Act
        genericAdapter.infer(TEST_PROMPT);

        // Assert
        verify(restTemplate).exchange(
            eq(TEST_INFERENCE_URL),
            eq(HttpMethod.POST),
            argThat(entity -> 
                entity.getHeaders().getFirst("Content-Type").equals("application/json") &&
                entity.getBody() instanceof GenericRequest &&
                ((GenericRequest) entity.getBody()).getMessage().equals(TEST_PROMPT)
            ),
            eq(String.class)
        );
    }
} 