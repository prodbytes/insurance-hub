package ih.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ih.domain.QuoteRequest;
import ih.domain.QuoteResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Calculates insurance quotes. The estimated vehicle value and the risk rate
 * both come from the car-quote DMN model, evaluated by the Aletyx Decision
 * Control engine over its KIE-compatible runtime REST API. A single evaluation
 * returns both outputs; the final premium is derived from them on the response.
 * The request and its computed response are persisted as a one-to-one pair.
 */
@ApplicationScoped
public class QuoteService {

    private static final String VEHICLE_VALUE_OUTPUT = "Estimated Vehicle Value";
    private static final String RISK_RATE_OUTPUT = "Risk Rate";

    private final EntityManager em;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // Decision Control runtime endpoint for the car-quote model.
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
        var decision = evaluateCarQuote(request);

        em.persist(request);

        var response = new QuoteResponse();
        response.setRequest(request);
        response.setEstimatedVehiclePrice(decision.vehiclePrice());
        response.setRiskRate(decision.riskRate());
        em.persist(response);
        return response;
    }

    /** Loads a previously computed response by id, or {@code null} if absent. */
    @Transactional
    public QuoteResponse findResponse(Long id) {
        return em.find(QuoteResponse.class, id);
    }

    /**
     * Calls the Decision Control runtime to evaluate the car-quote DMN model
     * once, returning both the {@code Estimated Vehicle Value} and the
     * {@code Risk Rate} it computes. The risk rate is driven by the model's
     * Risk Index, to which mileage, driver age, accidents and tickets each
     * contribute independently.
     */
    private CarQuoteResult evaluateCarQuote(QuoteRequest request) {
        // The model's decision table keys on lowercase make/model.
        var input = new HashMap<String, Object>();
        input.put("Make", lower(request.getMake()));
        input.put("Model", lower(request.getModel()));
        input.put("Year", request.getVehicleYear());
        input.put("Mileage", request.getAnnualMileage());
        input.put("Driver Age", driverAge(request.getDateOfBirth()));
        input.put("Accidents", request.isAccidentsLast5Years());
        input.put("Tickets", request.isViolationsLast3Years());

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

            var body = objectMapper.readTree(httpResponse.body());
            var vehiclePrice = required(body, VEHICLE_VALUE_OUTPUT).decimalValue().setScale(2, RoundingMode.HALF_UP);
            var riskRate = required(body, RISK_RATE_OUTPUT).decimalValue().setScale(4, RoundingMode.HALF_UP);
            return new CarQuoteResult(vehiclePrice, riskRate);
        } catch (QuoteCalculationException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QuoteCalculationException("Interrupted while contacting the decision engine", e);
        } catch (Exception e) {
            throw new QuoteCalculationException("Could not reach the decision engine", e);
        }
    }

    /** A non-null decision output, or a {@link QuoteCalculationException}. */
    private static JsonNode required(JsonNode body, String output) {
        var value = body.get(output);
        if (value == null || value.isNull()) {
            throw new QuoteCalculationException(
                    "Decision engine returned no '" + output + "' for the given request");
        }
        return value;
    }

    /** The driver's age in whole years, or {@code null} if no birth date is known. */
    private static Integer driverAge(LocalDate dateOfBirth) {
        return dateOfBirth == null ? null : Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase();
    }

    /** The pair of values the car-quote model computes for a request. */
    private record CarQuoteResult(BigDecimal vehiclePrice, BigDecimal riskRate) {
    }
}
