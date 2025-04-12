package no.janco.ragarchitect.ragarchitect.inference.ollama;

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
@Profile("ollama")
public class OllamaAdapter implements LLMInferenceProvider {

    @Value("${ollama.server.url}")
    private String ollamaServerUrl;

    @Value("${ollama.server.model}")
    private String ollamaServerModel;

    private final RestTemplate restTemplate;

    public OllamaAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String infer(String prompt) {
        String apiUrl = ollamaServerUrl + "/api/generate";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        // Use DTO instead of manually creating JSON
        OllamaRequest request = new OllamaRequest(prompt, false, ollamaServerModel);
        HttpEntity<OllamaRequest> requestEntity = new HttpEntity<>(request, headers);

        // Send request and parse response
        ResponseEntity<OllamaResponse> response = restTemplate.exchange(apiUrl, HttpMethod.POST, requestEntity, OllamaResponse.class);

        // Return only the response text
        return response.getBody() != null ? response.getBody().getResponse() : "No response";
    }

    @Override
    public String getHandlerDetails() {
        return getClass().getName();
    }
}
