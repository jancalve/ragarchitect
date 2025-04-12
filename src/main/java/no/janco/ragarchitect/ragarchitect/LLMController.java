package no.janco.ragarchitect.ragarchitect;

import no.janco.ragarchitect.ragarchitect.vector.IndexContent;
import no.janco.ragarchitect.ragarchitect.vector.VectorSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/llm")
public class LLMController {

    @Autowired
    private Orchestrator orchestrator;

    @Autowired
    private VectorSearchService vectorSearchService;

    public record ChatRequest(String message) { }

    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest request) throws ExecutionException, InterruptedException {
        return orchestrator.converse(request.message());
    }

    @PostMapping("/prompt")
    public String executePrompt(@RequestBody ChatRequest request) {
        return orchestrator.prompt(request.message());
    }

    @GetMapping("/vector-search")
    public List<IndexContent> searchVectorIndex(@RequestParam("index") String index, @RequestParam("query") String query) throws ExecutionException, InterruptedException {
        return vectorSearchService.searchContents(index, query);
    }
}
