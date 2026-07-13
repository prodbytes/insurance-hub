package ih.dmn;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.drools.io.FileSystemResource;
import org.junit.jupiter.api.Test;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.core.internal.utils.DMNRuntimeBuilder;

import ih.Parameters;
import ih.domain.CarMaker;
import ih.domain.CarMakerRepository;
import ih.domain.CarModel;
import ih.domain.CarModelRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Evaluates the car-quote DMN model directly with the DMN engine, without the
 * Decision Control runtime the application calls in production. Runs as a
 * QuarkusTest so the seeded car catalog can be read through Panache.
 */
@QuarkusTest
public class CarQuoteDmnTest {

    private static final Path MODEL_PATH = Path.of("..", "ih-models", "quote", "carQuote.dmn");
    private static final Path VEHICLE_VALUE_MODEL_PATH = Path.of("..", "ih-models", "quote", "carEstimatedValue.dmn");
    private static final String MODEL_NAMESPACE = "https://kie.org/dmn/_03709CE1-A299-4537-B2BA-39E6E442C1B8";
    private static final String MODEL_NAME = "carQuote";
    private static final String VEHICLE_VALUE_DECISION = "carEstimatedValue";

    @Inject
    CarMakerRepository carMakersRepo;

    @Inject
    CarModelRepository carModelsRepo;

    @Inject
    Parameters params;

    @Test
    public void honda_civic_2024_is_never_valued_above_50000() {
        var runtime = createRuntime();
        var model = runtime.getModel(MODEL_NAMESPACE, MODEL_NAME);
        assertNotNull(model, "DMN model " + MODEL_NAME + " not found in " + MODEL_PATH);

        var context = runtime.newContext();
        context.set("Make", "honda");
        context.set("Model", "civic");
        context.set("Year", 2024);

        var result = runtime.evaluateByName(model, context, VEHICLE_VALUE_DECISION);
        var value = (BigDecimal) result.getDecisionResultByName(VEHICLE_VALUE_DECISION).getResult();

        assertNotNull(value);
        assertTrue(value.compareTo(BigDecimal.valueOf(50000)) <= 0,
                "A 2024 honda civic must never be valued above 50000, but was " + value);
    }

    @Test
    public void premium_is_vehicle_value_times_risk_rate() {
        var runtime = createRuntime();
        var model = runtime.getModel(MODEL_NAMESPACE, MODEL_NAME);
        assertNotNull(model, "DMN model " + MODEL_NAME + " not found in " + MODEL_PATH);

        // camry 2026 is valued 30000; mileage 10000 (risk 1) + tickets (risk 1)
        // give Risk Index 2, so Risk Rate = 0.04 * (1 + 2 * 0.15) = 0.052.
        var context = runtime.newContext();
        context.set("Make", "toyota");
        context.set("Model", "camry");
        context.set("Year", 2026);
        context.set("Mileage", 10000);
        context.set("Driver Age", 40);
        context.set("Accidents", false);
        context.set("Tickets", true);

        var result = runtime.evaluateByName(model, context, "Insurance Premium");
        var premium = (BigDecimal) result.getDecisionResultByName("Insurance Premium").getResult();

        assertNotNull(premium, "Insurance Premium was not computed: " + result.getMessages());
        assertTrue(premium.compareTo(BigDecimal.valueOf(1560)) == 0,
                "Insurance Premium must be 30000 * 0.052 = 1560, but was " + premium);
    }

    @Test
    public void no_premium_higher_than_viability_threshold() {
        var runtime = createRuntime();
        var model = runtime.getModel(MODEL_NAMESPACE, MODEL_NAME);
        assertNotNull(model, "DMN model " + MODEL_NAME + " not found in " + MODEL_PATH);

        // A policy stays commercially viable while the premium does not exceed
        // this fraction of the vehicle's estimated value (default 0.15).
        var threshold = params.quote().viabilityThreshold();

        var carMakers = this.carMakersRepo.listAll();
        int assertions = 0;
        for (int year = 2024; year <= 2026; year++) {
            for (CarMaker maker : carMakers) {
                var carModels = this.carModelsRepo.listByMaker(maker);
                for (CarModel carModel : carModels) {
                    // Worst rateable driver: very high mileage, young, with
                    // both accidents and tickets on record.
                    var context = runtime.newContext();
                    context.set("Make", maker.getName());
                    context.set("Model", carModel.getName());
                    context.set("Year", year);
                    context.set("Mileage", 500000);
                    context.set("Driver Age", 14);
                    context.set("Accidents", true);
                    context.set("Tickets", true);

                    var result = runtime.evaluateByName(model, context, VEHICLE_VALUE_DECISION, "Insurance Premium");
                    var value = (BigDecimal) result.getDecisionResultByName(VEHICLE_VALUE_DECISION).getResult();
                    var premium = (BigDecimal) result.getDecisionResultByName("Insurance Premium").getResult();

                    var vehicle = year + " " + maker.getName() + " " + carModel.getName();
                    assertNotNull(value,
                            "No estimated value for " + vehicle + ": " + result.getMessages());
                    assertions++;
                    assertNotNull(premium,
                            "Insurance Premium was not computed for " + vehicle + ": " + result.getMessages());
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

    private static DMNRuntime createRuntime() {
        assertTrue(Files.exists(MODEL_PATH),
                "DMN model file not found: " + MODEL_PATH.toAbsolutePath().normalize());
        assertTrue(Files.exists(VEHICLE_VALUE_MODEL_PATH),
                "DMN model file not found: " + VEHICLE_VALUE_MODEL_PATH.toAbsolutePath().normalize());
        return DMNRuntimeBuilder.fromDefaults()
                .buildConfiguration()
                .fromResources(List.of(
                        // The imported model must compile before the model importing it.
                        new FileSystemResource(VEHICLE_VALUE_MODEL_PATH.toFile()),
                        new FileSystemResource(MODEL_PATH.toFile())))
                .getOrElseThrow(e -> new IllegalStateException("Could not build DMN runtime", e));
    }
}
