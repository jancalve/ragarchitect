package no.janco.ragarchitect.ragarchitect.inference.vllm;

public class VLLMResponse {
    private String response;

    public VLLMResponse() {
    }

    public VLLMResponse(String response) {
        this.response = response;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }
} 