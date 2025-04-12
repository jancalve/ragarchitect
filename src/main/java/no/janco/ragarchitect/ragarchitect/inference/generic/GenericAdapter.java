package no.janco.ragarchitect.ragarchitect.inference.generic;

import no.janco.ragarchitect.ragarchitect.inference.LLMInferenceProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Profile("generic")
public class GenericAdapter implements LLMInferenceProvider {

    @Value("${generic.server.url}")
    private String inferenceUrl;

    private final RestTemplate restTemplate;

    public GenericAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String infer(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        // Use DTO instead of manually creating JSON
        GenericRequest request = new GenericRequest(prompt);
        HttpEntity<GenericRequest> requestEntity = new HttpEntity<>(request, headers);

        // Send request and parse response
        ResponseEntity<String> response = restTemplate.exchange(inferenceUrl, HttpMethod.POST, requestEntity, String.class);

        // Return only the response text
        return response.getBody() != null ? response.getBody() : "No response";
    }

    @Override
    public String getHandlerDetails() {
        return getClass().getName();
    }
}
