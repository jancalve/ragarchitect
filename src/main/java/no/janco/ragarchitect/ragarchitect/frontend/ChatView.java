package no.janco.ragarchitect.ragarchitect.frontend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.shared.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Route("chat")
@CssImport("./styles/chat-styles.css")
public class ChatView extends VerticalLayout implements RouterLayout {

    private VerticalLayout conversationLayout;
    private TextField inputField;
    private Button sendButton;
    private ProgressBar loadingIndicator;
    private final ExecutorService executorService;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Logger LOG = LoggerFactory.getLogger(ChatView.class);
    private static final int MAX_MESSAGES = 50; // Limit number of messages to prevent slowdown

    @Autowired
    public ChatView(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.executorService = Executors.newSingleThreadExecutor();

        addClassName("chat-view");
        setSizeFull();
        setPadding(true);
        setSpacing(false);

        setupNavigationTabs();
        setupConversationLayout();
        setupLoadingIndicator();
        setupInputArea();
    }

    private void setupNavigationTabs() {
        Tabs tabs = new Tabs();
        tabs.add(new Tab(new RouterLink("Chat", ChatView.class)));
        tabs.add(new Tab(new RouterLink("Architect", ArchitectView.class)));
        add(tabs);
    }

    private void setupConversationLayout() {
        conversationLayout = new VerticalLayout();
        conversationLayout.addClassName("conversation-layout");
        conversationLayout.setSizeFull();
        add(conversationLayout);
        setFlexGrow(1, conversationLayout);
    }

    private void setupLoadingIndicator() {
        loadingIndicator = new ProgressBar();
        loadingIndicator.setIndeterminate(true);
        loadingIndicator.setVisible(false);
        loadingIndicator.addClassName("loading-indicator");
        add(loadingIndicator);
    }

    private void setupInputArea() {
        inputField = new TextField();
        inputField.addClassName("message-input");
        inputField.setPlaceholder("Type your message...");

        sendButton = new Button("Send");
        sendButton.addClassName("send-button");
        sendButton.addClickListener(event -> sendMessage());

        inputField.addKeyPressListener(com.vaadin.flow.component.Key.ENTER, event -> sendMessage());

        VerticalLayout inputLayout = new VerticalLayout(inputField, sendButton);
        inputLayout.addClassName("input-layout");
        add(new Div(inputLayout));
    }

    private void sendMessage() {
        String message = inputField.getValue().trim();
        if (message.isEmpty()) {
            return;
        }

        addUserMessage(message);
        inputField.clear();
        loadingIndicator.setVisible(true);

        String url = "http://localhost:8080/api/llm/chat";
        Map<String, String> payload = Map.of("message", message);
        
        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

            executorService.submit(() -> {
                try {
                    ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
                    if (response.getStatusCode() == HttpStatus.OK) {
                        getUI().ifPresent(ui -> ui.access(() -> {
                            addAssistantMessage(response.getBody());
                            loadingIndicator.setVisible(false);
                            limitMessages();
                        }));
                    } else {
                        handleError("Error: " + response.getStatusCode());
                    }
                } catch (Exception e) {
                    handleError("Error: " + e.getMessage());
                }
            });
        } catch (JsonProcessingException e) {
            handleError("Error processing request.");
        }
    }

    private void handleError(String errorMessage) {
        getUI().ifPresent(ui -> ui.access(() -> {
            addAssistantMessage(errorMessage);
            loadingIndicator.setVisible(false);
        }));
    }

    private void addUserMessage(String message) {
        Div userMessage = new Div();
        userMessage.addClassName("user-message");
        userMessage.add(new Span("You: " + message));
        conversationLayout.add(userMessage);
        scrollToBottom();
    }

    private void addAssistantMessage(String message) {
        Div assistantMessage = new Div();
        assistantMessage.addClassName("assistant-message");

        String formattedMessage = formatMessage(message);
        assistantMessage.getElement().setProperty("innerHTML", "AI: " + formattedMessage);

        conversationLayout.add(assistantMessage);
        scrollToBottom();
    }

    private String formatMessage(String message) {
        return message
                .replace("\n", "<br>")
                .replaceAll("(?s)```([a-z]*)\\n(.*?)```",
                        "<pre class='code-block'><code>$2</code></pre>");
    }

    private void limitMessages() {
        while (conversationLayout.getComponentCount() > MAX_MESSAGES) {
            conversationLayout.remove(conversationLayout.getComponentAt(0));
        }
    }

    private void scrollToBottom() {
        getUI().ifPresent(ui -> ui.access(() ->
            conversationLayout.getElement().executeJs("this.scrollTop = this.scrollHeight")
        ));
    }

    @Override
    protected void onDetach(DetachEvent event) {
        super.onDetach(event);
        executorService.shutdown();
    }
}
