package dev.nexus.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
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

    public record Response(int status, String rawBody, Map<String, Object> body, List<String> setCookie) {

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

    public HttpTestClient(int port) {
        this.baseUri = "http://localhost:" + port + "/api";
    }

    public Response patchJson(String path, Map<String, ?> payload, String... headers) {
        return send(request(path, headers)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(write(payload))));
    }

    public Response delete(String path, String... headers) {
        return send(request(path, headers).DELETE());
    }

    public Response postJson(String path, Map<String, ?> payload, String... headers) {
        return send(request(path, headers)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(write(payload))));
    }

    public Response post(String path, String... headers) {
        return send(request(path, headers).POST(HttpRequest.BodyPublishers.noBody()));
    }

    public Response get(String path, String... headers) {
        return send(request(path, headers).GET());
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
                    response.headers().allValues("set-cookie"));
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
