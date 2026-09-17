package io.apidogwatch.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * Loads the embedded dashboard assets packaged inside {@code apidogwatch-core}.
 */
public final class DashboardAssets {

    private static final String LANG_PLACEHOLDER = "__APIDOGWATCH_LANG__";

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
        return loadHtml(null);
    }

    /**
     * @param preferredLang raw locale such as {@code pt_BR}, {@code en-US}, {@code es},
     *                      {@code Accept-Language} header value, or {@code null}
     */
    public static String loadHtml(String preferredLang) {
        String html = load("index.html")
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .orElse("<!DOCTYPE html><html><body><h1>ApiDogWatch UI missing</h1></body></html>");
        String normalized = normalizeLang(preferredLang);
        return html.replace(LANG_PLACEHOLDER, normalized == null ? "" : normalized);
    }

    /**
     * Maps Systêxtil / browser locales to dashboard packs: {@code pt}, {@code en}, {@code es}.
     */
    public static String normalizeLang(String raw) {
        if (raw == null || raw.isBlank() || LANG_PLACEHOLDER.equals(raw)) {
            return null;
        }
        // Accept-Language may be: pt-BR,pt;q=0.9,en;q=0.8
        String first = raw.split(",")[0].trim();
        int q = first.indexOf(';');
        if (q > 0) {
            first = first.substring(0, q).trim();
        }
        String value = first.toLowerCase(Locale.ROOT).replace('_', '-');
        if (value.startsWith("pt")) {
            return "pt";
        }
        if (value.startsWith("es")) {
            return "es";
        }
        if (value.startsWith("en")) {
            return "en";
        }
        return null;
    }

    public static String contentTypeFor(String path) {
        String value = path == null ? "" : path.toLowerCase(Locale.ROOT);
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
