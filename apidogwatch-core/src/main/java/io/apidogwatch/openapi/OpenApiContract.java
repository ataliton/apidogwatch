package io.apidogwatch.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apidogwatch.util.JsonSupport;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads an OpenAPI 3.x document (JSON or YAML) from classpath, filesystem or HTTP URL
 * and resolves response schemas for concrete method/path/status combinations.
 */
public final class OpenApiContract {

    private static final Logger log = Logger.getLogger(OpenApiContract.class.getName());

    private final JsonNode root;
    private final boolean loaded;

    private OpenApiContract(JsonNode root, boolean loaded) {
        this.root = root;
        this.loaded = loaded;
    }

    public static OpenApiContract empty() {
        return new OpenApiContract(JsonSupport.mapper().createObjectNode(), false);
    }

    public static OpenApiContract load(String location) {
        if (location == null || location.isBlank()) {
            log.warning("ApiDogWatch: no OpenAPI location configured");
            return empty();
        }
        try {
            String content = readLocation(location.trim());
            JsonNode node = parseDocument(content);
            log.info("ApiDogWatch: OpenAPI contract loaded from " + location);
            return new OpenApiContract(node, true);
        } catch (Exception ex) {
            log.log(Level.SEVERE, "ApiDogWatch: failed to load OpenAPI from " + location + ": " + ex.getMessage(), ex);
            return empty();
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    public JsonNode getRoot() {
        return root;
    }

    public Optional<MatchedOperation> findOperation(String method, String requestPath, int statusCode) {
        if (!loaded || root == null) {
            return Optional.empty();
        }
        JsonNode paths = root.path("paths");
        if (!paths.isObject()) {
            return Optional.empty();
        }

        String normalizedPath = normalizePath(requestPath);
        var fields = paths.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String template = entry.getKey();
            if (!pathMatches(template, normalizedPath)) {
                continue;
            }
            JsonNode operation = entry.getValue().path(method.toLowerCase(Locale.ROOT));
            if (operation.isMissingNode()) {
                continue;
            }
            JsonNode responses = operation.path("responses");
            JsonNode response = responses.path(String.valueOf(statusCode));
            if (response.isMissingNode()) {
                response = responses.path("default");
            }
            JsonNode schema = extractSchema(response);
            return Optional.of(new MatchedOperation(template, method.toUpperCase(Locale.ROOT), schema, !response.isMissingNode()));
        }
        return Optional.empty();
    }

    public String schemaAsPrettyJson(JsonNode schema) {
        if (schema == null || schema.isMissingNode() || schema.isNull()) {
            return "{}";
        }
        try {
            JsonNode resolved = resolveLocalRefs(schema, root);
            return JsonSupport.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(resolved);
        } catch (Exception ex) {
            return schema.toPrettyString();
        }
    }

    private JsonNode extractSchema(JsonNode response) {
        if (response == null || response.isMissingNode()) {
            return null;
        }
        JsonNode content = response.path("content");
        if (!content.isObject()) {
            return null;
        }
        if (content.has("application/json")) {
            return content.path("application/json").path("schema");
        }
        var mediaTypes = content.fields();
        if (mediaTypes.hasNext()) {
            return mediaTypes.next().getValue().path("schema");
        }
        return null;
    }

    private JsonNode resolveLocalRefs(JsonNode node, JsonNode document) {
        if (node == null) {
            return null;
        }
        if (node.has("$ref")) {
            String ref = node.get("$ref").asText();
            if (ref.startsWith("#/")) {
                JsonNode resolved = document;
                for (String part : ref.substring(2).split("/")) {
                    resolved = resolved.path(part);
                }
                if (!resolved.isMissingNode()) {
                    return resolveLocalRefs(resolved, document);
                }
            }
            return node;
        }
        if (node.isObject()) {
            var copy = JsonSupport.mapper().createObjectNode();
            node.fields().forEachRemaining(entry ->
                    copy.set(entry.getKey(), resolveLocalRefs(entry.getValue(), document)));
            return copy;
        }
        if (node.isArray()) {
            var copy = JsonSupport.mapper().createArrayNode();
            node.forEach(item -> copy.add(resolveLocalRefs(item, document)));
            return copy;
        }
        return node;
    }

    static boolean pathMatches(String template, String actual) {
        String[] templateParts = normalizePath(template).split("/");
        String[] actualParts = normalizePath(actual).split("/");
        if (templateParts.length != actualParts.length) {
            return false;
        }
        for (int i = 0; i < templateParts.length; i++) {
            String expected = templateParts[i];
            String value = actualParts[i];
            if (expected.startsWith("{") && expected.endsWith("}")) {
                continue;
            }
            if (!expected.equals(value)) {
                return false;
            }
        }
        return true;
    }

    static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String cleaned = path.trim();
        int query = cleaned.indexOf('?');
        if (query >= 0) {
            cleaned = cleaned.substring(0, query);
        }
        if (!cleaned.startsWith("/")) {
            cleaned = "/" + cleaned;
        }
        if (cleaned.length() > 1 && cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    private static String readLocation(String location) throws IOException, InterruptedException {
        if (location.startsWith("classpath:")) {
            String resource = location.substring("classpath:".length());
            try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IOException("Classpath resource not found: " + resource);
                }
                return new String(in.readAllBytes());
            }
        }
        if (location.startsWith("http://") || location.startsWith("https://")) {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(location))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode() + " while fetching OpenAPI");
            }
            return response.body();
        }
        Path path = Path.of(location);
        return Files.readString(path);
    }

    private static JsonNode parseDocument(String content) throws IOException {
        String trimmed = content.trim();
        ObjectMapper mapper = trimmed.startsWith("{")
                ? JsonSupport.mapper()
                : new ObjectMapper(new YAMLFactory());
        return mapper.readTree(trimmed);
    }

    public record MatchedOperation(String pathTemplate,
                                   String method,
                                   JsonNode responseSchema,
                                   boolean statusDeclared) {
    }
}
