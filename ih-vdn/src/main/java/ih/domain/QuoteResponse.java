package ih.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * The computed result for a {@link QuoteRequest}: the estimated vehicle value
 * and the risk rate. The yearly premium is derived from the two. Associated
 * one-to-one with the request it answers.
 *
 * <p>Plain Hibernate entity, consistent with {@link QuoteRequest}. The backing
 * table is owned by a Flyway migration (schema generation stays off).
 */
@Entity
@Table(name = "quote_response")
public class QuoteResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "quote_request_id", unique = true, nullable = false)
    private QuoteRequest request;

    @Column(name = "estimated_vehicle_price", precision = 12, scale = 2)
    private BigDecimal estimatedVehiclePrice;

    @Column(name = "risk_rate", precision = 10, scale = 4)
    private BigDecimal riskRate;

    public QuoteResponse() {
        // Required by Hibernate.
    }

    public Long getId() {
        return id;
    }

    public QuoteRequest getRequest() {
        return request;
    }

    public void setRequest(QuoteRequest request) {
        this.request = request;
    }

    public BigDecimal getEstimatedVehiclePrice() {
        return estimatedVehiclePrice;
    }

    public void setEstimatedVehiclePrice(BigDecimal estimatedVehiclePrice) {
        this.estimatedVehiclePrice = estimatedVehiclePrice;
    }

    public BigDecimal getRiskRate() {
        return riskRate;
    }

    public void setRiskRate(BigDecimal riskRate) {
        this.riskRate = riskRate;
    }

    /**
     * The calculated yearly premium: estimated vehicle price × risk rate.
     * Derived on read, so it is not persisted; returns {@code null} until both
     * inputs are set.
     */
    public BigDecimal getYearlyPremium() {
        if (estimatedVehiclePrice == null || riskRate == null) {
            return null;
        }
        return estimatedVehiclePrice.multiply(riskRate).setScale(2, RoundingMode.HALF_UP);
    }
}
