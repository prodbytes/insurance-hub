package ih.rs;

import java.util.List;

import ih.domain.CarMaker;
import ih.domain.CarMakerRepository;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/insurance")
public class InsuranceResource {

    private final CarMakerRepository carMakers;

    public InsuranceResource(CarMakerRepository carMakers) {
        this.carMakers = carMakers;
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
}
