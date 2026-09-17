package io.apidogwatch.jersey1;

import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import io.apidogwatch.ApiDogWatchEngine;
import io.apidogwatch.util.JsonSupport;

/**
 * Global Jersey 1 request/response filter. Register as a singleton provider
 * (via {@link ApiDogWatchJersey#singletons()}) to watch <strong>all</strong> resources
 * without per-class {@code @ResourceFilters}.
 */
public final class ApiDogWatchGlobalFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String START_PROP = ApiDogWatchGlobalFilter.class.getName() + ".start";

    @Override
    public ContainerRequest filter(ContainerRequest request) {
        if (!ApiDogWatchJersey.isEnabled()) {
            return request;
        }
        request.getProperties().put(START_PROP, System.currentTimeMillis());
        return request;
    }

    @Override
    public ContainerResponse filter(ContainerRequest request, ContainerResponse response) {
        if (!ApiDogWatchJersey.isEnabled()) {
            return response;
        }
        try {
            String path = normalizePath(request.getPath());
            ApiDogWatchEngine engine = ApiDogWatchJersey.engine();
            if (engine.shouldIgnore(path)) {
                return response;
            }

            Object startObj = request.getProperties().get(START_PROP);
            long started = startObj instanceof Long ? (Long) startObj : System.currentTimeMillis();
            long duration = Math.max(0L, System.currentTimeMillis() - started);

            String body = serializeEntity(response.getEntity());
            String contentType = response.getMediaType() != null
                    ? response.getMediaType().toString()
                    : "application/json";

            engine.inspectAndRecord(new ApiDogWatchEngine.Capture(
                    request.getMethod(),
                    path,
                    request.getRequestUri().getRawQuery(),
                    response.getStatus(),
                    duration,
                    contentType,
                    null,
                    body
            ));
        } catch (Throwable ignored) {
            // Never break business APIs
        }
        return response;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String serializeEntity(Object entity) {
        if (entity == null) {
            return "";
        }
        if (entity instanceof String str) {
            return str;
        }
        if (entity instanceof byte[] bytes) {
            return new String(bytes);
        }
        try {
            return JsonSupport.toJson(entity);
        } catch (Exception ex) {
            return String.valueOf(entity);
        }
    }
}
