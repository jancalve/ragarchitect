package no.janco.ragarchitect.ragarchitect.inference.aws;

import no.janco.ragarchitect.ragarchitect.inference.LLMInferenceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

import jakarta.annotation.PostConstruct;

@Service
@Profile("bedrock")
public class BedrockAdapter implements LLMInferenceProvider {

    private static final Logger LOG = LoggerFactory.getLogger(BedrockAdapter.class);

    @Value("${bedrock.modelId}")
    private String bedrockModelId;

    @Value("${flyt.bedrock.regionId}")
    private String regionId;

    private BedrockRuntimeClient client;

    @PostConstruct
    public void initialize() {
        this.client = BedrockRuntimeClient.builder()
            .credentialsProvider(DefaultCredentialsProvider.create())
            .region(Region.of(regionId))
            .build();
    }

    @Override
    public String infer(String prompt) {
        Message userMessage = Message.builder()
                .content(ContentBlock.fromText(prompt))
                .role(ConversationRole.USER)
                .build();

        LOG.info("Sending query {} using modelId {}", userMessage, bedrockModelId);
        ConverseResponse response = client.converse(
                request -> request.modelId(bedrockModelId).messages(userMessage)
        );

        return response.output().message().content().get(0).text();
    }

    @Override
    public String getHandlerDetails() {
        return getClass().getName();
    }
}
