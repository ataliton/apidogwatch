package io.apidogwatch.servlet;

import io.apidogwatch.ApiDogWatchEngine;
import io.apidogwatch.util.BodyBuffers;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Generic Jakarta Servlet filter that captures HTTP exchanges and feeds ApiDogWatch.
 * <p>
 * Register via {@code web.xml}, {@code @WebFilter}, or programmatically.
 * Init params:
 * <ul>
 *   <li>{@code openapi} – classpath/file/URL of the OpenAPI document</li>
 *   <li>{@code enabled} – {@code true}/{@code false}</li>
 * </ul>
 */
public class ApiDogWatchFilter implements Filter {

    public static final String ENGINE_ATTRIBUTE = ApiDogWatchFilter.class.getName() + ".engine";

    private ApiDogWatchEngine engine;

    public ApiDogWatchFilter() {
    }

    public ApiDogWatchFilter(ApiDogWatchEngine engine) {
        this.engine = engine;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        if (engine != null) {
            return;
        }
        String openapi = filterConfig.getInitParameter("openapi");
        if (openapi == null || openapi.isBlank()) {
            openapi = "classpath:openapi.json";
        }
        String enabled = filterConfig.getInitParameter("enabled");
        if (enabled != null && enabled.equalsIgnoreCase("false")) {
            this.engine = ApiDogWatchEngine.create(openapi,
                    io.apidogwatch.ApiDogWatchConfig.builder().enabled(false).openApiLocation(openapi).build());
        } else {
            this.engine = ApiDogWatchEngine.create(openapi);
        }
        filterConfig.getServletContext().setAttribute(ENGINE_ATTRIBUTE, engine);
    }

    public ApiDogWatchEngine getEngine() {
        return engine;
    }

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request,
                         jakarta.servlet.ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)
                || engine == null
                || !engine.getConfig().isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        String path = httpRequest.getRequestURI();
        if (engine.shouldIgnore(path)) {
            chain.doFilter(request, response);
            return;
        }

        BufferedRequest wrappedRequest = new BufferedRequest(httpRequest, engine.getConfig().getMaxBodyChars());
        BufferedResponse wrappedResponse = new BufferedResponse(httpResponse);
        long started = System.currentTimeMillis();
        try {
            chain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long duration = Math.max(0, System.currentTimeMillis() - started);
            String contentType = wrappedResponse.getContentType();
            engine.inspectAndRecord(new ApiDogWatchEngine.Capture(
                    wrappedRequest.getMethod(),
                    path,
                    wrappedRequest.getQueryString(),
                    wrappedResponse.getStatus(),
                    duration,
                    contentType,
                    wrappedRequest.getCachedBodyAsString(),
                    wrappedResponse.getCachedBodyAsString()
            ));
            wrappedResponse.copyBodyToResponse();
        }
    }

    private static final class BufferedRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        private BufferedRequest(HttpServletRequest request, int maxBytes) throws IOException {
            super(request);
            this.cachedBody = BodyBuffers.readAll(request.getInputStream(), maxBytes);
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                }

                @Override
                public int read() {
                    return input.read();
                }
            };
        }

        private String getCachedBodyAsString() {
            return BodyBuffers.asString(cachedBody, getContentType());
        }
    }

    private static final class BufferedResponse extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private ServletOutputStream outputStream;
        private PrintWriter writer;
        private int status = SC_OK;

        private BufferedResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setStatus(int sc) {
            this.status = sc;
            super.setStatus(sc);
        }

        @Override
        public int getStatus() {
            return status;
        }

        @Override
        public ServletOutputStream getOutputStream() {
            if (writer != null) {
                throw new IllegalStateException("getWriter() already called");
            }
            if (outputStream == null) {
                outputStream = new ServletOutputStream() {
                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(WriteListener writeListener) {
                    }

                    @Override
                    public void write(int b) {
                        buffer.write(b);
                    }
                };
            }
            return outputStream;
        }

        @Override
        public PrintWriter getWriter() {
            if (outputStream != null) {
                throw new IllegalStateException("getOutputStream() already called");
            }
            if (writer == null) {
                writer = new PrintWriter(buffer, false, resolveCharset());
            }
            return writer;
        }

        private Charset resolveCharset() {
            String encoding = getCharacterEncoding();
            if (encoding == null) {
                return StandardCharsets.UTF_8;
            }
            try {
                return Charset.forName(encoding);
            } catch (Exception ex) {
                return StandardCharsets.UTF_8;
            }
        }

        private String getCachedBodyAsString() {
            if (writer != null) {
                writer.flush();
            }
            return BodyBuffers.asString(buffer.toByteArray(), getContentType());
        }

        private void copyBodyToResponse() throws IOException {
            if (writer != null) {
                writer.flush();
            }
            byte[] body = buffer.toByteArray();
            HttpServletResponse raw = (HttpServletResponse) getResponse();
            raw.setStatus(status);
            if (body.length > 0) {
                raw.setContentLength(body.length);
                raw.getOutputStream().write(body);
                raw.getOutputStream().flush();
            }
        }
    }
}
