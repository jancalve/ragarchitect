package no.janco.ragarchitect.ragarchitect.inference.generic;

public class GenericRequest {
    private String message;

    public GenericRequest() {
    }

    public GenericRequest(String message) {
        this.message = message;

    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

