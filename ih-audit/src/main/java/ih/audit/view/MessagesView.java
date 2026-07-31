package ih.audit.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import ih.audit.util.KafkaMessageStore;
import ih.audit.util.KafkaMessageStore.CapturedMessage;
import jakarta.inject.Inject;

/**
 * Message reader: lists every message captured from Kafka (all topics),
 * newest first.
 */
@PageTitle("InsuranceHub | Audit")
@Route(value = "messages", layout = MainLayout.class)
public class MessagesView extends MessageGridView {

    @Inject
    public MessagesView(KafkaMessageStore store, ObjectMapper mapper) {
        super(store, mapper, "Kafka messages");
    }

    @Override
    protected boolean include(CapturedMessage message) {
        return true;
    }

    @Override
    protected String subtitle(int count) {
        return "%d messages captured, all topics, newest first.".formatted(count);
    }
}
