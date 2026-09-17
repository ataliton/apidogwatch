package io.apidogwatch.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Snapshot of a single intercepted HTTP exchange plus contract inspection results.
 */
public final class InspectedRequest {

    private final String id;
    private final Instant timestamp;
    private final String method;
    private final String path;
    private final String queryString;
    private final int statusCode;
    private final long durationMs;
    private final String contentType;
    private final String requestBody;
    private final String responseBody;
    private final String expectedSchemaJson;
    private final List<Divergence> divergences;
    private final boolean matchedOperation;

    private InspectedRequest(Builder builder) {
        this.id = builder.id == null ? UUID.randomUUID().toString() : builder.id;
        this.timestamp = builder.timestamp == null ? Instant.now() : builder.timestamp;
        this.method = Objects.requireNonNull(builder.method, "method").toUpperCase();
        this.path = Objects.requireNonNull(builder.path, "path");
        this.queryString = builder.queryString;
        this.statusCode = builder.statusCode;
        this.durationMs = builder.durationMs;
        this.contentType = builder.contentType;
        this.requestBody = builder.requestBody;
        this.responseBody = builder.responseBody;
        this.expectedSchemaJson = builder.expectedSchemaJson;
        this.divergences = builder.divergences == null
                ? List.of()
                : Collections.unmodifiableList(List.copyOf(builder.divergences));
        this.matchedOperation = builder.matchedOperation;
    }

    public String getId() {
        return id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getQueryString() {
        return queryString;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getContentType() {
        return contentType;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getExpectedSchemaJson() {
        return expectedSchemaJson;
    }

    public List<Divergence> getDivergences() {
        return divergences;
    }

    public boolean isMatchedOperation() {
        return matchedOperation;
    }

    public boolean hasAlerts() {
        return !divergences.isEmpty();
    }

    public int getAlertCount() {
        return divergences.size();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private Instant timestamp;
        private String method;
        private String path;
        private String queryString;
        private int statusCode;
        private long durationMs;
        private String contentType;
        private String requestBody;
        private String responseBody;
        private String expectedSchemaJson;
        private List<Divergence> divergences;
        private boolean matchedOperation;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder queryString(String queryString) {
            this.queryString = queryString;
            return this;
        }

        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder requestBody(String requestBody) {
            this.requestBody = requestBody;
            return this;
        }

        public Builder responseBody(String responseBody) {
            this.responseBody = responseBody;
            return this;
        }

        public Builder expectedSchemaJson(String expectedSchemaJson) {
            this.expectedSchemaJson = expectedSchemaJson;
            return this;
        }

        public Builder divergences(List<Divergence> divergences) {
            this.divergences = divergences;
            return this;
        }

        public Builder matchedOperation(boolean matchedOperation) {
            this.matchedOperation = matchedOperation;
            return this;
        }

        public InspectedRequest build() {
            return new InspectedRequest(this);
        }
    }
}
