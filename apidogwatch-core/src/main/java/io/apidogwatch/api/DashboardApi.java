package io.apidogwatch.api;

import io.apidogwatch.model.Divergence;
import io.apidogwatch.model.InspectedRequest;
import io.apidogwatch.model.MetricsSummary;
import io.apidogwatch.store.InspectionStore;
import io.apidogwatch.util.JsonSupport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Shared JSON API used by Spring MVC controllers and the embedded HttpServer.
 */
public final class DashboardApi {

    private final InspectionStore store;

    public DashboardApi(InspectionStore store) {
        this.store = store;
    }

    public String metricsJson() {
        MetricsSummary metrics = store.metrics();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("totalRequests", metrics.getTotalRequests());
        payload.put("alertCount", metrics.getAlertCount());
        payload.put("cleanCount", metrics.getCleanCount());
        payload.put("undocumentedRoutes", metrics.getUndocumentedRoutes());
        payload.put("uniqueRoutes", metrics.getUniqueRoutes());
        return JsonSupport.toJson(payload);
    }

    public String requestsJson(String method, String pathContains, Boolean alertsOnly, Integer statusCode) {
        List<Map<String, Object>> items = store.findFiltered(method, pathContains, alertsOnly, statusCode)
                .stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
        return JsonSupport.toJson(Map.of("items", items));
    }

    public Optional<String> requestDetailJson(String id) {
        return store.findById(id).map(this::toDetail).map(JsonSupport::toJson);
    }

    public String clearJson() {
        store.clear();
        return JsonSupport.toJson(Map.of("cleared", true));
    }

    private Map<String, Object> toSummary(InspectedRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", request.getId());
        map.put("timestamp", request.getTimestamp().toString());
        map.put("method", request.getMethod());
        map.put("path", request.getPath());
        map.put("statusCode", request.getStatusCode());
        map.put("durationMs", request.getDurationMs());
        map.put("alertCount", request.getAlertCount());
        map.put("hasAlerts", request.hasAlerts());
        map.put("matchedOperation", request.isMatchedOperation());
        return map;
    }

    private Map<String, Object> toDetail(InspectedRequest request) {
        Map<String, Object> map = toSummary(request);
        map.put("queryString", request.getQueryString());
        map.put("contentType", request.getContentType());
        map.put("requestBody", request.getRequestBody());
        map.put("responseBody", JsonSupport.pretty(request.getResponseBody()));
        map.put("expectedSchemaJson", request.getExpectedSchemaJson());
        map.put("divergences", request.getDivergences().stream().map(this::toDivergence).collect(Collectors.toList()));
        return map;
    }

    private Map<String, Object> toDivergence(Divergence divergence) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", divergence.getType().name());
        map.put("path", divergence.getPath());
        map.put("message", divergence.getMessage());
        map.put("expected", divergence.getExpected());
        map.put("actual", divergence.getActual());
        return map;
    }
}
