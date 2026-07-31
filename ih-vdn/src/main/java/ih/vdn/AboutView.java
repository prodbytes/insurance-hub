package ih.vdn;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * About page describing the purpose of this demo application.
 */
@PageTitle("InsuranceHub | About")
@Route("about")
public class AboutView extends VerticalLayout {

    public AboutView() {
        addClassName("hero");
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        var inner = new VerticalLayout(buildText());
        inner.addClassName("hero__inner");
        inner.setWidthFull();
        add(inner);
    }

    private VerticalLayout buildText() {
        var eyebrow = new Span("ABOUT THIS DEMO");
        eyebrow.addClassName("hero__eyebrow");

        var title = new H1("A demonstration of enterprise application development.");
        title.addClassName("hero__title");

        var intro = new Paragraph(
                "InsuranceHub is not a real insurance product. It is a demo application built to showcase "
                        + "how modern enterprise applications are designed and delivered.");
        intro.addClassName("hero__subtitle");

        var devParagraph = new Paragraph(
                "It demonstrates enterprise application development practices, combining a Quarkus backend "
                        + "with a Vaadin user interface to deliver a fast, type-safe, full-stack Java experience.");
        devParagraph.addClassName("hero__subtitle");

        var decisionParagraph = new Paragraph(
                "It also illustrates decision management: the rules that drive eligibility, pricing, and coverage "
                        + "are modeled as explicit, governable business decisions rather than scattered throughout the code.");
        decisionParagraph.addClassName("hero__subtitle");

        var back = new Button("Back to home");
        back.addThemeVariants(ButtonVariant.LUMO_LARGE, ButtonVariant.LUMO_TERTIARY);
        back.addClickListener(e -> back.getUI().ifPresent(ui -> ui.navigate(MainView.class)));

        var text = new VerticalLayout(eyebrow, title, intro, devParagraph, decisionParagraph, back);
        text.addClassName("hero__text");
        text.setPadding(false);
        text.setSpacing(false);
        return text;
    }
}
