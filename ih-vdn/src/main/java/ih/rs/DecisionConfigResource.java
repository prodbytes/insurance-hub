package ih.rs;

import java.util.*;

import ih.Parameters;
import ih.domain.CarMaker;
import ih.domain.CarMakerRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;


@Path("/decision-config")
public class DecisionConfigResource {
    // Only the @ConfigMapping interface itself is a bean; nested groups such
    // as Parameters.Quote cannot be injected directly.
    @Inject
    Parameters params;


    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Map<String,String> get(){
        // Map.of rejects nulls, so surface a missing URL as an empty string.
        var vehiclePriceUrl = params.quote().vehiclePriceUrl().orElse("");
        var vehicleValueUrl = params.quote().vehicleValueUrl().orElse("");
        var result = Map.of(
                "ih.quote.vehicle-price.url", vehiclePriceUrl,
                "ih.quote.vehicle-value.url", vehicleValueUrl);
        return result;
    }

}
