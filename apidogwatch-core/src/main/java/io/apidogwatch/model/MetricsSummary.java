package io.apidogwatch.model;

/**
 * Aggregated counters exposed by the dashboard metrics cards.
 */
public final class MetricsSummary {

    private final long totalRequests;
    private final long alertCount;
    private final long cleanCount;
    private final long undocumentedRoutes;
    private final long uniqueRoutes;

    public MetricsSummary(long totalRequests,
                          long alertCount,
                          long cleanCount,
                          long undocumentedRoutes,
                          long uniqueRoutes) {
        this.totalRequests = totalRequests;
        this.alertCount = alertCount;
        this.cleanCount = cleanCount;
        this.undocumentedRoutes = undocumentedRoutes;
        this.uniqueRoutes = uniqueRoutes;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public long getAlertCount() {
        return alertCount;
    }

    public long getCleanCount() {
        return cleanCount;
    }

    public long getUndocumentedRoutes() {
        return undocumentedRoutes;
    }

    public long getUniqueRoutes() {
        return uniqueRoutes;
    }
}
