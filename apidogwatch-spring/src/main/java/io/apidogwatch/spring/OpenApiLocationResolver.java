package io.apidogwatch.spring;

import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Resolves OpenAPI location from explicit config or by probing common classpath / springdoc URLs.
 */
final class OpenApiLocationResolver {

    private static final String[] CLASSPATH_CANDIDATES = {
            "classpath:openapi.json",
            "classpath:openapi.yaml",
            "classpath:openapi.yml",
            "classpath:static/openapi.json",
            "classpath:static/openapi.yaml",
            "classpath:META-INF/openapi.json",
            "classpath:META-INF/openapi.yaml"
    };

    private OpenApiLocationResolver() {
    }

    static String resolve(ApiDogWatchProperties properties, ResourceLoader resourceLoader, Environment environment) {
        String configured = properties.getOpenapi();
        if (configured != null && !configured.isBlank() && !"auto".equalsIgnoreCase(configured.trim())) {
            return configured.trim();
        }

        for (String candidate : CLASSPATH_CANDIDATES) {
            if (exists(resourceLoader, candidate)) {
                return candidate;
            }
        }

        String contextPath = environment.getProperty("server.servlet.context-path", "");
        if (contextPath == null) {
            contextPath = "";
        }
        if (!contextPath.isEmpty() && !contextPath.startsWith("/")) {
            contextPath = "/" + contextPath;
        }
        if (contextPath.endsWith("/")) {
            contextPath = contextPath.substring(0, contextPath.length() - 1);
        }

        String port = environment.getProperty("local.server.port");
        if (port == null || port.isBlank()) {
            port = environment.getProperty("server.port", "8080");
        }

        String springdocPath = environment.getProperty("springdoc.api-docs.path", "/v3/api-docs");
        if (!springdocPath.startsWith("/")) {
            springdocPath = "/" + springdocPath;
        }

        return "http://127.0.0.1:" + port + contextPath + springdocPath;
    }

    private static boolean exists(ResourceLoader resourceLoader, String location) {
        try {
            Resource resource = resourceLoader.getResource(location);
            return resource.exists();
        } catch (Exception ignored) {
            return false;
        }
    }
}
