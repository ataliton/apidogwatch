package io.apidogwatch.spring;

import io.apidogwatch.ApiDogWatchEngine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Marks request start time for latency metrics collected by {@link ApiDogWatchContentFilter}.
 */
public class ApiDogWatchInterceptor implements HandlerInterceptor {

    public static final String START_ATTR = ApiDogWatchInterceptor.class.getName() + ".start";

    private final ApiDogWatchEngine engine;

    public ApiDogWatchInterceptor(ApiDogWatchEngine engine) {
        this.engine = engine;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (engine.getConfig().isEnabled() && !engine.shouldIgnore(request.getRequestURI())) {
            request.setAttribute(START_ATTR, System.currentTimeMillis());
        }
        return true;
    }
}
