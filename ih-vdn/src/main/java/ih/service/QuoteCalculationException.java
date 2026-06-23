package ih.service;

/**
 * Thrown when a quote cannot be calculated — e.g. the Decision Control engine is
 * unreachable or returns no result.
 */
public class QuoteCalculationException extends RuntimeException {

    public QuoteCalculationException(String message) {
        super(message);
    }

    public QuoteCalculationException(String message, Throwable cause) {
        super(message, cause);
    }
}
