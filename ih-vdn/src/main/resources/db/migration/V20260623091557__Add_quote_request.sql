-- Creates the quote_request table backing ih.domain.QuoteRequest, capturing
-- every field collected by the Vaadin quote form (ih.vdn.QuoteView).
CREATE TABLE quote_request (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- Driver details
    first_name              VARCHAR(255),
    last_name               VARCHAR(255),
    date_of_birth           DATE,
    email                   VARCHAR(255),
    phone                   VARCHAR(255),
    street_address          VARCHAR(255),
    city                    VARCHAR(255),
    state                   VARCHAR(255),
    zip_code                VARCHAR(255),
    marital_status          VARCHAR(255),
    age_when_first_licensed INTEGER,

    -- Vehicle details
    vehicle_year            INTEGER,
    make                    VARCHAR(255),
    model                   VARCHAR(255),
    vin                     VARCHAR(255),
    primary_use             VARCHAR(255),
    annual_mileage          INTEGER,
    ownership               VARCHAR(255),
    overnight_parking       VARCHAR(255),

    -- Coverage preferences
    coverage_level          VARCHAR(255),
    deductible              VARCHAR(255),
    desired_start_date      DATE,
    prior_insurance         BOOLEAN NOT NULL DEFAULT FALSE,
    accidents_last_5_years  BOOLEAN NOT NULL DEFAULT FALSE,
    violations_last_3_years BOOLEAN NOT NULL DEFAULT FALSE
);
