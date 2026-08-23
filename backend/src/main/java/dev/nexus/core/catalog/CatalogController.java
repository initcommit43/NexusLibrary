package dev.nexus.core.catalog;

import dev.nexus.auth.CurrentUser;
import dev.nexus.core.adapter.ItemSearchResult;
import dev.nexus.core.adapter.MetadataAdapterRegistry;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.web.RateLimiter;
import dev.nexus.config.NexusProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
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

    private final MetadataAdapterRegistry adapters;
    private final RateLimiter rateLimiter;
    private final int searchesPerMinute;

    public CatalogController(
            MetadataAdapterRegistry adapters, RateLimiter rateLimiter, NexusProperties properties) {
        this.adapters = adapters;
        this.rateLimiter = rateLimiter;
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

        return adapters.requireForMediaType(mediaType).search(mediaType, query.trim(), MAX_RESULTS);
    }

    @GetMapping("/modules")
    public List<MediaType> availableModules() {
        return adapters.availableMediaTypes();
    }
}
