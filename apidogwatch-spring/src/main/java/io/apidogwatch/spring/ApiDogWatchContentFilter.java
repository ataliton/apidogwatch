package io.apidogwatch.spring;

import io.apidogwatch.ApiDogWatchEngine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Wraps request/response bodies and runs contract inspection after the MVC pipeline.
 */
public class ApiDogWatchContentFilter extends OncePerRequestFilter {

    private final ApiDogWatchEngine engine;

    public ApiDogWatchContentFilter(ApiDogWatchEngine engine) {
        this.engine = engine;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !engine.getConfig().isEnabled() || engine.shouldIgnore(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingRequestWrapper requestWrapper =
                new ContentCachingRequestWrapper(request, engine.getConfig().getMaxBodyChars());
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        long started = System.currentTimeMillis();
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            Object attr = requestWrapper.getAttribute(ApiDogWatchInterceptor.START_ATTR);
            if (attr instanceof Long value) {
                started = value;
            }
            long duration = Math.max(0, System.currentTimeMillis() - started);
            inspect(requestWrapper, responseWrapper, duration);
            responseWrapper.copyBodyToResponse();
        }
    }

    private void inspect(ContentCachingRequestWrapper request,
                         ContentCachingResponseWrapper response,
                         long durationMs) {
        String contentType = response.getContentType();
        engine.inspectAndRecord(new ApiDogWatchEngine.Capture(
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                response.getStatus(),
                durationMs,
                contentType,
                decode(request.getContentAsByteArray(), request.getCharacterEncoding()),
                decode(response.getContentAsByteArray(), response.getCharacterEncoding())
        ));
    }

    private String decode(byte[] body, String encoding) {
        if (body == null || body.length == 0) {
            return "";
        }
        Charset charset = StandardCharsets.UTF_8;
        if (encoding != null && !encoding.isBlank()) {
            try {
                charset = Charset.forName(encoding);
            } catch (Exception ignored) {
                charset = StandardCharsets.UTF_8;
            }
        }
        return new String(body, charset);
    }
}
