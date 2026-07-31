package ih.audit.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;

import ih.audit.core.AnnotatedTopicListener;
import ih.audit.util.KafkaMessageStore;
import ih.audit.util.KafkaMessageStore.CapturedMessage;
import jakarta.inject.Inject;

/**
 * Audit events: lists only the audit results the annotated-topic listener
 * publishes under the x-audit* topics, newest first.
 */
@PageTitle("InsuranceHub | Audit events")
@Route(value = "", layout = MainLayout.class)
@RouteAlias(value = "audit", layout = MainLayout.class)
public class AuditEventsView extends MessageGridView {

    // The base constructor calls addExtraColumns() before this class's
    // fields would be initialized, so the parser must be static.
    private static final ObjectMapper RESULT_PARSER = new ObjectMapper();

    @Inject
    public AuditEventsView(KafkaMessageStore store, ObjectMapper mapper) {
        super(store, mapper, "Audit events");
    }

    /** AuditResult column: ✅ OK, ⚠️ WARNING, ❌ ERROR (full text as tooltip). */
    @Override
    protected void addExtraColumns(Grid<CapturedMessage> grid) {
        grid.addColumn(AuditEventsView::resultEmoji)
                .setHeader("Result").setWidth("90px").setFlexGrow(0)
                .setTooltipGenerator(AuditEventsView::auditResult);
    }

    private static String resultEmoji(CapturedMessage message) {
        return switch (auditResult(message)) {
            case "OK" -> "✅";
            case "WARNING" -> "⚠️";
            case "ERROR" -> "❌";
            default -> "";
        };
    }

    /** The AuditResult field of the event's evaluation result, or empty. */
    private static String auditResult(CapturedMessage message) {
        if (message.value() == null) {
            return "";
        }
        try {
            return RESULT_PARSER.readTree(message.value())
                    .path("result").path("AuditResult").asText("");
        } catch (Exception notJson) {
            return "";
        }
    }

    @Override
    protected boolean include(CapturedMessage message) {
        return message.topic().startsWith(AnnotatedTopicListener.AUDIT_TOPIC_PREFIX);
    }

    @Override
    protected String subtitle(int count) {
        return "%d audit events, %s* topics, newest first."
                .formatted(count, AnnotatedTopicListener.AUDIT_TOPIC_PREFIX);
    }
}
