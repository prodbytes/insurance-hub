-- Creates the quote_response table backing ih.domain.QuoteResponse: the
-- computed result for a quote_request, associated one-to-one (unique FK).
CREATE TABLE quote_response (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    quote_request_id        BIGINT NOT NULL UNIQUE REFERENCES quote_request (id),
    estimated_vehicle_price NUMERIC(12, 2),
    risk_rate               NUMERIC(10, 4)
);
