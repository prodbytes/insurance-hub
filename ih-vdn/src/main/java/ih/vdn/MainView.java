package ih.vdn;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * Landing page for the vehicle insurance app: a simple hero section.
 */
@PageTitle("InsuranceHub | Vehicle Insurance")
@Route("")
public class MainView extends VerticalLayout {

    public MainView() {
        addClassName("hero");
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        add(new HorizontalLayout(buildText(), buildImage()) {{
            addClassName("hero__inner");
            setWidthFull();
        }});
    }

    private VerticalLayout buildText() {
        var eyebrow = new Span("VEHICLE INSURANCE");
        eyebrow.addClassName("hero__eyebrow");

        var title = new H1("Drive protected, every mile of the way.");
        title.addClassName("hero__title");

        var subtitle = new Paragraph(
                "Comprehensive coverage built for your car, your budget, and your peace of mind. "
                        + "Get a personalized quote in minutes.");
        subtitle.addClassName("hero__subtitle");

        var quote = new Button("Get a quote");
        quote.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        quote.addClassName("hero__cta");
        quote.addClickListener(e -> quote.getUI().ifPresent(ui -> ui.navigate(QuoteView.class)));

        var learn = new Button("Explore Aletyx");
        learn.addThemeVariants(ButtonVariant.LUMO_LARGE, ButtonVariant.LUMO_TERTIARY);
        learn.addClickListener(e -> learn.getUI().ifPresent(ui -> ui.getPage().open("https://aletyx.ai", "_blank")));

        var actions = new HorizontalLayout(quote, learn);
        actions.addClassName("hero__actions");

        var text = new VerticalLayout(eyebrow, title, subtitle, actions);
        text.addClassName("hero__text");
        text.setPadding(false);
        text.setSpacing(false);
        return text;
    }

    private Image buildImage() {
        var hero = new Image("/images/hero-vehicles.png", "Car protected by an insurance shield");
        hero.addClassName("hero__image");
        return hero;
    }
}
