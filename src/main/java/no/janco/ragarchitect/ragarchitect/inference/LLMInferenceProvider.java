package no.janco.ragarchitect.ragarchitect.inference;

public interface LLMInferenceProvider {
    public String infer(String prompt);

    public String getHandlerDetails();
}
