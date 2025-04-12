package no.janco.ragarchitect.ragarchitect.prompt;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!ollama")
public class DefaultPromptTruncator implements PromptTruncator {
    @Override
    public String truncate(String prompt) {
        return prompt;
    }
} 