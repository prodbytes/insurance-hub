package ih.dmn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ih.Parameters;
import ih.domain.CarMaker;
import ih.domain.CarMakerRepository;
import ih.domain.CarModel;
import ih.domain.CarModelRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Evaluates the quote DMN models through the Aletyx Decision Control runtime
 * REST API — the same two invocations the application's QuoteService makes:
 * first the carEstimatedValue model (ih.quote.vehicle-value.url) for the
 * vehicle value, then the carQuote model (ih.quote.vehicle-price.url) with
 * that value passed as a regular input. Runs as a QuarkusTest so the seeded
 * car catalog can be read through Panache.
 */
@QuarkusTest
public class CarQuoteDmnTest {

    private static final String ESTIMATED_VALUE_DECISION = "estimatedValue";
    private static final String ESTIMATED_VALUE_INPUT = "carEstimatedValue";
    private static final String PREMIUM_DECISION = "Insurance Premium";

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Inject
    CarMakerRepository carMakersRepo;

    @Inject
    CarModelRepository carModelsRepo;

    @Inject
    Parameters params;

    @Test
    public void honda_civic_2024_is_never_valued_above_50000() {
        var value = estimateValue("honda", "civic", 2024);

        assertTrue(value.compareTo(BigDecimal.valueOf(50000)) <= 0,
                "A 2024 honda civic must never be valued above 50000, but was " + value);
    }

    @Test
    public void premium_is_vehicle_value_times_risk_rate() {
        // First invocation: camry 2026 is valued 30000.
        var value = estimateValue("toyota", "camry", 2026);
        assertTrue(value.compareTo(BigDecimal.valueOf(30000)) == 0,
                "A 2026 toyota camry must be valued 30000, but was " + value);

        // Second invocation: mileage 10000 (risk 1) + tickets (risk 1) give
        // Risk Index 2, so Risk Rate = 0.04 * (1 + 2 * 0.15) = 0.052.
        var result = quote(value, 10000, 40, false, true);
        var premium = decision(result, PREMIUM_DECISION);

        assertTrue(premium.compareTo(BigDecimal.valueOf(1560)) == 0,
                "Insurance Premium must be 30000 * 0.052 = 1560, but was " + premium);
    }

    @Test
    public void no_premium_higher_than_viability_threshold() {
        // A policy stays commercially viable while the premium does not exceed
        // this fraction of the vehicle's estimated value (default 0.15).
        var threshold = params.quote().viabilityThreshold();

        var carMakers = this.carMakersRepo.listAll();
        int assertions = 0;
        for (int year = 2024; year <= 2026; year++) {
            for (CarMaker maker : carMakers) {
                var carModels = this.carModelsRepo.listByMaker(maker);
                for (CarModel carModel : carModels) {
                    var vehicle = year + " " + maker.getName() + " " + carModel.getName();
                    var value = estimateValue(maker.getName(), carModel.getName(), year);
                    assertions++;

                    // Worst rateable driver: very high mileage, young, with
                    // both accidents and tickets on record.
                    var result = quote(value, 500000, 14, true, true);
                    var premium = decision(result, PREMIUM_DECISION, "for " + vehicle);
                    assertions++;
                    var maxViablePremium = value.multiply(threshold);
                    assertTrue(premium.compareTo(maxViablePremium) <= 0,
                            "Premium for " + vehicle + " is " + premium + ", above the viability threshold "
                                    + threshold + " of its value " + value + " (= " + maxViablePremium + ")");
                    assertions++;
                }
            }
        }
        System.out.println("no_premium_higher_than_viability_threshold: " + assertions + " assertions passed");
    }

    /**
     * First invocation: evaluates the carEstimatedValue model for the given
     * vehicle and returns its estimatedValue decision.
     */
    private BigDecimal estimateValue(String make, String model, int year) {
        // The model's decision table keys on lowercase make/model.
        var input = new LinkedHashMap<String, Object>();
        input.put("Make", make.toLowerCase());
        input.put("Model", model.toLowerCase());
        input.put("Year", year);
        var result = evaluate(url(params.quote().vehicleValueUrl(), "ih.quote.vehicle-value.url"), input);
        return decision(result, ESTIMATED_VALUE_DECISION,
                "for " + year + " " + make + " " + model);
    }

    /**
     * Second invocation: evaluates the carQuote model with the estimated value
     * passed as a regular input alongside the risk inputs, as QuoteService
     * posts them.
     */
    private JsonNode quote(BigDecimal estimatedValue, int mileage, int driverAge,
            boolean accidents, boolean tickets) {
        var input = new LinkedHashMap<String, Object>();
        input.put(ESTIMATED_VALUE_INPUT, estimatedValue);
        input.put("Mileage", mileage);
        input.put("Driver Age", driverAge);
        input.put("Accidents", accidents);
        input.put("Tickets", tickets);
        return evaluate(url(params.quote().vehiclePriceUrl(), "ih.quote.vehicle-price.url"), input);
    }

    private static String url(Optional<String> configured, String property) {
        return configured.filter(u -> !u.isBlank())
                .orElseThrow(() -> new AssertionError(
                        "Decision endpoint is not configured (" + property + ")"));
    }

    /**
     * Posts the given inputs to a Decision Control runtime endpoint the app
     * uses and returns the parsed decision outputs.
     */
    private JsonNode evaluate(String url, Map<String, Object> input) {
        try {
            var request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(input)))
                    .build();
            var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(),
                    "Decision Control could not evaluate the model at " + url
                            + " — is the unit uploaded and enabled (scripts/models-upload.sh)?"
                            + " Response: " + response.body());
            return JSON.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while contacting Decision Control at " + url, e);
        } catch (java.io.IOException e) {
            throw new AssertionError("Could not reach Decision Control at " + url, e);
        }
    }

    private static BigDecimal decision(JsonNode result, String name) {
        return decision(result, name, "");
    }

    /** A non-null numeric decision output, or a failed assertion. */
    private static BigDecimal decision(JsonNode result, String name, String detail) {
        var value = result.get(name);
        assertNotNull(value,
                "Decision '" + name + "' missing from the evaluation result " + detail + ": " + result);
        assertTrue(!value.isNull() && value.isNumber(),
                "Decision '" + name + "' has no numeric result " + detail + ": " + result);
        return value.decimalValue();
    }
}
