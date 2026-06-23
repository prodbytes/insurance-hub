package ih.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Random;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.ObjectMapper;

import ih.domain.QuoteRequest;
import ih.domain.QuoteResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Calculates insurance quotes. The estimated vehicle value comes from the
 * car-quote DMN model, evaluated by the Aletyx Decision Control engine over its
 * KIE-compatible runtime REST API. Risk and the final premium are still mock.
 * The request and its computed response are persisted as a one-to-one pair.
 */
@ApplicationScoped
public class QuoteService {

    // Mock base annual rate, scaled by the random risk multiplier to form the risk rate.
    private static final BigDecimal ANNUAL_RATE = BigDecimal.valueOf(0.04);
    private static final String VEHICLE_VALUE_OUTPUT = "Estimated Vehicle Value";

    private final EntityManager em;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Random random = new Random();

    // Decision Control runtime endpoint for the car-quote model (Estimated Vehicle Value).
    @ConfigProperty(name = "ih.quote.vehicle-price.url")
    String vehiclePriceUrl;

    public QuoteService(EntityManager em, ObjectMapper objectMapper) {
        this.em = em;
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluates the DMN model for the estimated vehicle value, computes a mock
     * {@link QuoteResponse} from it, persists the request/response pair, and
     * returns the saved response.
     *
     * @throws QuoteCalculationException if the decision engine cannot be reached
     *         or returns no value
     */
    @Transactional
    public QuoteResponse calculateQuote(QuoteRequest request) {
        var vehiclePrice = estimateVehiclePrice(request);

        var riskRate = BigDecimal.valueOf(0.5 + random.nextDouble() * 1.5)
                .multiply(ANNUAL_RATE)
                .setScale(4, RoundingMode.HALF_UP);

        em.persist(request);

        var response = new QuoteResponse();
        response.setRequest(request);
        response.setEstimatedVehiclePrice(vehiclePrice);
        response.setRiskRate(riskRate);
        em.persist(response);
        return response;
    }

    /** Loads a previously computed response by id, or {@code null} if absent. */
    @Transactional
    public QuoteResponse findResponse(Long id) {
        return em.find(QuoteResponse.class, id);
    }

    /**
     * Calls the Decision Control runtime to evaluate the car-quote DMN model and
     * returns its {@code Estimated Vehicle Value} as money.
     */
    private BigDecimal estimateVehiclePrice(QuoteRequest request) {
        // The model's decision table keys on lowercase make/model.
        var input = new HashMap<String, Object>();
        input.put("Make", lower(request.getMake()));
        input.put("Model", lower(request.getModel()));
        input.put("Year", request.getVehicleYear());
        input.put("Mileage", request.getAnnualMileage());

        try {
            var httpRequest = HttpRequest.newBuilder(URI.create(vehiclePriceUrl))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(input)))
                    .build();
            var httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() != 200) {
                throw new QuoteCalculationException(
                        "Decision engine returned HTTP " + httpResponse.statusCode() + ": " + httpResponse.body());
            }

            var value = objectMapper.readTree(httpResponse.body()).get(VEHICLE_VALUE_OUTPUT);
            if (value == null || value.isNull()) {
                throw new QuoteCalculationException(
                        "Decision engine returned no '" + VEHICLE_VALUE_OUTPUT + "' for the given vehicle");
            }
            return value.decimalValue().setScale(2, RoundingMode.HALF_UP);
        } catch (QuoteCalculationException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QuoteCalculationException("Interrupted while contacting the decision engine", e);
        } catch (Exception e) {
            throw new QuoteCalculationException("Could not reach the decision engine", e);
        }
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase();
    }
}
