package ih.rs;

import java.math.BigDecimal;
import java.util.List;

import ih.domain.CarMaker;
import ih.domain.CarMakerRepository;
import ih.service.QuoteService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/insurance")
public class InsuranceResource {

    private final CarMakerRepository carMakers;
    private final QuoteService quoteService;

    public InsuranceResource(CarMakerRepository carMakers, QuoteService quoteService) {
        this.carMakers = carMakers;
        this.quoteService = quoteService;
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String get(){
        return "Hello from RS service";
    }

    @GET
    @Path("/car-maker")
    @Produces(MediaType.APPLICATION_JSON)
    public List<CarMaker> listCarMakers() {
        return carMakers.listAll();
    }

    // curl http://localhost:8080/rs/insurance/vehicle-value?make=honda&model=civic&year=2024
    @GET
    @Path("/vehicle-value")
    @Produces(MediaType.APPLICATION_JSON)
    public BigDecimal estimateVehicleValue(@QueryParam("make") String make,
            @QueryParam("model") String model, @QueryParam("year") Integer year) {
        return quoteService.estimateVehicleValue(make, model, year);
    }

}
