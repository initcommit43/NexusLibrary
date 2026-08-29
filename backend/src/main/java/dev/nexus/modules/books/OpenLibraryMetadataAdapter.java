package dev.nexus.modules.books;

import dev.nexus.core.adapter.BrowseResults;
import dev.nexus.core.adapter.BrowseShelf;
import dev.nexus.core.adapter.DiscoverFilters;
import dev.nexus.core.adapter.FilterField;
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
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** The canonical catalogue for books. One media type, unlike TMDB's two and AniList's two. */
@Component
public class OpenLibraryMetadataAdapter implements MetadataAdapter {

    /** Open Library rates on 0-5; core stores 0-100, so ratings scale once, here. */
    private static final int RATING_SCALE = 20;

    /** Open Library's subject lists run to hundreds of entries; a shelf needs a handful. */
    private static final int MAX_SUBJECTS = 8;

    /**
     * What a book's own page keeps of the work record's lists.
     *
     * <p>Open Library's subject lists run to hundreds on a classic — every edition's cataloguing
     * folded together — and the answer is cached on the shared item, so a page's worth is what
     * is kept rather than all of it.
     */
    private static final int MAX_DETAIL_SUBJECTS = 20;

    /**
     * A book with forty contributors would otherwise cost forty requests. The ones worth a
     * card are the ones on the spine.
     */
    private static final int MAX_AUTHORS = 3;

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


    @Override
    public List<BrowseShelf> browseShelves(MediaType mediaType) {
        return OpenLibraryShelves.shelves();
    }

    @Override
    public BrowseResults browse(MediaType mediaType, String shelfId, int page, int size) {
        OpenLibraryShelves.Definition shelf = OpenLibraryShelves.find(shelfId);
        if (shelf == null) {
            return BrowseResults.empty();
        }

        List<Map<String, Object>> works = shelf.isTrending()
                ? client.trending(shelf.window(), page, size)
                : client.subject(shelf.subject(), (page - 1) * size, size);

        // Open Library reports no total on either list, so a full page is the only sign there
        // is another behind it.
        return new BrowseResults(toSearchResults(works), works.size() >= size);
    }

    @Override
    public List<FilterField> discoverFilters(MediaType mediaType) {
        return OpenLibraryFilters.fields(LocalDate.now());
    }

    @Override
    public BrowseResults discover(MediaType mediaType, DiscoverFilters filters, int page, int size) {
        List<Map<String, Object>> works =
                client.discover(OpenLibraryFilters.query(filters), (page - 1) * size, size);

        return new BrowseResults(toSearchResults(works), works.size() >= size);
    }

    /** Search docs as the shared shape, shared by the shelves and the filtered grid. */
    private List<ItemSearchResult> toSearchResults(List<Map<String, Object>> works) {
        return works.stream()
                .map(doc -> new ItemSearchResult(
                        MediaType.BOOK,
                        Source.OPEN_LIBRARY,
                        workId(doc),
                        title(doc),
                        properties.coverUrl(doc.get("cover_i")),
                        publishedDate(doc),
                        facets(doc)))
                .filter(result -> result.title() != null && result.externalId() != null)
                .toList();
    }

    private Map<String, Object> facets(Map<String, Object> doc) {
        Map<String, Object> facets = new HashMap<>();
        if (doc.get("ratings_average") instanceof Number rating && rating.doubleValue() > 0) {
            facets.put("score", Math.round(rating.doubleValue() * RATING_SCALE));
        }
        putIfPresent(facets, "authors", strings(doc.get("author_name")));
        return Map.copyOf(facets);
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
     * Everything a book's own page shows beyond the fields core models.
     *
     * <p>Comes from the work record, which is the same request {@link #fetchById} already makes
     * for the description — the search endpoint carries none of this.
     */
    @Override
    public Optional<Map<String, Object>> fetchDetail(String externalId) {
        return client.fetchWork(externalId).map(work -> toDetail(externalId, work));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toDetail(String workId, Map<String, Object> work) {
        Map<String, Object> detail = new HashMap<>();

        putIfPresent(detail, "subjects", subjects(work));
        putIfPresent(detail, "links", links(work));
        putIfPresent(detail, "excerpt", excerpt(work));
        putIfPresent(detail, "firstPublished", work.get("first_publish_date"));
        putIfPresent(detail, "authors", authors(work));

        Map<String, Object> stats = new HashMap<>();
        ratings(workId, detail, stats);
        readingCounts(workId, stats);
        if (!stats.isEmpty()) {
            detail.put("stats", stats);
        }

        return detail;
    }

    /**
     * The people who wrote it, each with whatever Open Library knows about them.
     *
     * <p>The work record names them only by key, so a card costs a request per author — which
     * is why there are three at most, and why the answer is cached on the shared item like
     * everything else here.
     */
    private List<Map<String, Object>> authors(Map<String, Object> work) {
        if (!(work.get("authors") instanceof List<?> credited)) {
            return List.of();
        }

        return credited.stream()
                .filter(Map.class::isInstance)
                .map(entry -> ((Map<?, ?>) entry).get("author"))
                .filter(Map.class::isInstance)
                .map(author -> ((Map<?, ?>) author).get("key"))
                .filter(Objects::nonNull)
                .map(key -> key.toString().replace("/authors/", ""))
                .distinct()
                .limit(MAX_AUTHORS)
                .map(this::author)
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<String, Object> author(String authorId) {
        Map<String, Object> record = client.fetchAuthor(authorId).orElse(null);
        if (record == null || record.get("name") == null) {
            return null;
        }

        Map<String, Object> card = new HashMap<>();
        card.put("name", record.get("name").toString());
        card.put("image", properties.authorPhotoUrl(authorId));
        putIfPresent(card, "bio", prose(record.get("bio")));
        putIfPresent(card, "lived", lived(record));
        return card;
    }

    /** "1892 – 1973", or just the birth where the author is alive or the death unrecorded. */
    private String lived(Map<String, Object> record) {
        Object born = record.get("birth_date");
        Object died = record.get("death_date");

        if (born == null && died == null) {
            return null;
        }
        if (died == null) {
            return "Born " + born;
        }
        return born == null ? "Died " + died : born + " – " + died;
    }

    /**
     * The average, how many readers said so, and the spread across the five stars.
     *
     * <p>The spread is written in the shape the page's other sources write theirs, so the same
     * chart draws it without learning where it came from.
     */
    private void ratings(String workId, Map<String, Object> detail, Map<String, Object> stats) {
        Map<String, Object> ratings = client.fetchRatings(workId).orElse(Map.of());

        if (ratings.get("summary") instanceof Map<?, ?> summary) {
            putIfPresent(detail, "ratingAverage", ((Map<String, Object>) summary).get("average"));
            putIfPresent(detail, "ratingCount", ((Map<String, Object>) summary).get("count"));
        }

        if (!(ratings.get("counts") instanceof Map<?, ?> counts)) {
            return;
        }

        List<Map<String, Object>> spread = new ArrayList<>();
        for (int score = 1; score <= 5; score++) {
            Object amount = ((Map<String, Object>) counts).get(String.valueOf(score));
            if (amount instanceof Number number && number.intValue() > 0) {
                spread.add(Map.of("score", score, "amount", number.intValue()));
            }
        }
        putIfPresent(stats, "scoreDistribution", spread);
    }

    /**
     * How many readers want it, are reading it, or have finished it.
     *
     * <p>Open Library keeps the same three shelves this app does, so the counts are written
     * under the status names core already speaks and read back by the chart unchanged.
     */
    private void readingCounts(String workId, Map<String, Object> stats) {
        Map<String, Object> shelves = client.fetchReadingCounts(workId).orElse(Map.of());
        if (!(shelves.get("counts") instanceof Map<?, ?> counts)) {
            return;
        }

        Map<String, String> statuses = new java.util.LinkedHashMap<>();
        statuses.put("want_to_read", "PLANNING");
        statuses.put("currently_reading", "IN_PROGRESS");
        statuses.put("already_read", "COMPLETED");

        List<Map<String, Object>> distribution = new ArrayList<>();
        statuses.forEach((shelf, status) -> {
            Object amount = ((Map<String, Object>) counts).get(shelf);
            if (amount instanceof Number number && number.intValue() > 0) {
                distribution.add(Map.of("status", status, "amount", number.intValue()));
            }
        });
        putIfPresent(stats, "statusDistribution", distribution);
    }

    /** Open Library writes prose either bare or wrapped in a typed record, under {@code value}. */
    private String prose(Object raw) {
        Object text = raw instanceof Map<?, ?> typed ? typed.get("value") : raw;
        return text == null || text.toString().isBlank() ? null : text.toString();
    }

    /**
     * What the book is about, where it is set, and who is in it — one list, because the page
     * shows them as one and Open Library's own split between them is not one a reader asks for.
     */
    private List<String> subjects(Map<String, Object> work) {
        return Stream.of("subjects", "subject_people", "subject_places", "subject_times")
                .flatMap(key -> strings(work.get(key)).stream())
                .distinct()
                .limit(MAX_DETAIL_SUBJECTS)
                .toList();
    }

    private List<Map<String, Object>> links(Map<String, Object> work) {
        if (!(work.get("links") instanceof List<?> entries)) {
            return List.of();
        }

        return entries.stream()
                .filter(Map.class::isInstance)
                .map(entry -> (Map<?, ?>) entry)
                .filter(link -> link.get("url") != null)
                .map(link -> Map.<String, Object>of(
                        "site",
                        link.get("title") == null ? "Website" : link.get("title").toString(),
                        "url",
                        link.get("url").toString()))
                .toList();
    }

    /**
     * A passage from the book itself, which says more about how it reads than a synopsis does.
     *
     * <p>Written under {@code excerpt} or, on older records, under {@code value} — the same
     * two shapes the description arrives in.
     */
    private String excerpt(Map<String, Object> work) {
        if (!(work.get("excerpts") instanceof List<?> excerpts)) {
            return null;
        }

        return excerpts.stream()
                .filter(Map.class::isInstance)
                .map(entry -> (Map<?, ?>) entry)
                .map(entry -> entry.get("excerpt") == null ? entry.get("value") : entry.get("excerpt"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(text -> !text.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * Open Library writes a description either as a plain string or as a typed record with the
     * text under {@code value}, depending on when the record was last edited. Both are current
     * and both appear in live data.
     */
    private String description(String workId) {
        return client.fetchWork(workId)
                .map(work -> prose(work.get("description")))
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
