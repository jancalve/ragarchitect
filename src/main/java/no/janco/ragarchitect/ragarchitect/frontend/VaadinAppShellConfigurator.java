package no.janco.ragarchitect.ragarchitect.frontend;

import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

@Push
@Theme(variant = Lumo.DARK)
@JsModule("@vaadin/vaadin-lumo-styles/presets/compact.js")
public class VaadinAppShellConfigurator implements AppShellConfigurator {
}
