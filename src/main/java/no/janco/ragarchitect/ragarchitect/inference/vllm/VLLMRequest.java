package no.janco.ragarchitect.ragarchitect.inference.vllm;

public class VLLMRequest {
    private String message;

    public VLLMRequest() {
    }

    public VLLMRequest(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
} 