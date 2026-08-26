package dev.nexus.modules.books;

import dev.nexus.core.adapter.FetchProgress;
import dev.nexus.core.adapter.ItemSearchResult;
import dev.nexus.core.adapter.MetadataAdapter;
import dev.nexus.core.adapter.TrackableItemData;
import dev.nexus.core.domain.ItemState;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** The canonical catalogue for books. One media type, unlike TMDB's two and AniList's two. */
@Component
public class OpenLibraryMetadataAdapter implements MetadataAdapter {

    /** Open Library rates on 0-5; core stores 0-100, so ratings scale once, here. */
    private static final int RATING_SCALE = 20;

    /** Open Library's subject lists run to hundreds of entries; a shelf needs a handful. */
    private static final int MAX_SUBJECTS = 8;

    private final OpenLibraryClient client;
    private final OpenLibraryProperties properties;

    public OpenLibraryMetadataAdapter(OpenLibraryClient client, OpenLibraryProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public Set<MediaType> mediaTypes() {
        return Set.of(MediaType.BOOK);
    }

    @Override
    public Source source() {
        return Source.OPEN_LIBRARY;
    }

    @Override
    public List<ItemSearchResult> search(MediaType mediaType, String query, int limit) {
        return client.search(query, limit).stream()
                .map(doc -> new ItemSearchResult(
                        MediaType.BOOK,
                        Source.OPEN_LIBRARY,
                        workId(doc),
                        title(doc),
                        properties.coverUrl(doc.get("cover_i")),
                        publishedDate(doc)))
                .filter(result -> result.title() != null && result.externalId() != null)
                .toList();
    }

    /**
     * One book, with its description — the field the search endpoint does not carry, and the
     * one thing worth a second request when a reader has asked for this title specifically.
     */
    @Override
    public Optional<TrackableItemData> fetchById(String externalId) {
        return client.findByWorkId(externalId).map(doc -> toItemData(doc, description(externalId)));
    }

    /**
     * The bulk path, batched by the client into one request per twenty books.
     *
     * <p>Descriptions are deliberately absent here. Fetching them would cost a request per book
     * and undo the batching entirely, for a paragraph that no list view shows; a book opened
     * later fills it in through {@link #fetchById}.
     */
    @Override
    public List<TrackableItemData> fetchByIds(Collection<String> externalIds) {
        return client.findByWorkIds(externalIds).stream()
                .map(doc -> toItemData(doc, null))
                .filter(data -> data.title() != null && data.externalId() != null)
                .toList();
    }

    @Override
    public List<TrackableItemData> fetchByIds(Collection<String> externalIds, FetchProgress progress) {
        List<String> ids = List.copyOf(externalIds);
        List<TrackableItemData> fetched = new ArrayList<>();

        // Reported per batch rather than once at the end, because a batch is a real unit of
        // waiting: a five-hundred-book import is twenty-five requests, not one.
        for (int start = 0; start < ids.size(); start += 20) {
            List<String> batch = ids.subList(start, Math.min(start + 20, ids.size()));
            fetched.addAll(fetchByIds(batch));
            progress.report(Math.min(start + batch.size(), ids.size()), ids.size());
        }
        return fetched;
    }

    TrackableItemData toItemData(Map<String, Object> doc, String description) {
        return new TrackableItemData(
                MediaType.BOOK,
                Source.OPEN_LIBRARY,
                workId(doc),
                title(doc),
                properties.coverUrl(doc.get("cover_i")),
                publishedDate(doc),
                itemState(doc),
                metadata(doc, description));
    }

    /**
     * Open Library keys a work as {@code /works/OL893414W}; only the id is stored. The prefix
     * is the same for every work and would sit in a URL path where slashes are structural.
     */
    private String workId(Map<String, Object> doc) {
        Object key = doc.get("key");
        if (key == null) {
            return null;
        }
        String text = key.toString();
        return text.startsWith("/works/") ? text.substring("/works/".length()) : text;
    }

    private String title(Map<String, Object> doc) {
        Object title = doc.get("title");
        return title == null || title.toString().isBlank() ? null : title.toString();
    }

    /**
     * Open Library records a first publication year and no finer, so a book dates to that
     * January. Wrong by up to eleven months, and still the only thing that sorts a shelf
     * chronologically.
     */
    private LocalDate publishedDate(Map<String, Object> doc) {
        if (!(doc.get("first_publish_year") instanceof Number year)) {
            return null;
        }
        int value = year.intValue();
        // Open Library carries genuinely ancient works, and LocalDate will not take a year 0.
        return value < 1 ? null : LocalDate.of(value, 1, 1);
    }

    /**
     * A book is the one media type here that is genuinely finished on release: it gains no
     * episodes and no chapters, so a published book is RELEASED and never refreshed again.
     */
    private ItemState itemState(Map<String, Object> doc) {
        LocalDate published = publishedDate(doc);
        return published != null && published.isAfter(LocalDate.now()) ? ItemState.UPCOMING : ItemState.RELEASED;
    }

    private Map<String, Object> metadata(Map<String, Object> doc, String description) {
        Map<String, Object> metadata = new HashMap<>();
        putIfPresent(metadata, "summary", description);
        putIfPresent(metadata, "authors", strings(doc.get("author_name")));
        putIfPresent(metadata, "genres", strings(doc.get("subject")).stream()
                .limit(MAX_SUBJECTS)
                .toList());
        putIfPresent(metadata, "format", "Book");

        // Editions of one work differ in length; the median is the honest single number.
        putIfPresent(metadata, "pageCount", doc.get("number_of_pages_median"));

        if (doc.get("ratings_average") instanceof Number rating && rating.doubleValue() > 0) {
            metadata.put("externalRating", Math.round(rating.doubleValue() * RATING_SCALE));
        }
        return metadata;
    }

    /**
     * Open Library writes a description either as a plain string or as a typed record with the
     * text under {@code value}, depending on when the record was last edited. Both are current
     * and both appear in live data.
     */
    private String description(String workId) {
        return client.fetchWork(workId)
                .map(work -> work.get("description"))
                .map(raw -> raw instanceof Map<?, ?> typed ? typed.get("value") : raw)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(text -> !text.isBlank())
                .orElse(null);
    }

    private List<String> strings(Object raw) {
        if (!(raw instanceof List<?> entries)) {
            return List.of();
        }
        return entries.stream().filter(Objects::nonNull).map(Object::toString).toList();
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value instanceof List<?> list && list.isEmpty()) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        if (value != null) {
            target.put(key, value);
        }
    }
}
