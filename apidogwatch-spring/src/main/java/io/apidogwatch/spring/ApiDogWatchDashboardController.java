package io.apidogwatch.spring;

import io.apidogwatch.ApiDogWatchEngine;
import io.apidogwatch.api.DashboardApi;
import io.apidogwatch.api.DashboardAssets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the embedded dashboard UI and JSON API under {@code /apidogwatch/**}.
 */
@RestController
@RequestMapping("${apidogwatch.path:/apidogwatch}")
public class ApiDogWatchDashboardController {

    private final DashboardApi api;
    private final ApiDogWatchEngine engine;

    public ApiDogWatchDashboardController(ApiDogWatchEngine engine) {
        this.engine = engine;
        this.api = new DashboardApi(engine.getStore());
    }

    @GetMapping({"", "/", "/ui", "/ui/"})
    public ResponseEntity<String> ui() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.TEXT_HTML)
                .body(DashboardAssets.loadHtml());
    }

    @GetMapping(value = "/api/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> metrics() {
        return json(api.metricsJson());
    }

    @GetMapping(value = "/api/requests", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> requests(
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) Boolean alertsOnly,
            @RequestParam(required = false) Integer status) {
        return json(api.requestsJson(method, path, alertsOnly, status));
    }

    @GetMapping(value = "/api/requests/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> requestDetail(@PathVariable String id) {
        return api.requestDetailJson(id)
                .map(this::json)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping(value = "/api/requests", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> clear() {
        return json(api.clearJson());
    }

    @GetMapping(value = "/api/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> health() {
        String body = "{\"status\":\"UP\",\"openapiLoaded\":"
                + engine.getContract().isLoaded()
                + ",\"storeSize\":"
                + engine.getStore().size()
                + "}";
        return json(body);
    }

    private ResponseEntity<String> json(String body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
