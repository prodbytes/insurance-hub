package ih.service;

/**
 * Thrown when a car-contract process instance cannot be started — e.g. the
 * Decision Control engine is unreachable or rejects the request.
 */
public class ContractProcessException extends RuntimeException {

    public ContractProcessException(String message) {
        super(message);
    }

    public ContractProcessException(String message, Throwable cause) {
        super(message, cause);
    }
}
