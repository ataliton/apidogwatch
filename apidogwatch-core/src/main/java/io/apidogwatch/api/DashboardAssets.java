package io.apidogwatch.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Loads the embedded dashboard assets packaged inside {@code apidogwatch-core}.
 */
public final class DashboardAssets {

    private DashboardAssets() {
    }

    public static Optional<byte[]> load(String relativePath) {
        String normalized = relativePath == null ? "index.html" : relativePath;
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank() || normalized.equals("ui") || normalized.equals("ui/")) {
            normalized = "index.html";
        }
        String resource = "static/" + normalized;
        try (InputStream in = DashboardAssets.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return Optional.empty();
            }
            return Optional.of(in.readAllBytes());
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    public static String loadHtml() {
        return load("index.html")
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .orElse("<!DOCTYPE html><html><body><h1>ApiDogWatch UI missing</h1></body></html>");
    }

    public static String contentTypeFor(String path) {
        String value = path == null ? "" : path.toLowerCase();
        if (value.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (value.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (value.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (value.endsWith(".png")) {
            return "image/png";
        }
        if (value.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        return "text/html; charset=utf-8";
    }
}
