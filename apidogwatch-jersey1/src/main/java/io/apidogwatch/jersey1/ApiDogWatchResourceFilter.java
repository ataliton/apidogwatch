package io.apidogwatch.jersey1;

import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import com.sun.jersey.spi.container.ResourceFilter;
import io.apidogwatch.ApiDogWatchEngine;
import io.apidogwatch.util.JsonSupport;

/**
 * Jersey 1 resource filter. Annotate resources with:
 * <pre>{@code @ResourceFilters(ApiDogWatchResourceFilter.class)}</pre>
 */
public class ApiDogWatchResourceFilter
        implements ResourceFilter, ContainerRequestFilter, ContainerResponseFilter {

    private static final String START_PROP = ApiDogWatchResourceFilter.class.getName() + ".start";

    @Override
    public ContainerRequestFilter getRequestFilter() {
        return ApiDogWatchJersey.isEnabled() ? this : null;
    }

    @Override
    public ContainerResponseFilter getResponseFilter() {
        return ApiDogWatchJersey.isEnabled() ? this : null;
    }

    @Override
    public ContainerRequest filter(ContainerRequest request) {
        request.getProperties().put(START_PROP, System.currentTimeMillis());
        return request;
    }

    @Override
    public ContainerResponse filter(ContainerRequest request, ContainerResponse response) {
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
            // Never break the business API because of the watchdog
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
