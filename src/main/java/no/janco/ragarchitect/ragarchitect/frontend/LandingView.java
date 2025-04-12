package no.janco.ragarchitect.ragarchitect.frontend;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route(value = "", layout = MainLayout.class)  // Root URL "/"
public class LandingView extends VerticalLayout {

    public LandingView() {
        // Set full height and center content
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        // Title
        H1 welcomeMessage = new H1("Welcome to RAG Architect!");
        welcomeMessage.getStyle().set("color", "white");

        // Description
        Paragraph description = new Paragraph("Choose an option below to get started.");
        description.getStyle().set("color", "#cccccc");

        // Navigation Buttons
        Button chatButton = new Button("Go to Chat", event -> getUI().ifPresent(ui -> ui.navigate("chat")));
        Button architectButton = new Button("Go to Architect", event -> getUI().ifPresent(ui -> ui.navigate("architect")));

        // Style buttons
        chatButton.getStyle()
                .set("background-color", "#007bff")
                .set("color", "white")
                .set("font-size", "18px")
                .set("padding", "10px 20px")
                .set("border-radius", "8px");

        architectButton.getStyle()
                .set("background-color", "#28a745")
                .set("color", "white")
                .set("font-size", "18px")
                .set("padding", "10px 20px")
                .set("border-radius", "8px");

        // Add components to the layout
        add(welcomeMessage, description, chatButton, architectButton);

        // Background styling
        getStyle().set("background-color", "#1e1e1e");
    }
}
