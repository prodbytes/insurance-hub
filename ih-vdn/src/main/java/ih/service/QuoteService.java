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
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ih.Parameters;
import ih.domain.QuoteRequest;
import ih.domain.QuoteResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Calculates insurance quotes with two DMN evaluations on the Aletyx Decision
 * Control engine (KIE-compatible runtime REST API): the carEstimatedValue
 * model first estimates the vehicle value from make/model/year, and the
 * carQuote model then takes that value as a regular input alongside the risk
 * inputs to compute the risk rate; the final premium is derived from them on
 * the response. The request and its computed response are persisted as a
 * one-to-one pair.
 */
@ApplicationScoped
public class QuoteService {

    /** Output of the carEstimatedValue model. */
    private static final String ESTIMATED_VALUE_OUTPUT = "estimatedValue";
    /** Input of the carQuote model carrying the estimated vehicle value. */
    private static final String ESTIMATED_VALUE_INPUT = "carEstimatedValue";
    private static final String RISK_RATE_OUTPUT = "Risk Rate";

    private final EntityManager em;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Inject
    Parameters params;

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
     * Evaluates the standalone carEstimatedValue DMN model, which depends
     * solely on make, model and year.
     *
     * @throws QuoteCalculationException if the decision engine cannot be reached
     *         or returns no value
     */
    public BigDecimal estimateVehicleValue(String make, String model, Integer year) {
        // The model's decision table keys on lowercase make/model.
        var input = new HashMap<String, Object>();
        input.put("Make", lower(make));
        input.put("Model", lower(model));
        input.put("Year", year);
        var body = evaluate(vehicleValueUrl(), input);
        return required(body, ESTIMATED_VALUE_OUTPUT).decimalValue().setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Makes the two Decision Control invocations behind a quote: first the
     * carEstimatedValue model for the vehicle value, then the carQuote model —
     * with that value passed as a regular input — for the {@code Risk Rate}.
     * The risk rate is driven by the model's Risk Index, to which mileage,
     * driver age, accidents and tickets each contribute independently.
     */
    private CarQuoteResult evaluateCarQuote(QuoteRequest request) {
        var vehiclePrice = estimateVehicleValue(
                request.getMake(), request.getModel(), request.getVehicleYear());

        var input = new HashMap<String, Object>();
        input.put(ESTIMATED_VALUE_INPUT, vehiclePrice);
        input.put("Mileage", request.getAnnualMileage());
        input.put("Driver Age", driverAge(request.getDateOfBirth()));
        input.put("Accidents", request.isAccidentsLast5Years());
        input.put("Tickets", request.isViolationsLast3Years());

        var body = evaluate(vehiclePriceUrl(), input);
        var riskRate = required(body, RISK_RATE_OUTPUT).decimalValue().setScale(4, RoundingMode.HALF_UP);
        return new CarQuoteResult(vehiclePrice, riskRate);
    }

    private String vehiclePriceUrl() {
        return requiredUrl(params.quote().vehiclePriceUrl(), "ih.quote.vehicle-price.url");
    }

    private String vehicleValueUrl() {
        return requiredUrl(params.quote().vehicleValueUrl(), "ih.quote.vehicle-value.url");
    }

    private static String requiredUrl(Optional<String> url, String property) {
        return url.filter(u -> !u.isBlank())
                .orElseThrow(() -> new QuoteCalculationException(
                        "Decision endpoint is not configured (" + property + ")"));
    }

    /**
     * Posts the given inputs to the Decision Control runtime endpoint and
     * returns the parsed decision outputs.
     */
    private JsonNode evaluate(String url, HashMap<String, Object> input) {
        try {
            var httpRequest = HttpRequest.newBuilder(URI.create(url))
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

            return objectMapper.readTree(httpResponse.body());
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
