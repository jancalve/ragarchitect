package no.janco.ragarchitect.ragarchitect.inference.awsbedrock;

import no.janco.ragarchitect.ragarchitect.inference.aws.BedrockAdapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.Collections;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AWSBedrockAdapterTest {

    @Mock
    private BedrockRuntimeClient bedrockRuntimeClient;

    @Captor
    private ArgumentCaptor<Consumer<ConverseRequest.Builder>> requestCaptor;

    private BedrockAdapter awsBedrockAdapter;

    private static final String TEST_MODEL_ID = "test-model-id";
    private static final String TEST_REGION_ID = "us-east-1";
    private static final String TEST_PROMPT = "Test prompt";
    private static final String TEST_RESPONSE = "Test response";

    @BeforeEach
    void setUp() {
        awsBedrockAdapter = new BedrockAdapter();
        ReflectionTestUtils.setField(awsBedrockAdapter, "bedrockModelId", TEST_MODEL_ID);
        ReflectionTestUtils.setField(awsBedrockAdapter, "regionId", TEST_REGION_ID);
        ReflectionTestUtils.setField(awsBedrockAdapter, "client", bedrockRuntimeClient);
    }

    @Test
    void infer_ShouldReturnResponse_WhenRequestIsSuccessful() {
        // Arrange
        ConverseOutput output = ConverseOutput.builder()
                .message(Message.builder()
                        .content(Collections.singletonList(ContentBlock.fromText(TEST_RESPONSE)))
                        .role(ConversationRole.ASSISTANT)
                        .build())
                .build();

        ConverseResponse mockResponse = ConverseResponse.builder()
                .output(output)
                .build();

        doReturn(mockResponse).when(bedrockRuntimeClient).converse(any(Consumer.class));

        // Act
        String result = awsBedrockAdapter.infer(TEST_PROMPT);

        // Assert
        assertEquals(TEST_RESPONSE, result);
        verify(bedrockRuntimeClient).converse(requestCaptor.capture());
        
        // Verify the request builder was called with correct parameters
        ConverseRequest.Builder builder = ConverseRequest.builder();
        requestCaptor.getValue().accept(builder);
        ConverseRequest request = builder.build();
        
        assertEquals(TEST_MODEL_ID, request.modelId());
        assertEquals(1, request.messages().size());
        assertEquals(ConversationRole.USER, request.messages().get(0).role());
        assertEquals(TEST_PROMPT, request.messages().get(0).content().get(0).text());
    }

    @Test
    void infer_ShouldHandleEmptyResponse() {
        // Arrange
        ConverseOutput output = ConverseOutput.builder()
                .message(Message.builder()
                        .content(Collections.emptyList())
                        .role(ConversationRole.ASSISTANT)
                        .build())
                .build();

        ConverseResponse mockResponse = ConverseResponse.builder()
                .output(output)
                .build();

        doReturn(mockResponse).when(bedrockRuntimeClient).converse(any(Consumer.class));

        // Act & Assert
        assertThrows(IndexOutOfBoundsException.class, () -> awsBedrockAdapter.infer(TEST_PROMPT));
        verify(bedrockRuntimeClient).converse(any(Consumer.class));
    }

    @Test
    void infer_ShouldHandleBedrockException() {
        // Arrange
        doThrow(new RuntimeException("Test error")).when(bedrockRuntimeClient).converse(any(Consumer.class));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> awsBedrockAdapter.infer(TEST_PROMPT));
        verify(bedrockRuntimeClient).converse(any(Consumer.class));
    }

    @Test
    void getHandlerDetails_ShouldReturnClassName() {
        // Act
        String result = awsBedrockAdapter.getHandlerDetails();

        // Assert
        assertEquals(BedrockAdapter.class.getName(), result);
    }
} 