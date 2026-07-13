package ih.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A supported vehicle model of a {@link CarMaker}. The table and its seed data
 * are owned by the Flyway migration {@code V20260713100000__Add_car_models};
 * this entity only maps it for use from Java (Hibernate ORM).
 */
@Entity
@Table(name = "car_model")
public class CarModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "car_maker_id", nullable = false)
    private CarMaker maker;

    @Column(nullable = false)
    private String name;

    protected CarModel() {
        // Required by Hibernate.
    }

    public CarModel(CarMaker maker, String name) {
        this.maker = maker;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public CarMaker getMaker() {
        return maker;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
