package ih.health;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Readiness
@ApplicationScoped
public class DecisionConfig implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.up("Decision config health check");
    }
}