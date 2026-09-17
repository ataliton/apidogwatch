package io.apidogwatch.store;

import io.apidogwatch.model.DivergenceType;
import io.apidogwatch.model.InspectedRequest;
import io.apidogwatch.model.MetricsSummary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Thread-safe, bounded in-memory history of inspected HTTP exchanges.
 * <p>
 * Designed as a singleton process store so Spring and Servlet adapters share
 * the same inspection memory without extra wiring.
 */
public final class InspectionStore {

    private static final InspectionStore INSTANCE = new InspectionStore(500);

    private final int maxEntries;
    private final ConcurrentLinkedDeque<InspectedRequest> history = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<String, AtomicInteger> routeHits = new ConcurrentHashMap<>();
    private final AtomicInteger totalAlerts = new AtomicInteger();

    public InspectionStore(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be >= 1");
        }
        this.maxEntries = maxEntries;
    }

    public static InspectionStore getInstance() {
        return INSTANCE;
    }

    public void record(InspectedRequest request) {
        Objects.requireNonNull(request, "request");
        history.addFirst(request);
        routeHits.computeIfAbsent(routeKey(request), key -> new AtomicInteger()).incrementAndGet();
        if (request.hasAlerts()) {
            totalAlerts.addAndGet(request.getAlertCount());
        }
        while (history.size() > maxEntries) {
            InspectedRequest removed = history.pollLast();
            if (removed != null && removed.hasAlerts()) {
                totalAlerts.addAndGet(-removed.getAlertCount());
            }
        }
    }

    public List<InspectedRequest> findAll() {
        return List.copyOf(history);
    }

    public List<InspectedRequest> findFiltered(String method,
                                               String pathContains,
                                               Boolean alertsOnly,
                                               Integer statusCode) {
        return history.stream()
                .filter(item -> method == null || method.isBlank()
                        || item.getMethod().equalsIgnoreCase(method.trim()))
                .filter(item -> pathContains == null || pathContains.isBlank()
                        || item.getPath().toLowerCase(Locale.ROOT)
                        .contains(pathContains.trim().toLowerCase(Locale.ROOT)))
                .filter(item -> alertsOnly == null || !alertsOnly || item.hasAlerts())
                .filter(item -> statusCode == null || item.getStatusCode() == statusCode)
                .sorted(Comparator.comparing(InspectedRequest::getTimestamp).reversed())
                .collect(Collectors.toList());
    }

    public Optional<InspectedRequest> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return history.stream().filter(item -> item.getId().equals(id)).findFirst();
    }

    public MetricsSummary metrics() {
        long total = history.size();
        long alerts = history.stream().filter(InspectedRequest::hasAlerts).count();
        long undocumented = history.stream()
                .filter(item -> item.getDivergences().stream()
                        .anyMatch(d -> d.getType() == DivergenceType.UNDOCUMENTED_ROUTE))
                .count();
        return new MetricsSummary(
                total,
                alerts,
                total - alerts,
                undocumented,
                routeHits.size()
        );
    }

    public void clear() {
        history.clear();
        routeHits.clear();
        totalAlerts.set(0);
    }

    public int size() {
        return history.size();
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public List<InspectedRequest> latest(int limit) {
        List<InspectedRequest> result = new ArrayList<>();
        int count = 0;
        for (InspectedRequest request : history) {
            result.add(request);
            if (++count >= limit) {
                break;
            }
        }
        return result;
    }

    private static String routeKey(InspectedRequest request) {
        return request.getMethod() + " " + request.getPath();
    }
}
