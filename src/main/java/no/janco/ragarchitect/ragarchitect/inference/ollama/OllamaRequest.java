package no.janco.ragarchitect.ragarchitect.inference.ollama;

public class OllamaRequest {
    private String prompt;
    private boolean stream;
    private String model;
    private Options options;

    public OllamaRequest() {
        this.options = new Options(8092);
    }

    public OllamaRequest(String prompt, boolean stream, String model) {
        this.prompt = prompt;
        this.stream = stream;
        this.model = model;
        this.options = new Options(8092);
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public boolean isStream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Options getOptions() {
        return options;
    }

    public void setOptions(Options options) {
        this.options = options;
    }

    public static class Options {
        private int num_ctx;

        public Options(int num_ctx) {
            this.num_ctx = num_ctx;
        }

        public int getNum_ctx() {
            return num_ctx;
        }

        public void setNum_ctx(int num_ctx) {
            this.num_ctx = num_ctx;
        }
    }
}

