package ih.vdn;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

import ih.domain.QuoteRequest;
import ih.domain.QuoteResponse;
import ih.service.QuoteService;

/**
 * Presents a computed {@link QuoteResponse}: the estimated vehicle value, the
 * risk rate, and the resulting yearly premium. The response id is taken
 * from the URL (e.g. {@code /app/quote/result/5}).
 */
@PageTitle("InsuranceHub | Your quote")
@Route("quote/result")
public class QuoteResultView extends VerticalLayout implements HasUrlParameter<Long> {

    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(Locale.US);

    static {
        MONEY.setMaximumFractionDigits(0);
    }

    private final QuoteService quoteService;
    private final VerticalLayout inner = new VerticalLayout();

    public QuoteResultView(QuoteService quoteService) {
        this.quoteService = quoteService;
        addClassName("hero");
        addClassName("hero--form");
        setWidthFull();
        setPadding(false);
        setSpacing(false);

        inner.addClassName("hero__inner");
        inner.setWidthFull();
        add(inner);
    }

    @Override
    public void setParameter(BeforeEvent event, Long id) {
        var response = quoteService.findResponse(id);
        inner.removeAll();
        inner.add(response == null ? buildNotFound() : buildContent(response));
    }

    private VerticalLayout buildContent(QuoteResponse response) {
        var eyebrow = new Span("YOUR QUOTE");
        eyebrow.addClassName("hero__eyebrow");

        var title = new H1("Here's your estimated premium.");
        title.addClassName("hero__title");

        var yearlyPremium = response.getYearlyPremium();
        var amount = new Span(MONEY.format(yearlyPremium));
        amount.addClassName("quote-result__amount");
        var period = new Span(" / year");
        period.addClassName("quote-result__period");
        var price = new HorizontalLayout(amount, period);
        price.addClassName("quote-result__price");
        price.setAlignItems(Alignment.BASELINE);

        var monthly = yearlyPremium.divide(BigDecimal.valueOf(12), 0, RoundingMode.HALF_UP);
        var annual = new Paragraph(
                "That's about " + MONEY.format(monthly) + " per month, billed annually with no hidden fees.");
        annual.addClassName("hero__subtitle");

        var howTitle = new H2("How we calculated this");
        howTitle.addClassName("hero__section-title");

        var explanation = new Paragraph(
                "This estimate is based on your vehicle's value and the risk profile from the details you provided:");
        explanation.addClassName("hero__subtitle");

        var factors = new VerticalLayout(
                factor("Estimated vehicle value", vehicleDescription(response.getRequest()),
                        MONEY.format(response.getEstimatedVehiclePrice())),
                factor("Risk rate", "Higher risk raises your premium.",
                        formatRiskRate(response.getRiskRate())),
                factor("Yearly premium", "Your estimated annual premium.",
                        MONEY.format(yearlyPremium) + " / year"));
        factors.addClassName("quote-result__factors");
        factors.setPadding(false);
        factors.setSpacing(false);

        var disclaimer = new Paragraph(
                "This is a demo application — the quote above is illustrative and not a real insurance offer.");
        disclaimer.addClassName("quote-result__disclaimer");

        var startButton = new Button("Accept quote and proceed");
        startButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        startButton.addClickListener(e -> startButton.getUI().ifPresent(ui -> ui.navigate(ThankYouView.class)));

        var newQuote = new RouterLink("", QuoteView.class);
        var newQuoteButton = new Button("New quote");
        newQuoteButton.addThemeVariants(ButtonVariant.LUMO_LARGE, ButtonVariant.LUMO_TERTIARY);
        newQuote.add(newQuoteButton);

        var actions = new HorizontalLayout(startButton, newQuote);
        actions.addClassName("hero__actions");

        var text = new VerticalLayout(eyebrow, title, price, annual,
                howTitle, explanation, factors, disclaimer, actions);
        text.addClassName("hero__text");
        text.setPadding(false);
        text.setSpacing(false);
        return text;
    }

    private VerticalLayout buildNotFound() {
        var eyebrow = new Span("YOUR QUOTE");
        eyebrow.addClassName("hero__eyebrow");

        var title = new H1("We couldn't find that quote.");
        title.addClassName("hero__title");

        var message = new Paragraph("It may have expired or never existed. Start a new quote to get an estimate.");
        message.addClassName("hero__subtitle");

        var newQuote = new RouterLink("", QuoteView.class);
        var newQuoteButton = new Button("Start a new quote");
        newQuoteButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        newQuote.add(newQuoteButton);

        var text = new VerticalLayout(eyebrow, title, message, newQuote);
        text.addClassName("hero__text");
        text.setPadding(false);
        text.setSpacing(false);
        return text;
    }

    private String vehicleDescription(QuoteRequest request) {
        if (request == null) {
            return "Based on the vehicle you described.";
        }
        var parts = new StringBuilder();
        if (request.getVehicleYear() != null) {
            parts.append(request.getVehicleYear()).append(' ');
        }
        if (request.getMake() != null) {
            parts.append(request.getMake()).append(' ');
        }
        if (request.getModel() != null) {
            parts.append(request.getModel());
        }
        var text = parts.toString().trim();
        return text.isEmpty() ? "Based on the vehicle you described." : text;
    }

    private String formatRiskRate(BigDecimal riskRate) {
        return riskRate == null ? "—"
                : riskRate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private HorizontalLayout factor(String name, String detail, String amount) {
        var label = new Span(name);
        label.addClassName("quote-result__factor-name");
        var description = new Span(detail);
        description.addClassName("quote-result__factor-detail");

        var labels = new VerticalLayout(label, description);
        labels.setPadding(false);
        labels.setSpacing(false);

        var value = new Span(amount);
        value.addClassName("quote-result__factor-value");

        var row = new HorizontalLayout(labels, value);
        row.addClassName("quote-result__factor");
        row.setWidthFull();
        row.setJustifyContentMode(JustifyContentMode.BETWEEN);
        row.setAlignItems(Alignment.CENTER);
        return row;
    }
}
