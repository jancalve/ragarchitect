package no.janco.ragarchitect.ragarchitect;

import no.janco.ragarchitect.ragarchitect.inference.LLMInferenceProvider;
import no.janco.ragarchitect.ragarchitect.prompt.PromptTruncator;
import no.janco.ragarchitect.ragarchitect.vector.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

@Service
public class Orchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(Orchestrator.class);

    private final LLMInferenceProvider inferenceHandler;
    private final VectorSearchService vectorSearchService;
    private final PromptTruncator promptTruncator;

    public Orchestrator(LLMInferenceProvider inferenceHandler, 
                       VectorSearchService vectorSearchService,
                       PromptTruncator promptTruncator) {
        this.inferenceHandler = inferenceHandler;
        this.vectorSearchService = vectorSearchService;
        this.promptTruncator = promptTruncator;
    }

    public String converse(String message) throws ExecutionException, InterruptedException {
        LOG.info("Received message {}", message);

        String context = vectorSearchService.searchVector(message);

        StringBuilder srb = new StringBuilder();
        srb.append("You are a helpful expert programmer. Use the following context to answer the question. ").append("\n")
        .append("Do not mention the nature of these snippets or how the information was obtained. ").append("\n")
        .append("My question is: ").append(message).append("\n") 
        .append("This is the relevant context and snippets: ").append("\n")
        .append(context);
        
        String prompt = srb.toString();
        String truncatedPrompt = promptTruncator.truncate(prompt);

        LOG.info("Using inference handler {} for prompt {}", inferenceHandler.getHandlerDetails(), truncatedPrompt);
        String responseText = inferenceHandler.infer(truncatedPrompt);
        LOG.info("Received response {}", responseText);

        return responseText;
    }


    public String prompt(String message) {
        LOG.info("Using inference handler {} for prompt {}", inferenceHandler.getHandlerDetails(), message);
        String responseText = inferenceHandler.infer(message);
        LOG.info("Received response {}", responseText);

        return responseText;
    }


}
