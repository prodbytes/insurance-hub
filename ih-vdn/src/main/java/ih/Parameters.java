package ih;

import java.math.BigDecimal;
import java.util.Optional;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix="ih")
@StaticInitSafe
public interface Parameters {

    Quote quote();

    Contract contract();

    public interface Quote {
        // Exported by models-upload.sh; empty when the upload didn't run, and
        // SmallRye converts "" to null — Optional keeps the app bootable then.
        @WithName("vehicle-price.url")
        Optional<String> vehiclePriceUrl();

        // Runtime endpoint of the standalone carEstimatedValue model, invoked
        // separately to obtain the value the carQuote model takes as input.
        @WithName("vehicle-value.url")
        Optional<String> vehicleValueUrl();

        @WithDefault("0.15")
        BigDecimal viabilityThreshold();
    }

    interface Contract {
        // No script exports IH_CONTRACT_PROCESS_URL yet (models-upload.sh only
        // handles DMN), so the property is empty in dev; Optional keeps the
        // app bootable, matching ContractService's use-time error.
        @WithName("process-start.url")
        Optional<String> processStartUrl();
    }
}
