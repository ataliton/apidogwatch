package io.apidogwatch.jersey1;

import io.apidogwatch.ApiDogWatchConfig;
import io.apidogwatch.ApiDogWatchEngine;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One-stop bootstrap for Jersey 1 / JAX-RS 1.1 applications.
 *
 * <h2>Zero-friction setup</h2>
 * <pre>{@code
 * static {
 *     ApiDogWatchJersey.auto(cfg -> cfg
 *         .openApiLocation("classpath:openapi.json") // or http://host/v3/api-docs
 *         .uiPathPrefix("/apidogwatch"));
 * }
 *
 * {@literal @}Override
 * public Set<Class<?>> getServices() {
 *     Set<Class<?>> classes = new HashSet<>();
 *     // ... your resources
 *     classes.addAll(ApiDogWatchJersey.resources()); // dashboard UI
 *     return classes;
 * }
 *
 * {@literal @}Override
 * public Set<Object> getSingletons() {
 *     return ApiDogWatchJersey.singletons(); // watches ALL APIs
 * }
 * }</pre>
 *
 * Enable with {@code -Dapidogwatch.enabled=true} or {@code APIDOGWATCH_ENABLED=true}.
 * Dashboard: {@code /apidogwatch/ui} (or subclass {@link ApiDogWatchDashboardEndpoints} with a custom {@code @Path}).
 */
public final class ApiDogWatchJersey {

    private static final Object LOCK = new Object();
    private static volatile ApiDogWatchEngine engine;
    private static volatile ApiDogWatchConfig config = ApiDogWatchConfig.builder().enabled(false).build();
    private static final ApiDogWatchResourceFilterFactory GLOBAL_FILTER_FACTORY = new ApiDogWatchResourceFilterFactory();

    private ApiDogWatchJersey() {
    }

    /**
     * Preferred entrypoint: configure + ready for {@link #resources()} / {@link #singletons()}.
     */
    public static void auto(Consumer<ApiDogWatchConfig.Builder> customizer) {
        configure(customizer);
    }

    /**
     * Configure from system properties only, then use {@link #resources()} / {@link #singletons()}.
     */
    public static void auto() {
        configureFromSystem();
    }

    /**
     * Configure before the first request. Safe to call multiple times before {@link #engine()} is used.
     */
    public static void configure(Consumer<ApiDogWatchConfig.Builder> customizer) {
        Objects.requireNonNull(customizer, "customizer");
        ApiDogWatchConfig.Builder builder = ApiDogWatchConfig.builder()
                .enabled(resolveEnabled(false))
                .openApiLocation(System.getProperty("apidogwatch.openapi", "classpath:openapi.json"))
                .uiPathPrefix(System.getProperty("apidogwatch.path", "/apidogwatch"));
        customizer.accept(builder);
        ApiDogWatchConfig built = builder.build();
        synchronized (LOCK) {
            config = built;
            engine = null;
        }
    }

    /**
     * Convenience: configure from system properties / env only.
     * <ul>
     *   <li>{@code apidogwatch.enabled} / {@code APIDOGWATCH_ENABLED}</li>
     *   <li>{@code apidogwatch.openapi} — classpath:, file or URL (incl. Swagger {@code /v3/api-docs})</li>
     *   <li>{@code apidogwatch.path} — UI base path (default {@code /apidogwatch})</li>
     * </ul>
     */
    public static void configureFromSystem() {
        configure(builder -> {
            // defaults already applied in configure()
        });
    }

    /**
     * JAX-RS resource classes to register (dashboard UI).
     */
    public static Set<Class<?>> resources() {
        Set<Class<?>> classes = new LinkedHashSet<>();
        classes.add(ApiDogWatchDashboardEndpoints.class);
        return Collections.unmodifiableSet(classes);
    }

    /**
     * JAX-RS singleton providers to register (global resource filter factory).
     */
    public static Set<Object> singletons() {
        Set<Object> singletons = new LinkedHashSet<>();
        singletons.add(GLOBAL_FILTER_FACTORY);
        return Collections.unmodifiableSet(singletons);
    }

    public static boolean isEnabled() {
        return resolveEnabled(config.isEnabled());
    }

    public static ApiDogWatchConfig config() {
        return config;
    }

    public static ApiDogWatchEngine engine() {
        ApiDogWatchEngine local = engine;
        if (local != null) {
            return local;
        }
        synchronized (LOCK) {
            if (engine == null) {
                try {
                    ApiDogWatchConfig effective = ApiDogWatchConfig.builder()
                            .enabled(isEnabled())
                            .openApiLocation(config.getOpenApiLocation())
                            .uiPathPrefix(config.getUiPathPrefix())
                            .maxBodyChars(config.getMaxBodyChars())
                            .maxStoreEntries(config.getMaxStoreEntries())
                            .excludedPathPrefixes(config.getExcludedPathPrefixes())
                            .addExcludedPathPrefix(config.getUiPathPrefix())
                            .build();
                    engine = ApiDogWatchEngine.create(effective.getOpenApiLocation(), effective);
                } catch (Throwable t) {
                    System.err.println("[ApiDogWatch] engine bootstrap failed: " + t);
                    config = ApiDogWatchConfig.builder().enabled(false).build();
                    throw new IllegalStateException("ApiDogWatch engine unavailable", t);
                }
            }
            return engine;
        }
    }

    private static boolean resolveEnabled(boolean configDefault) {
        String prop = System.getProperty("apidogwatch.enabled");
        if (prop != null) {
            return Boolean.parseBoolean(prop);
        }
        String env = System.getenv("APIDOGWATCH_ENABLED");
        if (env != null) {
            return Boolean.parseBoolean(env);
        }
        return configDefault;
    }
}
