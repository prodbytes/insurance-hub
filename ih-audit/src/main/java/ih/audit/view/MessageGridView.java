package ih.audit.view;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.shared.Registration;

import ih.audit.util.KafkaMessageStore;
import ih.audit.util.KafkaMessageStore.CapturedMessage;

/**
 * Shared message table: a newest-first grid over a filtered snapshot of the
 * captured Kafka messages, refreshing automatically via UI polling. Columns
 * have fixed widths with single-line ellipsis cells so the table always fits
 * the viewport width; click a row to inspect the full message. Subclasses
 * choose the title and which messages their page includes.
 */
abstract class MessageGridView extends VerticalLayout {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final KafkaMessageStore store;
    private final ObjectMapper mapper;
    private final Grid<CapturedMessage> grid = new Grid<>();
    private final Paragraph subtitle = new Paragraph();
    private Registration pollListener;

    MessageGridView(KafkaMessageStore store, ObjectMapper mapper, String title) {
        this.store = store;
        this.mapper = mapper;
        setSizeFull();

        var heading = new H1(title);
        heading.addClassName("messages__title");
        subtitle.addClassName("messages__subtitle");

        grid.addColumn(m -> TIMESTAMP.format(m.timestamp()))
                .setHeader("Timestamp").setWidth("185px").setFlexGrow(0);
        grid.addColumn(CapturedMessage::topic)
                .setHeader("Topic").setWidth("290px").setFlexGrow(0)
                .setTooltipGenerator(CapturedMessage::topic);
        grid.addColumn(m -> m.partition() + "/" + m.offset())
                .setHeader("Part/Offset").setWidth("110px").setFlexGrow(0);
        addExtraColumns(grid);
        grid.addColumn(CapturedMessage::key)
                .setHeader("Key").setWidth("220px").setFlexGrow(0)
                .setTooltipGenerator(CapturedMessage::key);
        grid.addColumn(CapturedMessage::value)
                .setHeader("Value").setFlexGrow(1)
                .setTooltipGenerator(CapturedMessage::value);
        var copyColumn = grid.addComponentColumn(m -> {
            var copy = new Button(VaadinIcon.COPY.create(), e -> copyValue(m));
            copy.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
            copy.setTooltipText("Copy value");
            copy.setAriaLabel("Copy value");
            return copy;
        }).setHeader("").setWidth("70px").setFlexGrow(0);
        grid.addClassName("messages__grid");
        // The copy column's own clicks must not also open the details dialog.
        grid.addItemClickListener(e -> {
            if (!copyColumn.equals(e.getColumn())) {
                openDetails(e.getItem());
            }
        });
        grid.setSizeFull();

        add(heading, subtitle, grid);
        expand(grid);
        refresh();
    }

    /**
     * Hook for page-specific columns, added between the standard Part/Offset
     * and Key columns. Called from the constructor, before subclass fields
     * are initialized.
     */
    protected void addExtraColumns(Grid<CapturedMessage> grid) {
    }

    /** Whether the message belongs on this page. */
    protected abstract boolean include(CapturedMessage message);

    /** The subtitle line for the current row count. */
    protected abstract String subtitle(int count);

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        attachEvent.getUI().setPollInterval(2000);
        pollListener = attachEvent.getUI().addPollListener(e -> refresh());
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (pollListener != null) {
            pollListener.remove();
            pollListener = null;
        }
        detachEvent.getUI().setPollInterval(-1);
        super.onDetach(detachEvent);
    }

    private void refresh() {
        var snapshot = store.snapshot().stream().filter(this::include).toList();
        grid.setItems(snapshot);
        subtitle.setText(subtitle(snapshot.size()));
    }

    private void openDetails(CapturedMessage m) {
        var dialog = new Dialog();
        dialog.setHeaderTitle("%s  %d/%d  %s".formatted(
                m.topic(), m.partition(), m.offset(), TIMESTAMP.format(m.timestamp())));
        dialog.setWidth("70%");

        var key = new Pre("Key: " + prettyJson(m.key()));
        key.addClassName("messages__detail");
        var value = new Pre(prettyJson(m.value()));
        value.addClassName("messages__detail");

        var copy = new Button("Copy value", VaadinIcon.COPY.create(), e -> copyValue(m));
        copy.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        dialog.getHeader().add(copy);

        dialog.add(key, value);
        dialog.open();
    }

    /** Copies the raw (unformatted) record value to the clipboard. */
    private void copyValue(CapturedMessage m) {
        getUI().ifPresent(ui -> ui.getPage()
                .executeJs("navigator.clipboard.writeText($0)", m.value() == null ? "" : m.value()));
        Notification.show("Value copied", 1500, Notification.Position.BOTTOM_END);
    }

    /** Pretty-prints JSON payloads; anything unparseable is shown as-is. */
    private String prettyJson(String raw) {
        if (raw == null) {
            return "(null)";
        }
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(raw));
        } catch (Exception notJson) {
            return raw;
        }
    }
}
