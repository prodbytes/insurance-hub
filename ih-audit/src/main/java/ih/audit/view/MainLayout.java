package ih.audit.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.RouterLink;

/**
 * Shell with the top navigation switching between the all-messages reader
 * and the x-audit events list.
 */
public class MainLayout extends AppLayout implements AfterNavigationObserver {

    private final Tabs nav = new Tabs();
    private final Tab messages = navTab("All messages", MessagesView.class);
    private final Tab audits = navTab("Audit events", AuditEventsView.class);

    public MainLayout() {
        // Audit events first: it is the app's default page.
        nav.add(audits, messages);
        addToNavbar(nav);
    }

    private static Tab navTab(String label, Class<? extends Component> view) {
        return new Tab(new RouterLink(label, view));
    }

    /** Keeps the selected tab in sync with the shown view (deep links too). */
    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        nav.setSelectedTab(getContent() instanceof AuditEventsView ? audits : messages);
    }
}
