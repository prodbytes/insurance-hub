package ih.audit.util;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Pattern;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import ih.audit.view.MessagesView;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Captures every message from every Kafka topic into an in-memory list that
 * {@link MessagesView} renders. Subscribes with a match-all topic pattern
 * under a unique consumer group reading from the earliest offset, so each app
 * start replays the full broker history and nothing is ever evicted — the dev
 * broker is throwaway and starts empty on every stack run, so the volume
 * stays dev-scale.
 */
@ApplicationScoped
public class KafkaMessageStore {

    private static final Logger LOG = Logger.getLogger(KafkaMessageStore.class);

    public record CapturedMessage(
            String topic,
            int partition,
            long offset,
            Instant timestamp,
            String key,
            String value) {
    }

    @ConfigProperty(name = "kafka.bootstrap.servers", defaultValue = "localhost:9092")
    String bootstrapServers;

    private final ConcurrentLinkedDeque<CapturedMessage> messages = new ConcurrentLinkedDeque<>();

    private volatile boolean running;
    private KafkaConsumer<String, String> consumer;
    private Thread reader;

    void onStart(@Observes StartupEvent event) {
        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // Unique group + earliest: always replay everything, never resume from
        // a committed position of a previous run.
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "ih-audit-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        // Refresh metadata often so topics created after startup (e.g. by
        // Debezium or ih-vdn) are picked up by the pattern subscription fast.
        props.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, "5000");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        consumer = new KafkaConsumer<>(props);
        running = true;
        reader = new Thread(this::readLoop, "ih-audit-kafka-reader");
        reader.setDaemon(true);
        reader.start();
    }

    void onStop(@Observes ShutdownEvent event) {
        running = false;
        if (consumer != null) {
            consumer.wakeup();
        }
        if (reader != null) {
            try {
                reader.join(Duration.ofSeconds(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void readLoop() {
        try (var c = consumer) {
            c.subscribe(Pattern.compile(".*"));
            LOG.infof("Capturing all Kafka topics from %s", bootstrapServers);
            while (running) {
                for (var r : c.poll(Duration.ofSeconds(1))) {
                    messages.addFirst(new CapturedMessage(
                            r.topic(), r.partition(), r.offset(),
                            Instant.ofEpochMilli(r.timestamp()), r.key(), r.value()));
                }
            }
        } catch (WakeupException e) {
            if (running) {
                throw e;
            }
        } catch (Exception e) {
            LOG.error("Kafka message capture stopped unexpectedly", e);
        }
    }

    /** Snapshot of all captured messages, newest first. */
    public List<CapturedMessage> snapshot() {
        return List.copyOf(messages);
    }
}
