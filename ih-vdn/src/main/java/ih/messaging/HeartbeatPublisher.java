package ih.messaging;

import java.time.Duration;
import java.time.Instant;

import org.eclipse.microprofile.reactive.messaging.Outgoing;

import io.smallrye.mutiny.Multi;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Publishes a heartbeat record to Kafka every two minutes, so downstream
 * consumers (e.g. the ih-audit message reader) can see the app is alive.
 * Records carry a JSON key identifying the app and event type, constant
 * across ticks so every heartbeat from the same app lands in the same
 * partition and stays ordered.
 */
@ApplicationScoped
public class HeartbeatPublisher {

    static final String APP = "ih-vdn";
    static final Duration INTERVAL = Duration.ofMinutes(2);
    static final String KEY = """
            {"app":"%s","event":"heartbeat"}""".formatted(APP);

    @Outgoing("app-heartbeat")
    Multi<Record<String, String>> heartbeat() {
        return Multi.createFrom().ticks().every(INTERVAL)
                .map(tick -> Record.of(KEY, """
                        {"app":"%s","event":"heartbeat","seq":%d,"timestamp":"%s"}"""
                        .formatted(APP, tick, Instant.now())));
    }
}
