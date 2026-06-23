package ih.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A supported vehicle maker. The table and its seed data are owned by the
 * Flyway migration {@code V20260623084134__Add_car_makers}; this entity only
 * maps it for use from Java (Hibernate ORM).
 */
@Entity
@Table(name = "car_maker")
public class CarMaker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    protected CarMaker() {
        // Required by Hibernate.
    }

    public CarMaker(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
