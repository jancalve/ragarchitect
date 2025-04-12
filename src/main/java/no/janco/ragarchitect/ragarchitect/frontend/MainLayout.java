package no.janco.ragarchitect.ragarchitect.frontend;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;

public class MainLayout extends VerticalLayout implements RouterLayout, AfterNavigationObserver {
    private final Tabs tabs;

    public MainLayout() {
        tabs = new Tabs();
        tabs.add(new Tab(new RouterLink("Landing", LandingView.class)));
        tabs.add(new Tab(new RouterLink("Chat", ChatView.class)));
        tabs.add(new Tab(new RouterLink("Architect", ArchitectView.class)));
        add(tabs);
    }
    
    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        String path = event.getLocation().getPath();
        if (path.isEmpty() || path.equals("/")) {
            tabs.setSelectedIndex(0); // Landing tab
        } else if (path.equals("/chat")) {
            tabs.setSelectedIndex(1); // Chat tab
        } else if (path.equals("/architect")) {
            tabs.setSelectedIndex(2); // Architect tab
        }
    }
}

