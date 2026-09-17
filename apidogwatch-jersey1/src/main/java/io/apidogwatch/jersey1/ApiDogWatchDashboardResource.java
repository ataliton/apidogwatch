package io.apidogwatch.jersey1;

import io.apidogwatch.api.DashboardApi;
import io.apidogwatch.api.DashboardAssets;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Dashboard + JSON API methods. Subclass and set {@code @Path} only:
 *
 * <pre>{@code
 * @Path("/apidogwatch")
 * public class MyApiDogWatchUi extends ApiDogWatchDashboardResource {}
 * }</pre>
 */
public abstract class ApiDogWatchDashboardResource {

    private DashboardApi api() {
        return new DashboardApi(ApiDogWatchJersey.engine().getStore());
    }

    @GET
    @Path("/ui")
    @Produces(MediaType.TEXT_HTML)
    public Response ui(@QueryParam("lang") String lang,
                       @HeaderParam("Accept-Language") String acceptLanguage) {
        if (!ApiDogWatchJersey.isEnabled()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(disabledMessage(lang, acceptLanguage))
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }
        String preferred = firstNonBlank(lang, resolveUserLanguage(), acceptLanguage);
        return Response.ok(DashboardAssets.loadHtml(preferred))
                .header("Cache-Control", "no-store")
                .build();
    }

    /**
     * Hook for hosts that know the signed-in user locale (e.g. Systêxtil {@code Login.idioma}).
     * Default: {@code null} (browser / Accept-Language / {@code ?lang=} decide).
     */
    protected String resolveUserLanguage() {
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String disabledMessage(String lang, String acceptLanguage) {
        String normalized = DashboardAssets.normalizeLang(firstNonBlank(lang, acceptLanguage));
        if ("pt".equals(normalized)) {
            return "ApiDogWatch desligado. Inicie a JVM com -Dapidogwatch.enabled=true";
        }
        if ("es".equals(normalized)) {
            return "ApiDogWatch desactivado. Inicie la JVM con -Dapidogwatch.enabled=true";
        }
        return "ApiDogWatch is disabled. Start the JVM with -Dapidogwatch.enabled=true";
    }

    @GET
    @Path("/api/metrics")
    @Produces(MediaType.APPLICATION_JSON)
    public Response metrics() {
        return requireEnabledJson(api().metricsJson());
    }

    @GET
    @Path("/api/requests")
    @Produces(MediaType.APPLICATION_JSON)
    public Response requests(@QueryParam("method") String method,
                             @QueryParam("path") String path,
                             @QueryParam("alertsOnly") Boolean alertsOnly,
                             @QueryParam("status") Integer status) {
        return requireEnabledJson(api().requestsJson(method, path, alertsOnly, status));
    }

    @GET
    @Path("/api/requests/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response requestDetail(@PathParam("id") String id) {
        if (!ApiDogWatchJersey.isEnabled()) {
            return disabled();
        }
        return api().requestDetailJson(id)
                .map(json -> Response.ok(json).header("Cache-Control", "no-store").build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/api/requests")
    @Produces(MediaType.APPLICATION_JSON)
    public Response clear() {
        return requireEnabledJson(api().clearJson());
    }

    @GET
    @Path("/api/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        if (!ApiDogWatchJersey.isEnabled()) {
            return Response.ok("{\"status\":\"DISABLED\"}").build();
        }
        String body = "{\"status\":\"UP\",\"openapiLoaded\":"
                + ApiDogWatchJersey.engine().getContract().isLoaded()
                + ",\"storeSize\":"
                + ApiDogWatchJersey.engine().getStore().size()
                + "}";
        return Response.ok(body).build();
    }

    private Response requireEnabledJson(String json) {
        if (!ApiDogWatchJersey.isEnabled()) {
            return disabled();
        }
        return Response.ok(json).header("Cache-Control", "no-store").build();
    }

    private Response disabled() {
        return Response.status(Response.Status.NOT_FOUND)
                .entity("{\"error\":\"apidogwatch_disabled\"}")
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
