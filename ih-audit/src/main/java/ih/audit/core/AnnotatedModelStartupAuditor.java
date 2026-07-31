package ih.audit.core;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jboss.logging.Logger;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * On startup, looks up on Decision Control every hosted decision model whose
 * DMN source carries a text annotation starting with "@" (e.g.
 * "@carquote_response"), evaluates each one through its runtime endpoint and
 * logs one message per model with the evaluation result.
 *
 * Runs on a daemon thread and retries the first lookup: process-compose
 * starts ih-audit and Decision Control independently, so the server may not
 * be answering yet when this app boots. Models are discovered through the
 * management API (units -> latest enabled version -> model sources) and
 * evaluated with an empty input set — a model whose inputs are required
 * reports the engine's error as its result.
 */
@ApplicationScoped
public class AnnotatedModelStartupAuditor {

    private static final Logger LOG = Logger.getLogger(AnnotatedModelStartupAuditor.class);

    /** A model carrying at least one @-annotation, as discovered at startup. */
    public record AnnotatedModel(String tenantId, String unitName, String modelName,
            List<String> annotations, List<String> inputNames) {
    }

    /** Outcome of a runtime evaluation: the HTTP status and raw response body. */
    public record EvaluationResult(int status, String body) {
        public boolean succeeded() {
            return status == 200;
        }
    }

    /** How long to keep retrying the first Decision Control lookup. */
    private static final int MAX_ATTEMPTS = 60;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Inject
    Parameters params;

    private Thread auditor;

    private final CountDownLatch discovered = new CountDownLatch(1);
    private volatile List<AnnotatedModel> annotatedModels = List.of();

    public AnnotatedModelStartupAuditor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void onStart(@Observes StartupEvent event) {
        auditor = new Thread(this::audit, "ih-audit-model-auditor");
        auditor.setDaemon(true);
        auditor.start();
    }

    void onStop(@Observes ShutdownEvent event) {
        if (auditor != null) {
            auditor.interrupt();
        }
    }

    /**
     * The @-annotated models the startup audit discovered, blocking until the
     * audit finished. Empty when Decision Control never answered or hosts no
     * annotated model.
     */
    public List<AnnotatedModel> awaitAnnotatedModels() throws InterruptedException {
        discovered.await();
        return annotatedModels;
    }

    private void audit() {
        var baseUrl = params.decisionControl().baseUrl();
        LOG.infof("Looking up @-annotated decision models on %s", baseUrl);
        try {
            var units = unitsWithRetry(baseUrl);
            if (units == null) {
                LOG.warnf("Decision Control at %s did not answer after %d attempts; no models audited",
                        baseUrl, MAX_ATTEMPTS);
                return;
            }
            var found = new ArrayList<AnnotatedModel>();
            for (var unit : units) {
                found.addAll(auditUnit(baseUrl, unit));
            }
            annotatedModels = List.copyOf(found);
            LOG.infof("Startup model audit done: %d @-annotated model(s) evaluated", found.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOG.error("Startup model audit failed", e);
        } finally {
            discovered.countDown();
        }
    }

    /**
     * Lists the deployment units, retrying while Decision Control is still
     * booting. Returns {@code null} when it never answered.
     */
    private JsonNode unitsWithRetry(String baseUrl) throws Exception {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                var units = getJson(baseUrl + "/api/management/units");
                if (units.isArray()) {
                    return units;
                }
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                if (attempt == 1) {
                    LOG.infof("Decision Control not answering yet (%s); retrying every %ds",
                            e.getMessage(), RETRY_DELAY.toSeconds());
                }
            }
            Thread.sleep(RETRY_DELAY);
        }
        return null;
    }

    /**
     * Evaluates every @-annotated model of the unit's latest enabled version
     * and returns them.
     */
    private List<AnnotatedModel> auditUnit(String baseUrl, JsonNode unit) throws Exception {
        if (!"ENABLED".equals(unit.path("status").asText())) {
            return List.of();
        }
        var unitId = unit.path("id").asLong();
        var unitName = unit.path("name").asText();
        var tenantId = unit.path("tenantId").asText();

        var version = latestEnabledVersion(baseUrl, unitId);
        if (version == null) {
            return List.of();
        }

        var found = new ArrayList<AnnotatedModel>();
        var models = getJson("%s/api/management/units/%d/versions/%d/models"
                .formatted(baseUrl, unitId, version.path("id").asLong()));
        for (var model : models) {
            var annotations = atAnnotations(model.path("modelContent").asText());
            if (annotations.isEmpty()) {
                continue;
            }
            var annotated = new AnnotatedModel(tenantId, unitName,
                    model.path("modelName").asText(), annotations, inputNames(model));
            var result = evaluate(annotated, objectMapper.createObjectNode());
            if (result.succeeded()) {
                LOG.infof("Model '%s' (unit '%s', annotations %s) evaluated: %s",
                        annotated.modelName(), unitName, annotations, result.body());
            } else {
                LOG.warnf("Model '%s' (unit '%s', annotations %s) evaluation returned HTTP %d: %s",
                        annotated.modelName(), unitName, annotations, result.status(), result.body());
            }
            found.add(annotated);
        }
        return found;
    }

    /**
     * The model's input parameter names, read from the InputSet section of
     * the JSON schema Decision Control serves alongside the model source.
     */
    private List<String> inputNames(JsonNode model) {
        var names = new ArrayList<String>();
        try {
            var schema = objectMapper.readTree(model.path("jsonSchema").asText());
            schema.path("definitions").path("InputSet").path("properties")
                    .fieldNames().forEachRemaining(names::add);
        } catch (Exception e) {
            LOG.warnf("Could not read the input names of model '%s': %s",
                    model.path("modelName").asText(), e.getMessage());
        }
        return List.copyOf(names);
    }

    private JsonNode latestEnabledVersion(String baseUrl, long unitId) throws Exception {
        var versions = getJson("%s/api/management/units/%d/versions".formatted(baseUrl, unitId));
        for (var version : versions) {
            if (version.path("isLastestEnabled").asBoolean()) {
                return version;
            }
        }
        return null;
    }

    /**
     * Evaluates the model with the given inputs through the unit's
     * version-independent /latest runtime endpoint — the same address family
     * the other apps call.
     */
    public EvaluationResult evaluate(AnnotatedModel model, JsonNode inputs) throws Exception {
        var url = "%s/api/runtime/%s/%s/latest/%s".formatted(
                params.decisionControl().baseUrl(), model.tenantId(), model.unitName(), model.modelName());
        var request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(inputs)))
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new EvaluationResult(response.statusCode(), response.body());
    }

    /** The model's text annotations starting with "@", in document order. */
    private List<String> atAnnotations(String modelContent) {
        var found = new ArrayList<String>();
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // The XML is engine-provided, but parse it inert anyway: no
            // doctypes, no external entities.
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(modelContent)));

            // Match by local name: the DMN namespace differs across spec
            // versions (20191111, 20230324, 20240513, ...).
            var textAnnotations = document.getElementsByTagNameNS("*", "textAnnotation");
            for (int i = 0; i < textAnnotations.getLength(); i++) {
                var texts = ((Element) textAnnotations.item(i)).getElementsByTagNameNS("*", "text");
                for (int j = 0; j < texts.getLength(); j++) {
                    var text = texts.item(j).getTextContent().trim();
                    if (text.startsWith("@")) {
                        found.add(text);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warnf("Could not parse a model's DMN source for annotations: %s", e.getMessage());
        }
        return found;
    }

    private JsonNode getJson(String url) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "GET " + url + " returned HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }
}
