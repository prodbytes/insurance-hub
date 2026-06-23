package ih.vdn;

import java.time.LocalDate;
import java.util.Map;
import java.util.Random;

import org.jboss.logging.Logger;

import ih.domain.QuoteRequest;
import ih.service.QuoteService;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

/**
 * Quote request form collecting the information usually needed for a vehicle
 * insurance quote: driver details, vehicle details, and coverage preferences.
 */
@PageTitle("InsuranceHub | Get a quote")
@Route("quote")
public class QuoteView extends VerticalLayout {

    private static final Logger LOG = Logger.getLogger(QuoteView.class);

    // Select option lists, reused both to populate the inputs and to draw
    // random values for the "Fill random" helper.
    private static final String[] MARITAL_STATUSES = {"Single", "Married", "Divorced", "Widowed"};
    private static final String[] PRIMARY_USES = {"Commute", "Pleasure", "Business", "Rideshare"};
    private static final String[] OWNERSHIPS = {"Owned", "Financed", "Leased"};
    private static final String[] PARKING_OPTIONS = {"Garage", "Driveway", "Street", "Parking lot"};
    private static final String[] COVERAGE_LEVELS = {"Liability only", "Standard", "Comprehensive", "Full coverage"};
    private static final String[] DEDUCTIBLES = {"$250", "$500", "$1,000", "$2,000"};

    // Makes/models mirror the DMN pricing model so generated data is meaningful.
    private static final String[] MAKES = {"Toyota", "Honda", "Ford"};
    private static final Map<String, String[]> MODELS = Map.of(
            "Toyota", new String[]{"Camry", "Corolla", "RAV4"},
            "Honda", new String[]{"Civic", "Accord", "CR-V"},
            "Ford", new String[]{"F-150", "Escape", "Explorer"});

    private static final String[] FIRST_NAMES = {"Alex", "Jordan", "Taylor", "Morgan", "Casey", "Jamie", "Riley", "Sam"};
    private static final String[] LAST_NAMES = {"Smith", "Johnson", "Williams", "Brown", "Garcia", "Miller", "Davis", "Lopez"};
    private static final String[] STREETS = {"Main St", "Oak Ave", "Maple Dr", "Cedar Ln", "Pine Rd", "Elm St"};
    private static final String[] CITIES = {"Springfield", "Riverside", "Fairview", "Madison", "Georgetown", "Franklin"};
    private static final String[] STATES = {"CA", "TX", "NY", "FL", "WA", "IL"};

    // VIN-safe alphabet (excludes I, O, Q per the VIN standard).
    private static final String VIN_CHARS = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";

    // Driver details
    private final TextField firstName = new TextField("First name");
    private final TextField lastName = new TextField("Last name");
    private final DatePicker dob = new DatePicker("Date of birth");
    private final EmailField email = new EmailField("Email");
    private final TextField phone = new TextField("Phone");
    private final TextField address = new TextField("Street address");
    private final TextField city = new TextField("City");
    private final TextField state = new TextField("State / Province");
    private final TextField zip = new TextField("ZIP / Postal code");
    private final Select<String> maritalStatus = new Select<>();
    private final IntegerField licenseAge = new IntegerField("Age when first licensed");

    // Vehicle details
    private final IntegerField year = new IntegerField("Year");
    private final TextField make = new TextField("Make");
    private final TextField model = new TextField("Model");
    private final TextField vin = new TextField("VIN");
    private final Select<String> usage = new Select<>();
    private final IntegerField annualMileage = new IntegerField("Estimated annual mileage");
    private final Select<String> ownership = new Select<>();
    private final Select<String> parking = new Select<>();

    // Coverage preferences
    private final Select<String> coverageLevel = new Select<>();
    private final Select<String> deductible = new Select<>();
    private final DatePicker startDate = new DatePicker("Desired start date");
    private final Checkbox priorInsurance = new Checkbox("I currently have auto insurance");
    private final Checkbox accidents = new Checkbox("Accidents or claims in the last 5 years");
    private final Checkbox violations = new Checkbox("Traffic tickets in the last 3 years");

    private final Random random = new Random();

    private final QuoteService quoteService;

    public QuoteView(QuoteService quoteService) {
        this.quoteService = quoteService;
        addClassName("hero");
        addClassName("hero--form");
        setWidthFull();
        setPadding(false);
        setSpacing(false);

        var inner = new VerticalLayout(buildForm());
        inner.addClassName("hero__inner");
        inner.setWidthFull();
        add(inner);
    }

    private VerticalLayout buildForm() {
        var fillRandom = new Button("Fill random", e -> fillRandom());
        fillRandom.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        var eyebrow = new Span("GET A QUOTE");
        eyebrow.addClassName("hero__eyebrow");

        var title = new H1("Tell us about you and your vehicle.");
        title.addClassName("hero__title");

        var subtitle = new Paragraph(
                "Fill in the details below to receive a personalized vehicle insurance quote.");
        subtitle.addClassName("hero__subtitle");

        var driver = buildDriverSection();
        var vehicle = buildVehicleSection();
        var coverage = buildCoverageSection();

        var submit = new Button("Get my quote");
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        submit.addClickListener(e -> {
            try {
                var response = quoteService.calculateQuote(buildRequest());
                submit.getUI().ifPresent(ui -> ui.navigate(QuoteResultView.class, response.getId()));
            } catch (RuntimeException ex) {
                LOG.error("Quote calculation failed", ex);
                submit.getUI().ifPresent(ui -> ui.navigate(ErrorView.class));
            }
        });

        var back = new RouterLink("", MainView.class);
        var backButton = new Button("Back to home");
        backButton.addThemeVariants(ButtonVariant.LUMO_LARGE, ButtonVariant.LUMO_TERTIARY);
        back.add(backButton);

        var actions = new HorizontalLayout(submit, back);
        actions.addClassName("hero__actions");

        var text = new VerticalLayout(eyebrow, title, subtitle, fillRandom, driver, vehicle, coverage, actions);
        text.addClassName("hero__text");
        text.setPadding(false);
        text.setSpacing(false);
        text.setHorizontalComponentAlignment(FlexComponent.Alignment.END, fillRandom);
        return text;
    }

    private VerticalLayout buildDriverSection() {
        dob.setMax(LocalDate.now());

        maritalStatus.setLabel("Marital status");
        maritalStatus.setItems(MARITAL_STATUSES);

        licenseAge.setMin(14);
        licenseAge.setMax(100);
        licenseAge.setStepButtonsVisible(true);

        return section("Driver details",
                firstName, lastName, dob, email, phone,
                address, city, state, zip, maritalStatus, licenseAge);
    }

    private VerticalLayout buildVehicleSection() {
        year.setMin(1900);
        year.setMax(LocalDate.now().getYear() + 1);
        year.setStepButtonsVisible(true);

        usage.setLabel("Primary use");
        usage.setItems(PRIMARY_USES);

        annualMileage.setMin(0);
        annualMileage.setStep(1000);
        annualMileage.setStepButtonsVisible(true);

        ownership.setLabel("Ownership");
        ownership.setItems(OWNERSHIPS);

        parking.setLabel("Where is it parked overnight?");
        parking.setItems(PARKING_OPTIONS);

        return section("Vehicle details",
                year, make, model, vin, usage, annualMileage, ownership, parking);
    }

    private VerticalLayout buildCoverageSection() {
        coverageLevel.setLabel("Coverage level");
        coverageLevel.setItems(COVERAGE_LEVELS);

        deductible.setLabel("Deductible");
        deductible.setItems(DEDUCTIBLES);

        startDate.setLabel("Desired start date");
        startDate.setMin(LocalDate.now());

        var extras = new VerticalLayout(priorInsurance, accidents, violations);
        extras.setPadding(false);
        extras.setSpacing(false);

        var form = formLayout();
        form.add(coverageLevel, deductible, startDate);
        form.setColspan(startDate, 1);

        var heading = new H2("Coverage preferences");
        heading.addClassName("hero__section-title");

        var wrapper = new VerticalLayout(heading, form, extras);
        wrapper.setPadding(false);
        wrapper.setSpacing(false);
        wrapper.setWidthFull();
        return wrapper;
    }

    /** Builds a {@link QuoteRequest} from the current form values. */
    private QuoteRequest buildRequest() {
        var request = new QuoteRequest();
        request.setFirstName(firstName.getValue());
        request.setLastName(lastName.getValue());
        request.setDateOfBirth(dob.getValue());
        request.setEmail(email.getValue());
        request.setPhone(phone.getValue());
        request.setStreetAddress(address.getValue());
        request.setCity(city.getValue());
        request.setState(state.getValue());
        request.setZipCode(zip.getValue());
        request.setMaritalStatus(maritalStatus.getValue());
        request.setAgeWhenFirstLicensed(licenseAge.getValue());
        request.setVehicleYear(year.getValue());
        request.setMake(make.getValue());
        request.setModel(model.getValue());
        request.setVin(vin.getValue());
        request.setPrimaryUse(usage.getValue());
        request.setAnnualMileage(annualMileage.getValue());
        request.setOwnership(ownership.getValue());
        request.setOvernightParking(parking.getValue());
        request.setCoverageLevel(coverageLevel.getValue());
        request.setDeductible(deductible.getValue());
        request.setDesiredStartDate(startDate.getValue());
        request.setPriorInsurance(priorInsurance.getValue());
        request.setAccidentsLast5Years(accidents.getValue());
        request.setViolationsLast3Years(violations.getValue());
        return request;
    }

    /** Populates every field with reasonable random mock data. */
    private void fillRandom() {
        var first = pick(FIRST_NAMES);
        var last = pick(LAST_NAMES);

        firstName.setValue(first);
        lastName.setValue(last);
        dob.setValue(LocalDate.now().minusYears(18 + random.nextInt(63)).minusDays(random.nextInt(365)));
        email.setValue((first + "." + last + random.nextInt(100) + "@example.com").toLowerCase());
        phone.setValue(String.format("(%03d) %03d-%04d", 200 + random.nextInt(800), random.nextInt(1000), random.nextInt(10000)));
        address.setValue((1 + random.nextInt(9999)) + " " + pick(STREETS));
        city.setValue(pick(CITIES));
        state.setValue(pick(STATES));
        zip.setValue(String.format("%05d", random.nextInt(100000)));
        maritalStatus.setValue(pick(MARITAL_STATUSES));
        licenseAge.setValue(16 + random.nextInt(10));

        var chosenMake = pick(MAKES);
        // The pricing model only covers 2024–2026, so keep generated years in range.
        year.setValue(2024 + random.nextInt(3));
        make.setValue(chosenMake);
        model.setValue(pick(MODELS.get(chosenMake)));
        vin.setValue(randomVin());
        usage.setValue(pick(PRIMARY_USES));
        annualMileage.setValue((5 + random.nextInt(21)) * 1000);
        ownership.setValue(pick(OWNERSHIPS));
        parking.setValue(pick(PARKING_OPTIONS));

        coverageLevel.setValue(pick(COVERAGE_LEVELS));
        deductible.setValue(pick(DEDUCTIBLES));
        startDate.setValue(LocalDate.now().plusDays(random.nextInt(31)));
        priorInsurance.setValue(random.nextBoolean());
        accidents.setValue(random.nextBoolean());
        violations.setValue(random.nextBoolean());
    }

    private String pick(String[] options) {
        return options[random.nextInt(options.length)];
    }

    private String randomVin() {
        var sb = new StringBuilder(17);
        for (var i = 0; i < 17; i++) {
            sb.append(VIN_CHARS.charAt(random.nextInt(VIN_CHARS.length())));
        }
        return sb.toString();
    }

    private VerticalLayout section(String heading, com.vaadin.flow.component.Component... fields) {
        var form = formLayout();
        form.add(fields);

        var title = new H2(heading);
        title.addClassName("hero__section-title");

        var wrapper = new VerticalLayout(title, form);
        wrapper.setPadding(false);
        wrapper.setSpacing(false);
        wrapper.setWidthFull();
        return wrapper;
    }

    private FormLayout formLayout() {
        var form = new FormLayout();
        form.setWidthFull();
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("480px", 2),
                new FormLayout.ResponsiveStep("768px", 3));
        return form;
    }
}
