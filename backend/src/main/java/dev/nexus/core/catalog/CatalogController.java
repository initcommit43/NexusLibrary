package dev.nexus.core.catalog;

import dev.nexus.auth.CurrentUser;
import dev.nexus.core.adapter.BrowseResults;
import dev.nexus.core.adapter.BrowseShelf;
import dev.nexus.core.adapter.DiscoverFilters;
import dev.nexus.core.adapter.FilterField;
import dev.nexus.core.adapter.ItemSearchResult;
import dev.nexus.core.adapter.StudioBrowse;
import dev.nexus.core.adapter.MetadataAdapterRegistry;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.tracking.TrackingService;
import dev.nexus.core.tracking.dto.TrackedItemResponse;
import dev.nexus.modules.games.AchievementCatalogue;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import dev.nexus.core.web.RateLimiter;
import dev.nexus.core.web.ServerTimings;
import dev.nexus.config.NexusProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/catalog")
public class CatalogController {

    private static final int MAX_RESULTS = 20;

    /** How long a reader's own browser may reuse a shelf before asking for it again. */
    private static final Duration BROWSER_CACHE = Duration.ofMinutes(10);

    /** Past these a request is not a narrower question, it is a slower one. */
    private static final int MAX_FILTERS = 12;

    private static final int MAX_VALUES_PER_FILTER = 12;

    private static final int MAX_VALUE = 200;

    /** Query parameters this endpoint reads itself, and so never treats as a filter. */
    private static final Set<String> OWN_PARAMS = Set.of("mediaType", "page");

    /** A catalogue item, plus this reader's own entry for it when they have one. */
    public record MediaResponse(
            MediaType mediaType,
            Source source,
            String externalId,
            String title,
            String coverUrl,
            LocalDate releaseDate,
            String itemState,
            Map<String, Object> metadata,
            TrackedItemResponse entry) {}

    private final MetadataAdapterRegistry adapters;
    private final ServerTimings timings;
    private final MediaDetailService media;
    private final BrowseService browse;
    private final TrackingService tracking;
    private final RateLimiter rateLimiter;
    private final AchievementCatalogue achievements;
    private final int searchesPerMinute;

    public CatalogController(
            MetadataAdapterRegistry adapters,
            ServerTimings timings,
            MediaDetailService media,
            BrowseService browse,
            TrackingService tracking,
            RateLimiter rateLimiter,
            AchievementCatalogue achievements,
            NexusProperties properties) {
        this.adapters = adapters;
        this.timings = timings;
        this.media = media;
        this.browse = browse;
        this.tracking = tracking;
        this.rateLimiter = rateLimiter;
        this.achievements = achievements;
        this.searchesPerMinute = properties.rateLimit().searchRequestsPerMinute();
    }

    /**
     * Results are transient. An item is written to the shared cache when someone tracks it,
     * not because it appeared in a search, so browsing never fills the cache with noise.
     */
    @GetMapping("/search")
    public List<ItemSearchResult> search(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam MediaType mediaType,
            @RequestParam("q") @NotBlank @Size(max = 200) String query) {

        // Keyed by user rather than by IP: the endpoint is authenticated, and this is as much
        // about protecting the external API budget as about abuse.
        rateLimiter.check("search:" + user.id(), searchesPerMinute);

        return timings.time(
                "search", () -> adapters.requireForMediaType(mediaType).search(mediaType, query.trim(), MAX_RESULTS));
    }

    /**
     * One title, whether or not it is on a shelf. Relations point at things nobody here
     * tracks yet, so a page keyed by the catalogue rather than by an entry is what lets you
     * follow them.
     */
    @GetMapping("/media/{source}/{externalId}")
    public MediaResponse media(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Source source,
            @PathVariable String externalId) {

        TrackableItem item = media.require(source, externalId);
        Optional<TrackedItemResponse> entry = timings.time(
                "entry", () -> tracking.findByItem(user.id(), item.getId()).map(TrackedItemResponse::from));

        return new MediaResponse(
                item.getMediaType(),
                item.getSource(),
                item.getExternalId(),
                item.getTitle(),
                item.getCoverUrl(),
                item.getReleaseDate(),
                item.getItemState().name(),
                item.getMetadata(),
                entry.orElse(null));
    }

    /**
     * What there is to earn in one game, whether or not this reader owns it.
     *
     * <p>Asked for separately rather than carried by the response above: the list is the
     * same for everyone and cached on the item once fetched, but the first reader to open a
     * game nobody has imported pays a call to Steam for it, and that must not be a call the
     * page waits on before it can render anything at all.
     */
    @GetMapping("/media/{source}/{externalId}/achievements")
    public List<Map<String, Object>> achievements(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Source source,
            @PathVariable String externalId) {

        rateLimiter.check("achievements:" + user.id(), searchesPerMinute);
        return timings.time("achievements", () -> this.achievements.forMedia(source, externalId));
    }

    /** Which rows this module's browse page has, so the client renders what exists. */
    @GetMapping("/shelves")
    public List<BrowseShelf> shelves(@RequestParam MediaType mediaType) {
        return browse.shelves(mediaType);
    }

    /**
     * One browse shelf. Not rate-limited like search is: the answer is the same for everyone
     * and comes from memory, so a reader refreshing the page spends no external budget.
     */
    @GetMapping("/browse")
    public ResponseEntity<BrowseResults> browseShelf(
            @RequestParam MediaType mediaType,
            @RequestParam @NotBlank @Size(max = 50) String shelf,
            @RequestParam(defaultValue = "1") @Positive int page) {

        BrowseResults results = timings.time("browse", () -> browse.page(mediaType, shelf, page));

        /*
         * Held by the reader's own browser for a few minutes.
         *
         * <p>A shelf is the same list for everyone and changes by the day at most, so opening
         * home again should not be four round trips to be told the same thing. Private because
         * the route is behind a login, not because the answer is anybody's in particular.
         */
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(BROWSER_CACHE).cachePrivate())
                .body(results);
    }

    /**
     * What one studio made, newest first.
     *
     * <p>Rate-limited like search rather than free like a shelf: it is one reader's question
     * about one company, and every page of it costs a request to the source.
     */
    @GetMapping("/studios/{source}/{studioId}")
    public StudioBrowse.Works studioWorks(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Source source,
            @PathVariable @NotBlank @Size(max = 40) String studioId,
            @RequestParam(defaultValue = "1") @Positive int page) {

        rateLimiter.check("studio:" + user.id(), searchesPerMinute);
        return timings.time("studio", () -> browse.worksOf(source, studioId, page));
    }

    /** The controls this media type's browse bar offers, so the client renders what exists. */
    @GetMapping("/filters")
    public List<FilterField> filters(@RequestParam MediaType mediaType) {
        return browse.filters(mediaType);
    }

    /**
     * One page of a filtered browse grid.
     *
     * <p>Rate-limited the way search is rather than free the way a shelf is: a shelf is one
     * answer shared by everyone and served from memory, while every combination of filters is
     * a question only this reader asked, and costs a request to the source to answer.
     */
    @GetMapping("/discover")
    public BrowseResults discover(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam MediaType mediaType,
            @RequestParam(defaultValue = "1") @Positive int page,
            @RequestParam MultiValueMap<String, String> params) {

        rateLimiter.check("discover:" + user.id(), searchesPerMinute);

        DiscoverFilters filters = new DiscoverFilters(filterValues(params));
        return timings.time("discover", () -> browse.discover(mediaType, filters, page));
    }

    /**
     * Everything in the query string that is not this endpoint's own, handed on unread.
     *
     * <p>The names come from what the adapter published, so there is no list here to keep in
     * step with it. The caps are the only opinion core has about them: a value long enough to
     * be a paragraph, or a hundred of them, is not a narrower question but a slower one.
     */
    private static Map<String, List<String>> filterValues(MultiValueMap<String, String> params) {
        Map<String, List<String>> values = new LinkedHashMap<>();

        params.forEach((field, chosen) -> {
            if (OWN_PARAMS.contains(field) || values.size() >= MAX_FILTERS) {
                return;
            }
            values.put(
                    field,
                    chosen.stream()
                            .filter(value -> value != null && !value.isBlank() && value.length() <= MAX_VALUE)
                            .limit(MAX_VALUES_PER_FILTER)
                            .toList());
        });

        return values;
    }

    @GetMapping("/modules")
    public List<MediaType> availableModules() {
        return adapters.availableMediaTypes();
    }
}
