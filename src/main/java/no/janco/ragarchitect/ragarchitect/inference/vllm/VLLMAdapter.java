package no.janco.ragarchitect.ragarchitect.inference.vllm;

import no.janco.ragarchitect.ragarchitect.inference.LLMInferenceProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Profile("vllm")
public class VLLMAdapter implements LLMInferenceProvider {
    private static final Logger LOG = LoggerFactory.getLogger(VLLMAdapter.class);

    @Value("${vllm.server.url}")
    private String vllmServerUrl;

    private final RestTemplate restTemplate;

    public VLLMAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String infer(String prompt) {
        String apiUrl = vllmServerUrl + "/inference";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        VLLMRequest request = new VLLMRequest(prompt);
        HttpEntity<VLLMRequest> requestEntity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<VLLMResponse> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                requestEntity,
                VLLMResponse.class
            );

            return response.getBody() != null ? response.getBody().getResponse() : "No response";
        } catch (Exception e) {
            LOG.error("Error during vLLM inference: {}", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    @Override
    public String getHandlerDetails() {
        return getClass().getName();
    }
} 