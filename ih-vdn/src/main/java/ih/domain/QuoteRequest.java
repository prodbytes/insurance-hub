package ih.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A vehicle insurance quote request, capturing every field collected by the
 * quote form (see {@code ih.vdn.QuoteView}): driver details, vehicle details,
 * and coverage preferences.
 *
 * <p>Plain Hibernate entity, consistent with {@link CarMaker}. The backing
 * table is owned by a Flyway migration (schema generation stays off).
 */
@Entity
@Table(name = "quote_request")
public class QuoteRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Driver details ------------------------------------------------------
    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    private String email;

    private String phone;

    @Column(name = "street_address")
    private String streetAddress;

    private String city;

    private String state;

    @Column(name = "zip_code")
    private String zipCode;

    @Column(name = "marital_status")
    private String maritalStatus;

    @Column(name = "age_when_first_licensed")
    private Integer ageWhenFirstLicensed;

    // --- Vehicle details -----------------------------------------------------
    @Column(name = "vehicle_year")
    private Integer vehicleYear;

    private String make;

    private String model;

    private String vin;

    @Column(name = "primary_use")
    private String primaryUse;

    @Column(name = "annual_mileage")
    private Integer annualMileage;

    private String ownership;

    @Column(name = "overnight_parking")
    private String overnightParking;

    // --- Coverage preferences ------------------------------------------------
    @Column(name = "coverage_level")
    private String coverageLevel;

    private String deductible;

    @Column(name = "desired_start_date")
    private LocalDate desiredStartDate;

    @Column(name = "prior_insurance")
    private boolean priorInsurance;

    @Column(name = "accidents_last_5_years")
    private boolean accidentsLast5Years;

    @Column(name = "violations_last_3_years")
    private boolean violationsLast3Years;

    public QuoteRequest() {
        // Required by Hibernate; the form populates fields via setters.
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public void setStreetAddress(String streetAddress) {
        this.streetAddress = streetAddress;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public String getMaritalStatus() {
        return maritalStatus;
    }

    public void setMaritalStatus(String maritalStatus) {
        this.maritalStatus = maritalStatus;
    }

    public Integer getAgeWhenFirstLicensed() {
        return ageWhenFirstLicensed;
    }

    public void setAgeWhenFirstLicensed(Integer ageWhenFirstLicensed) {
        this.ageWhenFirstLicensed = ageWhenFirstLicensed;
    }

    public Integer getVehicleYear() {
        return vehicleYear;
    }

    public void setVehicleYear(Integer vehicleYear) {
        this.vehicleYear = vehicleYear;
    }

    public String getMake() {
        return make;
    }

    public void setMake(String make) {
        this.make = make;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getVin() {
        return vin;
    }

    public void setVin(String vin) {
        this.vin = vin;
    }

    public String getPrimaryUse() {
        return primaryUse;
    }

    public void setPrimaryUse(String primaryUse) {
        this.primaryUse = primaryUse;
    }

    public Integer getAnnualMileage() {
        return annualMileage;
    }

    public void setAnnualMileage(Integer annualMileage) {
        this.annualMileage = annualMileage;
    }

    public String getOwnership() {
        return ownership;
    }

    public void setOwnership(String ownership) {
        this.ownership = ownership;
    }

    public String getOvernightParking() {
        return overnightParking;
    }

    public void setOvernightParking(String overnightParking) {
        this.overnightParking = overnightParking;
    }

    public String getCoverageLevel() {
        return coverageLevel;
    }

    public void setCoverageLevel(String coverageLevel) {
        this.coverageLevel = coverageLevel;
    }

    public String getDeductible() {
        return deductible;
    }

    public void setDeductible(String deductible) {
        this.deductible = deductible;
    }

    public LocalDate getDesiredStartDate() {
        return desiredStartDate;
    }

    public void setDesiredStartDate(LocalDate desiredStartDate) {
        this.desiredStartDate = desiredStartDate;
    }

    public boolean isPriorInsurance() {
        return priorInsurance;
    }

    public void setPriorInsurance(boolean priorInsurance) {
        this.priorInsurance = priorInsurance;
    }

    public boolean isAccidentsLast5Years() {
        return accidentsLast5Years;
    }

    public void setAccidentsLast5Years(boolean accidentsLast5Years) {
        this.accidentsLast5Years = accidentsLast5Years;
    }

    public boolean isViolationsLast3Years() {
        return violationsLast3Years;
    }

    public void setViolationsLast3Years(boolean violationsLast3Years) {
        this.violationsLast3Years = violationsLast3Years;
    }
}
