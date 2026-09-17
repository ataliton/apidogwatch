package io.apidogwatch.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Helpers for safely buffering request/response bodies during interception.
 */
public final class BodyBuffers {

    private BodyBuffers() {
    }

    public static byte[] readAll(InputStream inputStream, int maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int total = 0;
        int read;
        while ((read = inputStream.read(chunk)) != -1) {
            int allowed = Math.min(read, Math.max(0, maxBytes - total));
            if (allowed > 0) {
                buffer.write(chunk, 0, allowed);
                total += allowed;
            }
            if (total >= maxBytes) {
                break;
            }
        }
        return buffer.toByteArray();
    }

    public static String asString(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        Charset charset = StandardCharsets.UTF_8;
        if (contentType != null) {
            int idx = contentType.toLowerCase().indexOf("charset=");
            if (idx >= 0) {
                String raw = contentType.substring(idx + 8).trim();
                int end = raw.indexOf(';');
                if (end > 0) {
                    raw = raw.substring(0, end);
                }
                try {
                    charset = Charset.forName(raw.replace("\"", ""));
                } catch (Exception ignored) {
                    charset = StandardCharsets.UTF_8;
                }
            }
        }
        return new String(bytes, charset);
    }
}
