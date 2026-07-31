package ih.messaging;

import java.time.Instant;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Publishes an application-lifecycle event to Kafka on every server start, so
 * downstream consumers (e.g. the ih-audit message reader) can observe it.
 */
@ApplicationScoped
public class StartupEventPublisher {

    private static final Logger LOG = Logger.getLogger(StartupEventPublisher.class);

    @Inject
    @Channel("app-lifecycle")
    Emitter<String> lifecycle;

    void onStart(@Observes StartupEvent event) {
        String payload = """
                {"app":"ih-vdn","event":"started","timestamp":"%s"}""".formatted(Instant.now());
        lifecycle.send(payload).whenComplete((unused, failure) -> {
            if (failure == null) {
                LOG.infof("Published startup event: %s", payload);
            } else {
                // Fire-and-forget: a missing broker must never fail startup.
                LOG.warnf(failure, "Could not publish startup event: %s", payload);
            }
        });
    }
}
