package io.apidogwatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runtime configuration shared by Spring and Servlet adapters.
 */
public final class ApiDogWatchConfig {

    private final boolean enabled;
    private final String openApiLocation;
    private final String uiPathPrefix;
    private final int maxBodyChars;
    private final int maxStoreEntries;
    private final List<String> excludedPathPrefixes;
    private final int embeddedServerPort;

    private ApiDogWatchConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.openApiLocation = builder.openApiLocation;
        this.uiPathPrefix = normalizePrefix(builder.uiPathPrefix);
        this.maxBodyChars = builder.maxBodyChars;
        this.maxStoreEntries = builder.maxStoreEntries;
        this.excludedPathPrefixes = List.copyOf(builder.excludedPathPrefixes);
        this.embeddedServerPort = builder.embeddedServerPort;
    }

    public static ApiDogWatchConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getOpenApiLocation() {
        return openApiLocation;
    }

    public String getUiPathPrefix() {
        return uiPathPrefix;
    }

    public int getMaxBodyChars() {
        return maxBodyChars;
    }

    public int getMaxStoreEntries() {
        return maxStoreEntries;
    }

    public List<String> getExcludedPathPrefixes() {
        return excludedPathPrefixes;
    }

    public int getEmbeddedServerPort() {
        return embeddedServerPort;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "/apidogwatch";
        }
        String value = prefix.trim();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        if (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public static final class Builder {
        private boolean enabled = true;
        private String openApiLocation = "classpath:openapi.json";
        private String uiPathPrefix = "/apidogwatch";
        private int maxBodyChars = 64_000;
        private int maxStoreEntries = 500;
        private final List<String> excludedPathPrefixes = new ArrayList<>(List.of(
                "/actuator",
                "/swagger-ui",
                "/v3/api-docs",
                "/favicon.ico"
        ));
        private int embeddedServerPort = 9099;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder openApiLocation(String openApiLocation) {
            this.openApiLocation = openApiLocation;
            return this;
        }

        public Builder uiPathPrefix(String uiPathPrefix) {
            this.uiPathPrefix = uiPathPrefix;
            return this;
        }

        public Builder maxBodyChars(int maxBodyChars) {
            this.maxBodyChars = maxBodyChars;
            return this;
        }

        public Builder maxStoreEntries(int maxStoreEntries) {
            this.maxStoreEntries = maxStoreEntries;
            return this;
        }

        public Builder excludedPathPrefixes(List<String> excludedPathPrefixes) {
            Objects.requireNonNull(excludedPathPrefixes, "excludedPathPrefixes");
            this.excludedPathPrefixes.clear();
            this.excludedPathPrefixes.addAll(excludedPathPrefixes);
            return this;
        }

        public Builder addExcludedPathPrefix(String prefix) {
            this.excludedPathPrefixes.add(prefix);
            return this;
        }

        public Builder embeddedServerPort(int embeddedServerPort) {
            this.embeddedServerPort = embeddedServerPort;
            return this;
        }

        public ApiDogWatchConfig build() {
            return new ApiDogWatchConfig(this);
        }
    }
}
