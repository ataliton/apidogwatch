package io.apidogwatch;

import com.fasterxml.jackson.databind.JsonNode;
import io.apidogwatch.comparator.SchemaComparator;
import io.apidogwatch.model.Divergence;
import io.apidogwatch.model.DivergenceType;
import io.apidogwatch.model.InspectedRequest;
import io.apidogwatch.openapi.OpenApiContract;
import io.apidogwatch.store.InspectionStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Central façade used by adapters after capturing an HTTP exchange.
 */
public final class ApiDogWatchEngine {

    private final InspectionStore store;
    private final OpenApiContract contract;
    private final SchemaComparator comparator;
    private final ApiDogWatchConfig config;

    public ApiDogWatchEngine(OpenApiContract contract, ApiDogWatchConfig config) {
        this(InspectionStore.getInstance(), contract, new SchemaComparator(), config);
    }

    public ApiDogWatchEngine(InspectionStore store,
                             OpenApiContract contract,
                             SchemaComparator comparator,
                             ApiDogWatchConfig config) {
        this.store = Objects.requireNonNull(store, "store");
        this.contract = Objects.requireNonNull(contract, "contract");
        this.comparator = Objects.requireNonNull(comparator, "comparator");
        this.config = config == null ? ApiDogWatchConfig.defaults() : config;
    }

    public static ApiDogWatchEngine create(String openApiLocation) {
        return create(openApiLocation, ApiDogWatchConfig.defaults());
    }

    public static ApiDogWatchEngine create(String openApiLocation, ApiDogWatchConfig config) {
        return new ApiDogWatchEngine(OpenApiContract.load(openApiLocation), config);
    }

    public InspectionStore getStore() {
        return store;
    }

    public OpenApiContract getContract() {
        return contract;
    }

    public ApiDogWatchConfig getConfig() {
        return config;
    }

    public boolean shouldIgnore(String path) {
        if (path == null) {
            return true;
        }
        String normalized = path.toLowerCase(Locale.ROOT);
        if (normalized.startsWith(config.getUiPathPrefix().toLowerCase(Locale.ROOT))) {
            return true;
        }
        for (String excluded : config.getExcludedPathPrefixes()) {
            if (normalized.startsWith(excluded.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public InspectedRequest inspectAndRecord(Capture capture) {
        Objects.requireNonNull(capture, "capture");
        List<Divergence> divergences = new ArrayList<>();
        boolean matched = false;
        String expectedSchemaJson = null;

        var operation = contract.findOperation(capture.method(), capture.path(), capture.statusCode());
        if (operation.isEmpty()) {
            divergences.add(new Divergence(
                    DivergenceType.UNDOCUMENTED_ROUTE,
                    capture.path(),
                    "No OpenAPI operation matches " + capture.method().toUpperCase(Locale.ROOT) + " " + capture.path()
            ));
        } else {
            matched = true;
            OpenApiContract.MatchedOperation matchedOp = operation.get();
            if (!matchedOp.statusDeclared()) {
                divergences.add(new Divergence(
                        DivergenceType.UNEXPECTED_STATUS,
                        capture.path(),
                        "Status " + capture.statusCode() + " is not declared for this operation",
                        "declared responses",
                        String.valueOf(capture.statusCode())
                ));
            }
            JsonNode schema = matchedOp.responseSchema();
            if (schema != null && !schema.isMissingNode()) {
                expectedSchemaJson = contract.schemaAsPrettyJson(schema);
                String body = capture.responseBody();
                boolean looksJson = body != null && !body.isBlank()
                        && (capture.contentType() == null
                        || capture.contentType().toLowerCase(Locale.ROOT).contains("json")
                        || body.trim().startsWith("{")
                        || body.trim().startsWith("["));
                if (looksJson || (body != null && !body.isBlank())) {
                    divergences.addAll(comparator.compare(body, schema));
                }
            }
        }

        InspectedRequest inspected = InspectedRequest.builder()
                .timestamp(capture.timestamp() == null ? Instant.now() : capture.timestamp())
                .method(capture.method())
                .path(capture.path())
                .queryString(capture.queryString())
                .statusCode(capture.statusCode())
                .durationMs(capture.durationMs())
                .contentType(capture.contentType())
                .requestBody(truncate(capture.requestBody()))
                .responseBody(truncate(capture.responseBody()))
                .expectedSchemaJson(expectedSchemaJson)
                .divergences(divergences)
                .matchedOperation(matched)
                .build();

        store.record(inspected);
        return inspected;
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        int max = config.getMaxBodyChars();
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "\n… [truncated by ApiDogWatch]";
    }

    public record Capture(
            String method,
            String path,
            String queryString,
            int statusCode,
            long durationMs,
            String contentType,
            String requestBody,
            String responseBody,
            Instant timestamp
    ) {
        public Capture(String method,
                       String path,
                       String queryString,
                       int statusCode,
                       long durationMs,
                       String contentType,
                       String requestBody,
                       String responseBody) {
            this(method, path, queryString, statusCode, durationMs, contentType, requestBody, responseBody, Instant.now());
        }
    }
}
