package io.apidogwatch.servlet;

import io.apidogwatch.ApiDogWatchConfig;
import io.apidogwatch.ApiDogWatchEngine;

import java.io.IOException;

/**
 * Convenience bootstrap for plain Java / Servlet apps.
 *
 * <pre>{@code
 * ApiDogWatchBootstrap bootstrap = ApiDogWatchBootstrap.start(
 *     "classpath:openapi.yaml",
 *     9099
 * );
 * // register bootstrap.getFilter() in your servlet container
 * }</pre>
 */
public final class ApiDogWatchBootstrap implements AutoCloseable {

    private final ApiDogWatchEngine engine;
    private final ApiDogWatchFilter filter;
    private final ApiDogWatchDashboardServer dashboardServer;

    private ApiDogWatchBootstrap(ApiDogWatchEngine engine,
                                 ApiDogWatchFilter filter,
                                 ApiDogWatchDashboardServer dashboardServer) {
        this.engine = engine;
        this.filter = filter;
        this.dashboardServer = dashboardServer;
    }

    public static ApiDogWatchBootstrap start(String openApiLocation, int dashboardPort) throws IOException {
        ApiDogWatchConfig config = ApiDogWatchConfig.builder()
                .openApiLocation(openApiLocation)
                .embeddedServerPort(dashboardPort)
                .build();
        ApiDogWatchEngine engine = ApiDogWatchEngine.create(openApiLocation, config);
        ApiDogWatchFilter filter = new ApiDogWatchFilter(engine);
        ApiDogWatchDashboardServer server = ApiDogWatchDashboardServer.start(engine, dashboardPort);
        return new ApiDogWatchBootstrap(engine, filter, server);
    }

    public ApiDogWatchEngine getEngine() {
        return engine;
    }

    public ApiDogWatchFilter getFilter() {
        return filter;
    }

    public ApiDogWatchDashboardServer getDashboardServer() {
        return dashboardServer;
    }

    @Override
    public void close() {
        if (dashboardServer != null) {
            dashboardServer.close();
        }
    }
}
