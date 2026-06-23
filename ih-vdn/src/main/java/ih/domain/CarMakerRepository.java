package ih.domain;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Panache repository for {@link CarMaker}. Keeps the entity a plain Hibernate
 * entity while exposing Panache's query helpers (e.g. {@code listAll()}).
 */
@ApplicationScoped
public class CarMakerRepository implements PanacheRepository<CarMaker> {
}
