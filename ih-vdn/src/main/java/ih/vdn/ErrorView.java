package ih.vdn;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

/**
 * Generic error page shown when a quote cannot be calculated (for example, the
 * Decision Control engine is unreachable). Offers a link to start over.
 */
@PageTitle("InsuranceHub | Something went wrong")
@Route("error")
public class ErrorView extends VerticalLayout {

    public ErrorView() {
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
        var eyebrow = new Span("SOMETHING WENT WRONG");
        eyebrow.addClassName("hero__eyebrow");

        var title = new H1("We couldn't calculate your quote.");
        title.addClassName("hero__title");

        var message = new Paragraph(
                "An unexpected error occurred while contacting the pricing service. "
                        + "Your details were not saved. Please try again in a moment.");
        message.addClassName("hero__subtitle");

        var retry = new RouterLink("", QuoteView.class);
        var retryButton = new Button("Try again");
        retryButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        retry.add(retryButton);

        var text = new VerticalLayout(eyebrow, title, message, retry);
        text.addClassName("hero__text");
        text.setPadding(false);
        text.setSpacing(false);
        return text;
    }
}
