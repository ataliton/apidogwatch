package io.apidogwatch.spring;

import io.apidogwatch.ApiDogWatchConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "apidogwatch")
public class ApiDogWatchProperties {

    /**
     * Enables or disables interception and the dashboard endpoints.
     */
    private boolean enabled = true;

    /**
     * OpenAPI location: {@code classpath:…}, filesystem path, HTTP(S) URL, or {@code auto}
     * (probe classpath then springdoc {@code /v3/api-docs}).
     */
    private String openapi = "auto";

    /**
     * Base path for UI and JSON API (default {@code /apidogwatch}).
     */
    private String path = "/apidogwatch";

    private int maxBodyChars = 64_000;

    private List<String> excludePaths = new ArrayList<>(List.of(
            "/actuator",
            "/swagger-ui",
            "/v3/api-docs",
            "/favicon.ico"
    ));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getOpenapi() {
        return openapi;
    }

    public void setOpenapi(String openapi) {
        this.openapi = openapi;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getMaxBodyChars() {
        return maxBodyChars;
    }

    public void setMaxBodyChars(int maxBodyChars) {
        this.maxBodyChars = maxBodyChars;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }

    public ApiDogWatchConfig toConfig() {
        return ApiDogWatchConfig.builder()
                .enabled(enabled)
                .openApiLocation(openapi)
                .uiPathPrefix(path)
                .maxBodyChars(maxBodyChars)
                .excludedPathPrefixes(excludePaths)
                .build();
    }
}
