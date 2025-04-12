package no.janco.ragarchitect.ragarchitect.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("ollama")
public class OllamaPromptTruncator implements PromptTruncator {
    private static final Logger logger = LoggerFactory.getLogger(OllamaPromptTruncator.class);
    private static final int MAX_TOKENS = 8092; // adjust based on your model's context window (TODO - Use context window description from ollama)
    private static final double TOKENS_PER_CHAR_ESTIMATE = 0.35; // rough average (TODO - Use accurate estimation from ollama)

    @Override
    public String truncate(String prompt) {
        if (prompt == null || prompt.isEmpty()) {
            return prompt;
        }

        int estimatedTokens = (int) (prompt.length() * TOKENS_PER_CHAR_ESTIMATE);
        if (estimatedTokens <= MAX_TOKENS) {
            logger.debug("Prompt within token limits ({} tokens), no truncation needed", estimatedTokens);
            return prompt;
        }

        int maxLength = (int) (MAX_TOKENS / TOKENS_PER_CHAR_ESTIMATE);
        String truncatedPrompt = prompt.substring(0, Math.min(maxLength, prompt.length()));
        logger.info("Prompt truncated from {} to {} characters (estimated tokens: {} -> {})", 
            prompt.length(), truncatedPrompt.length(), estimatedTokens, MAX_TOKENS);
        return truncatedPrompt;
    }
} 