package ih.rs;

import java.util.*;

import ih.domain.CarMaker;
import ih.domain.CarMakerRepository;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;


@Path("/decision-config")
public class DecisionConfigResource {
    @ConfigProperty(name = "ih.quote.vehicle-price.url")
    String vehiclePriceUrl;


    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Map<String,String> get(){
        var result = Map.of("ih.quote.vehicle-price.url", vehiclePriceUrl);
        return result;
    }

}
