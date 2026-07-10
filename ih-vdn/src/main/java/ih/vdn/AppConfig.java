package ih.vdn;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.theme.lumo.Lumo;

@StyleSheet(Lumo.STYLESHEET)
@StyleSheet("styles.css")
public class AppConfig implements AppShellConfigurator {

    @Override
    public void configurePage(AppShellSettings settings) {
        // Absolute path: views served under /app/* would otherwise resolve the
        // icon relative to the page URL and 404.
        settings.addFavIcon("icon", "/favicon.svg", "32x32");
    }
}
