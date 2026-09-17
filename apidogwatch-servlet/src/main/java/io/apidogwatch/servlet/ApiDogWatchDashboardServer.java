package io.apidogwatch.servlet;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.apidogwatch.ApiDogWatchEngine;
import io.apidogwatch.api.DashboardApi;
import io.apidogwatch.api.DashboardAssets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Lightweight standalone dashboard server powered by {@link HttpServer}.
 * Useful for plain Servlet / Jakarta EE apps that should not expose the UI
 * on the main application port.
 */
public final class ApiDogWatchDashboardServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ApiDogWatchDashboardServer.class);

    private final ApiDogWatchEngine engine;
    private final DashboardApi api;
    private final int port;
    private HttpServer server;

    public ApiDogWatchDashboardServer(ApiDogWatchEngine engine, int port) {
        this.engine = engine;
        this.api = new DashboardApi(engine.getStore());
        this.port = port;
    }

    public static ApiDogWatchDashboardServer start(ApiDogWatchEngine engine, int port) throws IOException {
        ApiDogWatchDashboardServer dashboard = new ApiDogWatchDashboardServer(engine, port);
        dashboard.start();
        return dashboard;
    }

    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }
        server = HttpServer.create(new InetSocketAddress(port), 0);
        String prefix = engine.getConfig().getUiPathPrefix();

        server.createContext(prefix + "/ui", this::handleUi);
        server.createContext(prefix + "/api/metrics", exchange -> handleGet(exchange, api.metricsJson()));
        server.createContext(prefix + "/api/requests", this::handleRequests);
        server.createContext(prefix + "/api/health", exchange -> handleGet(exchange,
                "{\"status\":\"UP\",\"openapiLoaded\":" + engine.getContract().isLoaded()
                        + ",\"storeSize\":" + engine.getStore().size() + "}"));

        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "apidogwatch-dashboard");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        log.info("ApiDogWatch dashboard listening on http://localhost:{}{}/ui", port, prefix);
    }

    public int getPort() {
        return port;
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Override
    public void close() {
        stop();
    }

    private void handleUi(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }
        byte[] body = DashboardAssets.loadHtml().getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/html; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private void handleRequests(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String prefix = engine.getConfig().getUiPathPrefix() + "/api/requests";

        if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod()) && path.equals(prefix)) {
            handleGet(exchange, api.clearJson());
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        if (path.equals(prefix) || path.equals(prefix + "/")) {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            Boolean alertsOnly = query.containsKey("alertsOnly")
                    ? Boolean.parseBoolean(query.get("alertsOnly"))
                    : null;
            Integer status = query.containsKey("status") ? Integer.parseInt(query.get("status")) : null;
            handleGet(exchange, api.requestsJson(query.get("method"), query.get("path"), alertsOnly, status));
            return;
        }

        if (path.startsWith(prefix + "/")) {
            String id = path.substring((prefix + "/").length());
            var detail = api.requestDetailJson(id);
            if (detail.isEmpty()) {
                send(exchange, 404, "application/json", "{\"error\":\"not_found\"}");
                return;
            }
            handleGet(exchange, detail.get());
            return;
        }

        send(exchange, 404, "application/json", "{\"error\":\"not_found\"}");
    }

    private void handleGet(HttpExchange exchange, String json) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())
                && !"DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }
        send(exchange, 200, "application/json; charset=utf-8", json);
    }

    private void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        headers.set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return map;
        }
        for (String pair : rawQuery.split("&")) {
            int idx = pair.indexOf('=');
            if (idx < 0) {
                map.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
            } else {
                map.put(
                        URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8)
                );
            }
        }
        return map;
    }
}
