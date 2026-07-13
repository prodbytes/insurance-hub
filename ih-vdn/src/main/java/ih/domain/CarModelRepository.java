package ih.domain;

import java.util.List;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Panache repository for {@link CarModel}. Keeps the entity a plain Hibernate
 * entity while exposing Panache's query helpers.
 */
@ApplicationScoped
public class CarModelRepository implements PanacheRepository<CarModel> {

    public List<CarModel> listByMaker(CarMaker maker) {
        return list("maker", maker);
    }
}
