package no.janco.ragarchitect.ragarchitect;

import no.janco.ragarchitect.ragarchitect.inference.LLMInferenceProvider;
import no.janco.ragarchitect.ragarchitect.prompt.PromptTruncator;
import no.janco.ragarchitect.ragarchitect.vector.VectorSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrchestratorTest {

    @Mock
    private LLMInferenceProvider inferenceHandler;

    @Mock
    private VectorSearchService vectorSearchService;

    @Mock
    private PromptTruncator promptTruncator;

    private Orchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new Orchestrator(inferenceHandler, vectorSearchService, promptTruncator);
    }

    @Test
    void converse_ShouldCombineContextAndMessage() throws ExecutionException, InterruptedException {
        // Arrange
        String message = "What is the purpose of the Orchestrator class?";
        String context = "The Orchestrator class manages LLM interactions and vector search.";
        String expectedResponse = "The Orchestrator class coordinates interactions between components.";
        String truncatedPrompt = "You are a helpful expert programmer...";
        
        when(vectorSearchService.searchVector(message)).thenReturn(context);
        when(promptTruncator.truncate(anyString())).thenReturn(truncatedPrompt);
        when(inferenceHandler.infer(anyString())).thenReturn(expectedResponse);
        when(inferenceHandler.getHandlerDetails()).thenReturn("TestHandler");

        // Act
        String response = orchestrator.converse(message);

        // Assert
        assertEquals(expectedResponse, response);
        verify(vectorSearchService).searchVector(message);
        verify(promptTruncator).truncate(anyString());
        verify(inferenceHandler).infer(truncatedPrompt);
    }

    @Test
    void converse_ShouldHandleEmptyContext() throws ExecutionException, InterruptedException {
        // Arrange
        String message = "Simple question";
        String expectedResponse = "Simple answer";
        String truncatedPrompt = "You are a helpful expert programmer...";
        
        when(vectorSearchService.searchVector(message)).thenReturn("");
        when(promptTruncator.truncate(anyString())).thenReturn(truncatedPrompt);
        when(inferenceHandler.infer(anyString())).thenReturn(expectedResponse);
        when(inferenceHandler.getHandlerDetails()).thenReturn("TestHandler");

        // Act
        String response = orchestrator.converse(message);

        // Assert
        assertEquals(expectedResponse, response);
        verify(vectorSearchService).searchVector(message);
        verify(promptTruncator).truncate(anyString());
        verify(inferenceHandler).infer(truncatedPrompt);
    }

    @Test
    void converse_ShouldHandleVectorSearchException() throws ExecutionException, InterruptedException {
        // Arrange
        String message = "Test message";
        when(vectorSearchService.searchVector(message)).thenThrow(new ExecutionException("Vector search failed", new RuntimeException()));

        // Act & Assert
        assertThrows(ExecutionException.class, () -> orchestrator.converse(message));
        verify(inferenceHandler, never()).infer(anyString());
        verify(promptTruncator, never()).truncate(anyString());
    }

    @Test
    void converse_ShouldHandleInferenceException() throws ExecutionException, InterruptedException {
        // Arrange
        String message = "Test message";
        String context = "Test context";
        String truncatedPrompt = "You are a helpful expert programmer...";
        
        when(vectorSearchService.searchVector(message)).thenReturn(context);
        when(promptTruncator.truncate(anyString())).thenReturn(truncatedPrompt);
        when(inferenceHandler.infer(anyString())).thenThrow(new RuntimeException("Inference failed"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> orchestrator.converse(message));
        verify(promptTruncator).truncate(anyString());
    }

    @Test
    void rawPrompt_ShouldPassMessageDirectlyToInferenceHandler() throws ExecutionException, InterruptedException {
        // Arrange
        String message = "Raw prompt message";
        String expectedResponse = "Raw prompt response";
        
        when(inferenceHandler.infer(message)).thenReturn(expectedResponse);
        when(inferenceHandler.getHandlerDetails()).thenReturn("TestHandler");

        // Act
        String response = orchestrator.prompt(message);

        // Assert
        assertEquals(expectedResponse, response);
        verify(inferenceHandler).infer(message);
        verify(vectorSearchService, never()).searchVector(anyString());
        verify(promptTruncator, never()).truncate(anyString());
    }

    @Test
    void rawPrompt_ShouldHandleInferenceException() throws ExecutionException, InterruptedException {
        // Arrange
        String message = "Raw prompt message";
        when(inferenceHandler.infer(message)).thenThrow(new RuntimeException("Inference failed"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> orchestrator.prompt(message));
        verify(vectorSearchService, never()).searchVector(anyString());
        verify(promptTruncator, never()).truncate(anyString());
    }
} 