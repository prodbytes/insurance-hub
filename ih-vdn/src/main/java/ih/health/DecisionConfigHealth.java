package ih.health;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;

import ih.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class DecisionConfigHealth implements HealthCheck {
    @Inject
    Parameters params;

    @Override
    public HealthCheckResponse call() {
        var vehiclePriceUrl = params.quote().vehiclePriceUrl().orElse("");
        var viabilityThreshold = params.quote().viabilityThreshold();
        return HealthCheckResponse.named("decision-config")
                .withData("ih.quote.vehicle-price.url", vehiclePriceUrl)
                .withData("ih.quote.viability-threshold", viabilityThreshold.toString())
                .up()
                .build();
    }
}