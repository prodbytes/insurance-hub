package ih.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ih.Parameters;
import ih.domain.QuoteRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Starts car-contract process instances on the Aletyx Decision Control engine
 * over its KIE-compatible runtime REST API. The process collects provider
 * quotes for a minute, selects the lowest, runs the approval and credit
 * checks, and establishes the contract.
 */
@ApplicationScoped
public class ContractService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // Decision Control process-start endpoint for the car-contract process.
    // Optional: the URL is only exported when the model upload resolved it, and
    // an empty value must not fail Quarkus config validation at startup.
    @Inject
    Parameters params;

    public ContractService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Starts a car-contract process instance for the vehicle in the given
     * quote request and returns the new process instance id.
     *
     * @throws ContractProcessException if the endpoint is not configured, the
     *         engine cannot be reached, or it rejects the request
     */
    public long startContract(QuoteRequest request) {
        var processStartUrl = params.contract().processStartUrl();
        var url = processStartUrl.filter(u -> !u.isBlank()).orElseThrow(() -> new ContractProcessException(
                "Contract process endpoint is not configured (ih.contract.process-start.url)"));

        // The process feeds make/model into the car-quote DMN, whose decision
        // table keys on lowercase make/model.
        var variables = new HashMap<String, Object>();
        variables.put("make", lower(request.getMake()));
        variables.put("model", lower(request.getModel()));
        variables.put("year", request.getVehicleYear());

        try {
            var httpRequest = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(variables)))
                    .build();
            var httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() != 200 && httpResponse.statusCode() != 201) {
                throw new ContractProcessException(
                        "Process engine returned HTTP " + httpResponse.statusCode() + ": " + httpResponse.body());
            }

            return instanceId(objectMapper.readTree(httpResponse.body()));
        } catch (ContractProcessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ContractProcessException("Interrupted while contacting the process engine", e);
        } catch (Exception e) {
            throw new ContractProcessException("Could not reach the process engine", e);
        }
    }

    /**
     * The new instance's id from the start response. KIE Server returns the
     * bare id as a JSON number; tolerate an object wrapping it under a
     * conventional field name.
     */
    private static long instanceId(JsonNode body) {
        if (body.isNumber()) {
            return body.asLong();
        }
        for (var field : new String[] { "id", "processInstanceId", "process-instance-id" }) {
            var value = body.get(field);
            if (value != null && value.isNumber()) {
                return value.asLong();
            }
        }
        throw new ContractProcessException("Process engine returned no instance id: " + body);
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase();
    }
}
