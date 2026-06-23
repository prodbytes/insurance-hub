package ih.vdn;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

/**
 * Confirmation page shown after the user accepts a quote.
 */
@PageTitle("InsuranceHub | Thank you")
@Route("quote/thank-you")
public class ThankYouView extends VerticalLayout {

    public ThankYouView() {
        addClassName("hero");
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        var inner = new VerticalLayout(buildContent());
        inner.addClassName("hero__inner");
        inner.setWidthFull();
        add(inner);
    }

    private VerticalLayout buildContent() {
        var eyebrow = new Span("ALL SET");
        eyebrow.addClassName("hero__eyebrow");

        var title = new H1("Thank you!");
        title.addClassName("hero__title");

        var message = new Paragraph(
                "We've received your details — one of our agents will reach out shortly.");
        message.addClassName("hero__subtitle");

        var invite = new Paragraph(
                "This quote was priced in real time by Aletyx. Explore how our decisioning "
                        + "platform powers experiences like this one.");
        invite.addClassName("hero__subtitle");

        var aletyx = new Anchor("https://aletyx.com", "");
        aletyx.setTarget("_blank");
        var aletyxButton = new Button("Explore Aletyx");
        aletyxButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        aletyx.add(aletyxButton);

        var home = new RouterLink("", MainView.class);
        var homeButton = new Button("Back to home");
        homeButton.addThemeVariants(ButtonVariant.LUMO_LARGE, ButtonVariant.LUMO_TERTIARY);
        home.add(homeButton);

        var actions = new HorizontalLayout(aletyx, home);
        actions.addClassName("hero__actions");

        var text = new VerticalLayout(eyebrow, title, message, invite, actions);
        text.addClassName("hero__text");
        text.setPadding(false);
        text.setSpacing(false);
        return text;
    }
}
