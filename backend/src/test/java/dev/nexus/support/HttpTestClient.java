package dev.nexus.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thin JSON wrapper over the JDK HTTP client. Deliberately does not manage cookies:
 * the tests assert on Set-Cookie attributes and replay cookies by hand.
 */
public class HttpTestClient {

    public record Response(
            int status, String rawBody, Map<String, Object> body, List<String> setCookie, HttpHeaders headers) {

        /** First value only, matched case-insensitively as HTTP header names are. */
        public Optional<String> header(String name) {
            return headers.firstValue(name);
        }

        public Optional<String> refreshCookie() {
            return setCookie.stream().filter(cookie -> cookie.startsWith("nexus_refresh=")).findFirst();
        }

        /** The {@code name=value} head of the refresh cookie, ready to send back. */
        public String refreshCookiePair() {
            return refreshCookie().orElseThrow().split(";", 2)[0];
        }

        public String accessToken() {
            return (String) body.get("accessToken");
        }

        @SuppressWarnings("unchecked")
        public Map<String, Object> fieldErrors() {
            return (Map<String, Object>) body.getOrDefault("fieldErrors", Map.of());
        }

        /** For endpoints that answer with a JSON array rather than an object. */
        @SuppressWarnings("unchecked")
        public List<Map<String, Object>> list() {
            try {
                return new ObjectMapper().readValue(rawBody, List.class);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException("Response body is not a JSON array: " + rawBody, e);
            }
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final String baseUri;
    private final String rootUri;

    public HttpTestClient(int port) {
        this.rootUri = "http://localhost:" + port;
        this.baseUri = rootUri + dev.nexus.config.ApiPaths.PREFIX;
    }

    /** Hits a path outside the /api prefix — the app shell, actuator, static assets. */
    public Response getRoot(String path, String... headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(rootUri + path));
        for (int i = 0; i + 1 < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        return send(builder.GET());
    }

    /** A form post to a path outside the /api prefix, as a browser submits a plain HTML form. */
    public Response postFormRoot(String path, Map<String, String> form, String... headers) {
        String body = form.entrySet().stream()
                .map(field -> java.net.URLEncoder.encode(field.getKey(), java.nio.charset.StandardCharsets.UTF_8)
                        + "=" + java.net.URLEncoder.encode(field.getValue(), java.nio.charset.StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&"));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(rootUri + path))
                .header("Content-Type", "application/x-www-form-urlencoded");
        for (int i = 0; i + 1 < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        return send(builder.POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    public Response patchJson(String path, Map<String, ?> payload, String... headers) {
        return send(request(path, headers)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(write(payload))));
    }

    public Response putJson(String path, Map<String, ?> payload, String... headers) {
        return send(request(path, headers)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(write(payload))));
    }

    public Response delete(String path, String... headers) {
        return send(request(path, headers).DELETE());
    }

    /** Deleting something that asks to be confirmed first, which needs a body to confirm with. */
    public Response deleteJson(String path, Map<String, ?> payload, String... headers) {
        return send(request(path, headers)
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(write(payload))));
    }

    public Response postJson(String path, Map<String, ?> payload, String... headers) {
        return send(request(path, headers)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(write(payload))));
    }

    /**
     * A JSON body sent with no declared {@code Content-Length} — chunked transfer, as a
     * client streaming from something without a known size would send it.
     */
    public Response postJsonChunked(String path, String rawJsonBody, String... headers) {
        HttpRequest.BodyPublisher unknownLength =
                HttpRequest.BodyPublishers.fromPublisher(HttpRequest.BodyPublishers.ofString(rawJsonBody));
        return send(request(path, headers).header("Content-Type", "application/json").POST(unknownLength));
    }

    /** A single-file multipart upload, built by hand since the JDK client has no support of its own. */
    public Response postMultipart(String path, String fieldName, String filename, byte[] content, String... headers) {
        String boundary = "NexusTestBoundary" + System.nanoTime();
        byte[] head = ("--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + filename
                        + "\"\r\nContent-Type: text/csv\r\n\r\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + content.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(content, 0, body, head.length, content.length);
        System.arraycopy(tail, 0, body, head.length + content.length, tail.length);

        return send(request(path, headers)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)));
    }

    public Response post(String path, String... headers) {
        return send(request(path, headers).POST(HttpRequest.BodyPublishers.noBody()));
    }

    public Response get(String path, String... headers) {
        return send(request(path, headers).GET());
    }

    /** A response read as bytes, for the routes that answer with a file rather than JSON. */
    public record BinaryResponse(int status, byte[] body, HttpHeaders headers) {

        public Optional<String> header(String name) {
            return headers.firstValue(name);
        }
    }

    public BinaryResponse getBytes(String path, String... headers) {
        try {
            HttpResponse<byte[]> response =
                    client.send(request(path, headers).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            return new BinaryResponse(response.statusCode(), response.body(), response.headers());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private HttpRequest.Builder request(String path, String... headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUri + path));
        for (int i = 0; i + 1 < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        return builder;
    }

    private Response send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(
                    response.statusCode(),
                    response.body(),
                    read(response.body()),
                    response.headers().allValues("set-cookie"),
                    response.headers());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private String write(Map<String, ?> payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /** Object bodies parse; array bodies stay reachable through {@code rawBody}/{@code list()}. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> read(String body) {
        if (body == null || body.isBlank() || body.startsWith("[")) {
            return Map.of();
        }
        try {
            return mapper.readValue(body, Map.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return Map.of("raw", body);
        }
    }
}
