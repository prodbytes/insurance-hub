package ih.vdn;

import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HighlightConditions;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.router.RouterLink;

/**
 * Shared layout for the admin section: a top navbar with the admin pages
 * (Map, Settings) and a content area filling the rest of the viewport.
 */
public class AdminLayout extends VerticalLayout implements RouterLayout {

    private final Div content = new Div();

    public AdminLayout() {
        addClassName("admin");
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        add(buildNavbar());

        content.addClassName("admin__content");
        content.setSizeFull();
        addAndExpand(content);
    }

    private HorizontalLayout buildNavbar() {
        var brand = new Span("InsuranceHub Admin");
        brand.addClassName("admin__brand");

        var mapLink = new RouterLink("Map", AdminView.class);
        mapLink.setHighlightCondition(HighlightConditions.sameLocation());
        var settingsLink = new RouterLink("Settings", AdminSettingsView.class);

        var nav = new HorizontalLayout(mapLink, settingsLink);
        nav.addClassName("admin__nav");
        nav.setSpacing(false);

        var navbar = new HorizontalLayout(brand, nav);
        navbar.addClassName("admin__navbar");
        navbar.setWidthFull();
        navbar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        navbar.setAlignItems(Alignment.CENTER);
        return navbar;
    }

    @Override
    public void showRouterLayoutContent(HasElement newContent) {
        content.getElement().removeAllChildren();
        if (newContent != null) {
            content.getElement().appendChild(newContent.getElement());
        }
    }
}
