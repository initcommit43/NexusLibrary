package dev.nexus.core.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses a JSON request body over the configured cap before a controller ever sees it.
 *
 * <p>Only requests declaring a JSON content type are bounded: the CSV import upload is the
 * only {@code multipart/form-data} route in the app, and multipart already has its own,
 * larger ceiling in {@code spring.servlet.multipart} — this filter never touches it.
 *
 * <p>A {@code Content-Length} already over the cap is refused immediately, before a byte of
 * the body is read. One that is missing or {@code -1} — chunked transfer, or a client that
 * never sends the header — must not be read as "no limit", so the body is wrapped and
 * counted as it is read, and a request that keeps sending past the cap is cut off mid-stream
 * instead.
 *
 * <p>Not a Spring bean on purpose, matching {@link dev.nexus.core.security.SiteGateFilter}:
 * Boot registers every {@code Filter} bean with the servlet container as well, and this
 * would then run twice.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;

    public RequestSizeLimitFilter(RequestSizeProperties properties) {
        this.maxBytes = properties.maxJsonBodySize().toBytes();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String contentType = request.getContentType();
        if (contentType == null
                || !contentType.toLowerCase(Locale.ROOT).startsWith(MediaType.APPLICATION_JSON_VALUE)) {
            chain.doFilter(request, response);
            return;
        }

        long declared = request.getContentLengthLong();
        if (declared > maxBytes) {
            reject(response);
            return;
        }

        // The usual path: a controller reading @RequestBody hits the limit mid-parse, and
        // GlobalExceptionHandler turns it into a 413 from inside the dispatch, so this never
        // propagates back out here. This catch is only a backstop for a body read outside
        // that machinery, where nothing else would turn the exception into a response.
        try {
            chain.doFilter(new SizeLimitedRequest(request, maxBytes), response);
        } catch (RequestTooLargeException e) {
            if (!response.isCommitted()) {
                reject(response);
            }
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"That request is too large.\"}");
    }

    /** Counts bytes as they are read and stops the request the moment they pass the cap. */
    private static final class SizeLimitedRequest extends HttpServletRequestWrapper {

        private final long maxBytes;

        SizeLimitedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new SizeLimitedInputStream(super.getInputStream(), maxBytes);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(
                    getInputStream(), encoding != null ? encoding : StandardCharsets.UTF_8.name()));
        }
    }

    private static final class SizeLimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private long read;

        SizeLimitedInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) {
            read += n;
            if (read > maxBytes) {
                throw new RequestTooLargeException();
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
