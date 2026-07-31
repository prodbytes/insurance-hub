package ih.audit.core;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import ih.audit.core.AnnotatedModelStartupAuditor.AnnotatedModel;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Consumes every Kafka topic except the x-audit* family and matches each
 * message against the @-annotated decision models the startup audit
 * discovered: a model matches when the topic name ends with the annotation's
 * text after the "@" (e.g. a topic ending in "carquote_response" matches
 * "@carquote_response").
 *
 * A matched message is audited: the model's input parameters are filled from
 * the message's "after" section (fields matched by name ignoring case and
 * special characters — a message without an "after" section is only warned
 * about), the model is
 * evaluated on Decision Control, and the outcome is published to
 * x-audit.&lt;source topic&gt; — a prefix this listener never consumes, so
 * audit results cannot trigger further audits.
 *
 * Same consumption pattern as {@link ih.audit.util.KafkaMessageStore}: a
 * daemon reader thread under a unique consumer group reading from the
 * earliest offset, so each app start replays the full (dev-scale, throwaway)
 * broker history. Subscribing is deferred until the startup audit finished —
 * the replay then covers everything published while the models were still
 * being discovered.
 */
@ApplicationScoped
public class AnnotatedTopicListener {

    private static final Logger LOG = Logger.getLogger(AnnotatedTopicListener.class);

    /**
     * Prefix of the topics audit results are published under, and which this
     * listener therefore never consumes.
     */
    public static final String AUDIT_TOPIC_PREFIX = "x-audit";

    private static final Pattern TOPICS = Pattern.compile("(?!" + AUDIT_TOPIC_PREFIX + ").*");

    @ConfigProperty(name = "kafka.bootstrap.servers", defaultValue = "localhost:9092")
    String bootstrapServers;

    private final AnnotatedModelStartupAuditor auditor;
    private final ObjectMapper objectMapper;

    private volatile boolean running;
    private volatile KafkaConsumer<String, String> consumer;
    private Thread reader;

    public AnnotatedTopicListener(AnnotatedModelStartupAuditor auditor, ObjectMapper objectMapper) {
        this.auditor = auditor;
        this.objectMapper = objectMapper;
    }

    void onStart(@Observes StartupEvent event) {
        running = true;
        reader = new Thread(this::readLoop, "ih-audit-annotated-topic-listener");
        reader.setDaemon(true);
        reader.start();
    }

    void onStop(@Observes ShutdownEvent event) {
        running = false;
        if (reader != null) {
            // Unblocks both the wait for the startup audit and the poll loop.
            reader.interrupt();
        }
        var c = consumer;
        if (c != null) {
            c.wakeup();
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
        final List<AnnotatedModel> models;
        try {
            models = auditor.awaitAnnotatedModels();
        } catch (InterruptedException e) {
            return;
        }
        if (models.isEmpty()) {
            LOG.info("No @-annotated models discovered; annotated-topic listener not started");
            return;
        }

        var consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "ih-audit-annotated-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        consumerProps.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, "5000");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        var producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (var producer = new KafkaProducer<String, String>(producerProps);
                var c = new KafkaConsumer<String, String>(consumerProps)) {
            consumer = c;
            if (!running) {
                return;
            }
            c.subscribe(TOPICS);
            LOG.infof("Matching Kafka topics from %s against %d @-annotated model(s)",
                    bootstrapServers, models.size());
            while (running) {
                for (var r : c.poll(Duration.ofSeconds(1))) {
                    if (!r.topic().startsWith(AUDIT_TOPIC_PREFIX)) {
                        match(models, r, producer);
                    }
                }
            }
        } catch (WakeupException | InterruptException e) {
            if (running) {
                throw e;
            }
        } catch (Exception e) {
            LOG.error("Annotated-topic listener stopped unexpectedly", e);
        }
    }

    private void match(List<AnnotatedModel> models, ConsumerRecord<String, String> r,
            KafkaProducer<String, String> producer) {
        for (var model : models) {
            for (var annotation : model.annotations()) {
                var suffix = annotation.substring(1);
                if (!suffix.isEmpty() && r.topic().endsWith(suffix)) {
                    audit(model, annotation, r, producer);
                }
            }
        }
    }

    /**
     * Audits one matched message: fills the model's inputs from the message's
     * "after" section, evaluates the model and publishes the outcome under
     * the x-audit prefix.
     */
    private void audit(AnnotatedModel model, String annotation, ConsumerRecord<String, String> r,
            KafkaProducer<String, String> producer) {
        LOG.debugf("Topic '%s' matches model '%s' (annotation '%s'): key=%s value=%s",
                r.topic(), model.modelName(), annotation, r.key(), r.value());

        var after = afterSection(r.value());
        if (after == null || after.isNull()) {
            LOG.warnf("Message %s/%d/%d matches model '%s' but has no 'after' section; not evaluated",
                    r.topic(), r.partition(), r.offset(), model.modelName());
            return;
        }

        var inputs = inputsFrom(model, after);
        try {
            var result = auditor.evaluate(model, inputs);

            var event = objectMapper.createObjectNode();
            event.put("model", model.modelName());
            event.put("unit", model.unitName());
            event.put("annotation", annotation);
            event.put("sourceTopic", r.topic());
            event.put("sourcePartition", r.partition());
            event.put("sourceOffset", r.offset());
            event.set("inputs", inputs);
            event.put("status", result.status());
            event.set("result", parseOrWrap(result.body()));

            producer.send(new ProducerRecord<>(
                    AUDIT_TOPIC_PREFIX + "." + r.topic(), r.key(), objectMapper.writeValueAsString(event)));
            LOG.debugf("Audit of model '%s' for %s/%d/%d published: HTTP %d %s",
                    model.modelName(), r.topic(), r.partition(), r.offset(), result.status(), result.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOG.errorf(e, "Audit of model '%s' for message %s/%d/%d failed",
                    model.modelName(), r.topic(), r.partition(), r.offset());
        }
    }

    /**
     * The "after" section of a CDC message value, either at the JSON root or
     * under the Debezium "payload" envelope. Null when the value is not JSON
     * or has no such section.
     */
    private JsonNode afterSection(String value) {
        if (value == null) {
            return null;
        }
        try {
            var root = objectMapper.readTree(value);
            var after = root.get("after");
            if (after == null) {
                after = root.path("payload").get("after");
            }
            return after;
        } catch (Exception notJson) {
            return null;
        }
    }

    /**
     * The model's input parameters found in the after section, matched by
     * normalized name — ignoring case and any character that is not a letter
     * or digit, so RiskRate matches risk_rate or risk-rate. Inputs the
     * message doesn't carry stay unset.
     */
    private ObjectNode inputsFrom(AnnotatedModel model, JsonNode after) {
        var byNormalizedName = new HashMap<String, JsonNode>();
        after.properties().forEach(f -> byNormalizedName.put(normalized(f.getKey()), f.getValue()));

        var inputs = objectMapper.createObjectNode();
        for (var name : model.inputNames()) {
            var value = byNormalizedName.get(normalized(name));
            if (value == null) {
                LOG.debugf("Input '%s' of model '%s' not present in the message; left unset",
                        name, model.modelName());
            } else {
                inputs.set(name, value);
            }
        }
        return inputs;
    }

    /** Lowercased with every non-alphanumeric character (_, -, spaces, ...) dropped. */
    private static String normalized(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** The body as JSON when it parses, as a plain text node otherwise. */
    private JsonNode parseOrWrap(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception notJson) {
            return TextNode.valueOf(body);
        }
    }
}
