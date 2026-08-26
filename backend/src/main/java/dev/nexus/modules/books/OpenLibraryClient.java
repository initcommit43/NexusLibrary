package dev.nexus.modules.books;

import dev.nexus.core.web.OutboundRateLimiter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Speaks Open Library's search and works APIs.
 *
 * <p>Two endpoints with different shapes. {@code /search.json} answers with works already
 * flattened — author names, cover id and page count inlined — which is what every list view
 * and every import row needs. {@code /works/{id}.json} answers with the raw record, where
 * authors are unresolved references and the description lives; it is worth a second call only
 * when someone is actually reading the page.
 *
 * <p>Unusually for this codebase, the search endpoint takes several keys at once, so an
 * import batches rather than paying a call per book.
 */
@Component
public class OpenLibraryClient {

    /**
     * Fields asked for by name. Open Library returns a very wide record otherwise — every
     * edition's ISBN, every contributor — and a library-sized import would move megabytes of
     * fields nothing here reads.
     */
    private static final String FIELDS =
            "key,title,author_name,first_publish_year,cover_i,number_of_pages_median,subject,ratings_average";

    /** How many work keys go into one batched search. Longer URLs start being refused. */
    private static final int BATCH_SIZE = 20;

    private final RestClient restClient;
    private final OpenLibraryProperties properties;
    private final OutboundRateLimiter rateLimiter;

    public OpenLibraryClient(RestClient.Builder builder, OpenLibraryProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
        this.rateLimiter = new OutboundRateLimiter(properties.requestsPerSecond());
    }

    /** Free-text search, the search box's path. */
    public List<Map<String, Object>> search(String query, int limit) {
        return docs(searchJson(limit, "q", query));
    }

    /**
     * The work carrying this Goodreads id. Open Library records the Goodreads id of editions
     * catalogued from Goodreads data, which is what lets an export resolve exactly rather than
     * by matching titles.
     */
    public Optional<Map<String, Object>> findByGoodreadsId(String goodreadsId) {
        return docs(searchJson(1, "q", "id_goodreads:" + goodreadsId)).stream().findFirst();
    }

    /**
     * The work carrying this ISBN. An ISBN names one edition, and Open Library answers with the
     * work above it — which is the right level: a reader tracked the book, not the printing.
     */
    public Optional<Map<String, Object>> findByIsbn(String isbn) {
        return docs(searchJson(1, "q", "isbn:" + isbn)).stream().findFirst();
    }

    /**
     * Title and author, for a row with neither id. Open Library takes these as separate
     * parameters rather than as operators inside the query, and weights them accordingly.
     */
    public List<Map<String, Object>> findByTitleAndAuthor(String title, String author, int limit) {
        return author == null || author.isBlank()
                ? docs(searchJson(limit, "title", title))
                : docs(searchJson(limit, "title", title, "author", author));
    }

    /** One work, by the id stored as a canonical — {@code OL893414W}, without the path. */
    public Optional<Map<String, Object>> findByWorkId(String workId) {
        return findByWorkIds(List.of(workId)).stream().findFirst();
    }

    /**
     * Many works in as few calls as the URL length allows. The search endpoint accepts an OR of
     * key lookups, so a two-hundred-book import costs ten requests rather than two hundred.
     */
    public List<Map<String, Object>> findByWorkIds(Collection<String> workIds) {
        List<String> ids = workIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        List<Map<String, Object>> found = new ArrayList<>();

        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            List<String> batch = ids.subList(start, Math.min(start + BATCH_SIZE, ids.size()));
            String query = String.join(
                    " OR ", batch.stream().map(id -> "key:/works/" + id).toList());
            found.addAll(docs(searchJson(batch.size(), "q", query)));
        }
        return found;
    }

    /**
     * The raw work record, for the description alone — the one field the search endpoint does
     * not carry. Empty when Open Library has no such work.
     */
    public Optional<Map<String, Object>> fetchWork(String workId) {
        return get(UriComponentsBuilder.fromUriString(properties.apiBaseUrl())
                .path("/works/{id}.json")
                .build(workId));
    }

    /** Query parameters as alternating name and value, encoded exactly once on the way out. */
    private Map<String, Object> searchJson(int limit, String... queryParams) {
        UriComponentsBuilder builder =
                UriComponentsBuilder.fromUriString(properties.apiBaseUrl()).path("/search.json");
        for (int i = 0; i + 1 < queryParams.length; i += 2) {
            builder.queryParam(queryParams[i], queryParams[i + 1]);
        }
        return get(builder.queryParam("fields", FIELDS)
                        .queryParam("limit", limit)
                        .build()
                        .encode()
                        .toUri())
                .orElse(Map.of());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> docs(Map<String, Object> body) {
        return body.get("docs") instanceof List<?> docs
                ? docs.stream()
                        .filter(Map.class::isInstance)
                        .map(doc -> (Map<String, Object>) doc)
                        .toList()
                : List.of();
    }

    /**
     * Takes a built {@link URI} rather than a template, so the query is encoded exactly once.
     * Handing {@code RestClient} an already-encoded string encodes it a second time and turns
     * every space in a title into {@code %2520}, which matches nothing and fails silently.
     */
    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> get(URI uri) {
        rateLimiter.acquire();

        try {
            return Optional.ofNullable(restClient
                    .get()
                    .uri(uri)
                    .header("User-Agent", properties.userAgent())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (status.value() == 404) {
                            // Not an outage: the work is gone, and the caller wants an empty.
                            return null;
                        }
                        if (status.isError()) {
                            throw new OpenLibraryUnavailableException(
                                    "Open Library responded with " + status.value(), statusMessage(response));
                        }
                        return (Map<String, Object>) response.bodyTo(Map.class);
                    }));
        } catch (RestClientException e) {
            throw new OpenLibraryUnavailableException("Open Library request failed", e);
        }
    }

    /**
     * Open Library answers an error with an {@code error} field where it says anything at all.
     * Often it says nothing and serves an HTML page, which is the gateway talking rather than
     * the service, and is not worth repeating to a reader.
     */
    private String statusMessage(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try {
            Map<?, ?> body = response.bodyTo(Map.class);
            return body != null && body.get("error") != null ? body.get("error").toString() : null;
        } catch (RestClientException | IllegalStateException e) {
            return null;
        }
    }
}
