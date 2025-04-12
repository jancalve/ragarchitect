package no.janco.ragarchitect.ragarchitect.frontend;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;
import no.janco.ragarchitect.ragarchitect.vector.IndexContent;
import no.janco.ragarchitect.ragarchitect.vector.QdrantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Route(value = "architect", layout = MainLayout.class)
public class ArchitectView extends VerticalLayout {

    private static final Logger LOG = LoggerFactory.getLogger(ArchitectView.class);

    private final QdrantService qdrantService;
    private final RestTemplate restTemplate;

    // Track the selected index name
    private String selectedIndexName = null;

    // Grids
    private final Grid<String> indexGrid;
    private final Grid<IndexContent> contentsGrid;

    // Components
    private final TextField searchField;
    private final Button searchButton;
    private final TextArea itemDetailTextArea;
    private final TextArea rawPromptTextArea;
    private final TextArea responseTextArea;
    private final TextArea tokenCountTextArea;
    private final ProgressBar loadingIndicator;
    private final Button togglePromptButton;
    private final Button toggleItemDetailsButton;
    private boolean isPromptExpanded = false;
    private boolean isItemDetailsExpanded = false;

    // Some general state
    private static final int MODEL_TOKEN_LIMIT = 8092;

    @Autowired
    public ArchitectView(QdrantService qdrantService, RestTemplate restTemplate)
            throws ExecutionException, InterruptedException {
        this.qdrantService = qdrantService;
        this.restTemplate = restTemplate;

        // Set up the main layout
        this.setSizeFull();
        this.setPadding(true);
        this.setSpacing(true);

        // ------------------------------
        // 1) Create the index selection area
        // ------------------------------
        indexGrid = new Grid<>(String.class);
        indexGrid.setSelectionMode(Grid.SelectionMode.SINGLE);
        indexGrid.addColumn(item -> item).setHeader("Index");
        indexGrid.setHeight("180px");
        indexGrid.setWidthFull();

        // ------------------------------
        // 2) Create the search area
        // ------------------------------
        searchField = new TextField();
        searchField.setPlaceholder("Search index...");
        searchField.setWidthFull();

        searchButton = new Button(VaadinIcon.SEARCH.create(), event -> searchContents());

        HorizontalLayout searchLayout = new HorizontalLayout(searchField, searchButton);
        searchLayout.setWidthFull();
        searchLayout.setFlexGrow(1, searchField);

        // ------------------------------
        // 3) Create the contents grid
        // ------------------------------
        contentsGrid = new Grid<>(IndexContent.class);
        contentsGrid.setSelectionMode(Grid.SelectionMode.SINGLE);
        contentsGrid.setWidthFull();
        contentsGrid.setHeight("300px");

        // ------------------------------
        // 4) Create the details and prompt area
        // ------------------------------
        // Item details
        itemDetailTextArea = new TextArea("Selected Content");
        itemDetailTextArea.setWidthFull();
        itemDetailTextArea.setHeight("200px");
        itemDetailTextArea.getStyle()
                .set("white-space", "pre-wrap")
                .set("word-wrap", "break-word")
                .set("font-family", "monospace")
                .set("padding", "10px");

        toggleItemDetailsButton = new Button("🔽 Expand Content", event -> toggleItemDetailsSize());
        toggleItemDetailsButton.addClassName("toggle-button");
        toggleItemDetailsButton.getStyle().set("margin", "0 auto");

        Button addItemDetailsToPromptButton = new Button("➕ Add to Prompt", event -> addItemDetailsToRawPrompt());
        addItemDetailsToPromptButton.getStyle().set("margin", "0");
        addItemDetailsToPromptButton.getStyle().set("align-self", "flex-start");

        VerticalLayout itemDetailsLayout = new VerticalLayout(toggleItemDetailsButton, itemDetailTextArea, addItemDetailsToPromptButton);
        itemDetailsLayout.setWidthFull();
        itemDetailsLayout.setAlignItems(Alignment.CENTER);
        itemDetailsLayout.setSpacing(false);
        
        // Prompt and response
        rawPromptTextArea = new TextArea("LLM Raw Prompt");
        rawPromptTextArea.setWidthFull();
        rawPromptTextArea.setHeight("200px");
        rawPromptTextArea.getStyle()
                .set("white-space", "pre-wrap")
                .set("word-wrap", "break-word")
                .set("font-family", "monospace")
                .set("padding", "10px");

        togglePromptButton = new Button("🔽 Expand Prompt", event -> togglePromptSize());
        togglePromptButton.addClassName("toggle-button");
        togglePromptButton.getStyle().set("margin", "0 auto");

        Button sendButton = new Button("Send", event -> sendPromptToLLM());
        sendButton.getStyle().set("margin", "0");
        sendButton.getStyle().set("align-self", "flex-start");

        VerticalLayout promptLayout = new VerticalLayout(togglePromptButton, rawPromptTextArea, sendButton);
        promptLayout.setWidthFull();
        promptLayout.setAlignItems(Alignment.CENTER);
        promptLayout.setSpacing(false);

        tokenCountTextArea = new TextArea("Token Usage");
        tokenCountTextArea.setWidthFull();
        tokenCountTextArea.setHeight("50px");
        tokenCountTextArea.setReadOnly(true);

        loadingIndicator = new ProgressBar();
        loadingIndicator.setIndeterminate(true);
        loadingIndicator.setVisible(false);
        loadingIndicator.setWidthFull();

        responseTextArea = new TextArea("LLM Response");
        responseTextArea.setWidthFull();
        responseTextArea.setHeight("800px");
        responseTextArea.setReadOnly(true);
        responseTextArea.getStyle()
                .set("white-space", "pre-wrap")
                .set("word-wrap", "break-word")
                .set("font-family", "monospace")
                .set("padding", "10px");

        rawPromptTextArea.addValueChangeListener(event -> updateTokenUsage());

        // ------------------------------
        // 5) Combine all components
        // ------------------------------
        this.setSpacing(false);
        this.add(
            indexGrid,
            searchLayout,
            contentsGrid,
            itemDetailsLayout,
            promptLayout,
            tokenCountTextArea,
            loadingIndicator,
            responseTextArea
        );

        // ------------------------------
        // 6) Setup logic
        // ------------------------------
        setupEventHandlers();
        listIndexes();
    }

    private void listIndexes() throws ExecutionException, InterruptedException {
        indexGrid.removeAllColumns();
        indexGrid.setItems(qdrantService.getIndexes());
        indexGrid.setSelectionMode(Grid.SelectionMode.SINGLE);
        indexGrid.addColumn(item -> item).setHeader("Index");
    }

    private void setupEventHandlers() {
        indexGrid.addSelectionListener(event ->
                event.getFirstSelectedItem().ifPresent(this::listContentsOfIndex)
        );
        contentsGrid.addSelectionListener(event ->
                event.getFirstSelectedItem().ifPresent(this::showItemDetails)
        );
    }

    private void listContentsOfIndex(String indexName) {
        try {
            LOG.info("Listing contents of index {}", indexName);
            this.selectedIndexName = indexName;

            // Clear old columns
            contentsGrid.removeAllColumns();

            // Fetch data
            List<IndexContent> contents = qdrantService.getIndexContents(indexName);
            contentsGrid.setItems(contents);

            // AREA column: narrower, no flex grow
            contentsGrid.addColumn(IndexContent::getAreaName)
                    .setHeader("Area")
                    .setWidth("200px")      // or whatever narrow width you want
                    .setFlexGrow(0)         // do not grow when there's extra space
                    .setAutoWidth(false);

            // PATH column: wider, grows to fill leftover space
            contentsGrid.addColumn(IndexContent::getFilePath)
                    .setHeader("Path")
                    .setAutoWidth(false)    // disable auto sizing
                    .setFlexGrow(2);        // allow it to expand more

            // ACTION column: minimal width for the button
            contentsGrid.addColumn(new ComponentRenderer<>(indexContent -> {
                        Button addButton = new Button("➕ Add to Prompt", event -> addToRawPrompt(indexContent));
                        addButton.getStyle().set("margin-right", "auto"); // Align to the left
                        return addButton;
                    }))
                    .setHeader("Action")
                    .setWidth("150px")  // Increased width to fit the button
                    .setFlexGrow(0)    // no expansion
                    .setTextAlign(ColumnTextAlign.START); // Align content to the left

        } catch (Exception e) {
            LOG.error("Error listing contents of index {}", indexName, e);
        }
    }

    private void searchContents() {
        if (selectedIndexName == null || searchField.getValue().trim().isEmpty()) {
            return;
        }

        String url = "http://localhost:8080/api/llm/vector-search?index=" + selectedIndexName + "&query=" + searchField.getValue().trim();

        try {
            ResponseEntity<List<IndexContent>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );
            contentsGrid.setItems(response.getBody());
        } catch (Exception e) {
            LOG.error("Error searching contents", e);
        }
    }

    private void showItemDetails(IndexContent indexContent) {
        LOG.info("Showing item details for {}", indexContent);
        itemDetailTextArea.setValue(indexContent.getContent());
    }

    private void addItemDetailsToRawPrompt() {
        String existingText = rawPromptTextArea.getValue();
        rawPromptTextArea.setValue(existingText + "\n\n" + itemDetailTextArea.getValue());
    }

    private void addToRawPrompt(IndexContent indexContent) {
        String existingText = rawPromptTextArea.getValue();
        rawPromptTextArea.setValue(existingText + "\n\n" + indexContent.getContent());
    }

    private void sendPromptToLLM() {
        loadingIndicator.setVisible(true);
        String prompt = rawPromptTextArea.getValue();

        // Send the prompt to the LLM service
        String url = "http://localhost:8080/api/llm/prompt";
        Map<String, String> payload = Map.of("message", prompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(payload, headers);

        new Thread(() -> {
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
                getUI().ifPresent(ui -> ui.access(() -> {
                    responseTextArea.setValue(response.getBody());
                    loadingIndicator.setVisible(false);
                }));
            } catch (Exception e) {
                getUI().ifPresent(ui -> ui.access(() -> {
                    responseTextArea.setValue("Error: " + e.getMessage());
                    loadingIndicator.setVisible(false);
                }));
            }
        }).start();
    }

    private void updateTokenUsage() {
        int tokenCount = rawPromptTextArea.getValue().split("\\s+").length;
        tokenCountTextArea.setValue("Tokens Used: " + tokenCount +
                " / Remaining: " + (MODEL_TOKEN_LIMIT - tokenCount));
    }

    private void toggleItemDetailsSize() {
        isItemDetailsExpanded = !isItemDetailsExpanded;
        itemDetailTextArea.setHeight(isItemDetailsExpanded ? "1200px" : "200px");
        toggleItemDetailsButton.setText(isItemDetailsExpanded ? "🔼 Collapse Content" : "🔽 Expand Content");
    }

    private void togglePromptSize() {
        isPromptExpanded = !isPromptExpanded;
        rawPromptTextArea.setHeight(isPromptExpanded ? "1200px" : "200px");
        togglePromptButton.setText(isPromptExpanded ? "🔼 Collapse Prompt" : "🔽 Expand Prompt");
    }
}
