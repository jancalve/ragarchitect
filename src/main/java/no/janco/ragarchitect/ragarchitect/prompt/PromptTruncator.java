package no.janco.ragarchitect.ragarchitect.prompt;

public interface PromptTruncator {
    /**
     * Truncates the prompt if necessary based on the implementation.
     * @param prompt The original prompt to potentially truncate
     * @return The truncated prompt or the original prompt if no truncation is needed
     */
    String truncate(String prompt);
} 