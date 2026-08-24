package dev.nexus.modules.anime;

import dev.nexus.core.adapter.CanonicalRef;
import dev.nexus.core.adapter.ExternalItemRef;
import dev.nexus.core.adapter.ItemResolver;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.Source;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Maps MAL entries onto their AniList canonicals — the resolver with real work to do.
 *
 * <p>Two passes, in the order AL-MAL-Sync's strategy chain establishes: trusted before
 * clever. First the hard join — AniList stores the MAL id of the same work, so a whole
 * list resolves fifty at a time and an id match is trusted by construction. Only what the
 * join missed goes to the fallback: an AniList title search judged by {@link TitleMatcher},
 * which is where splits, renames and specials live. Whatever survives neither lands in the
 * unmatched report, which is the intended escape hatch rather than a failure.
 */
@Component
public class MalToAniListResolver implements ItemResolver {

    private static final Logger log = LoggerFactory.getLogger(MalToAniListResolver.class);

    /** Candidates per title search; past the first handful it is noise, not options. */
    private static final int SEARCH_LIMIT = 10;

    private final AniListClient client;

    public MalToAniListResolver(AniListClient client) {
        this.client = client;
    }

    @Override
    public Provider provider() {
        return Provider.MAL;
    }

    @Override
    public Map<ExternalItemRef, CanonicalRef> resolveAll(Collection<ExternalItemRef> refs) {
        // Anime and manga resolve separately: MAL numbers them in separate id spaces, so
        // the join is only meaningful within one type.
        Map<MediaType, List<ExternalItemRef>> byType = new LinkedHashMap<>();
        for (ExternalItemRef ref : refs) {
            byType.computeIfAbsent(typeOf(ref), type -> new ArrayList<>()).add(ref);
        }

        Map<ExternalItemRef, CanonicalRef> resolved = new HashMap<>();
        byType.forEach((mediaType, group) -> resolveType(mediaType, group, resolved));
        return resolved;
    }

    private void resolveType(
            MediaType mediaType, List<ExternalItemRef> refs, Map<ExternalItemRef, CanonicalRef> resolved) {

        Map<String, String> joined = joinByMalId(mediaType, refs);

        List<ExternalItemRef> unresolved = new ArrayList<>();
        for (ExternalItemRef ref : refs) {
            String anilistId = joined.get(ref.providerItemId());
            if (anilistId != null) {
                resolved.put(ref, new CanonicalRef(Source.ANILIST, anilistId));
            } else {
                unresolved.add(ref);
            }
        }

        log.debug(
                "MAL {} join resolved {} of {}; trying titles for the rest",
                mediaType,
                refs.size() - unresolved.size(),
                refs.size());

        for (ExternalItemRef ref : unresolved) {
            Map<String, Object> match = searchByTitle(mediaType, ref);
            if (match != null) {
                resolved.put(ref, new CanonicalRef(Source.ANILIST, String.valueOf(match.get("id"))));
            }
        }
    }

    /** The hard join: one AniList query per fifty ids, answering with its own id per MAL id. */
    private Map<String, String> joinByMalId(MediaType mediaType, List<ExternalItemRef> refs) {
        List<String> malIds = refs.stream().map(ExternalItemRef::providerItemId).toList();

        Map<String, String> anilistIdByMalId = new HashMap<>();
        for (Map<String, Object> media : client.findMediaByMalIds(mediaType, malIds)) {
            if (media.get("idMal") != null && media.get("id") != null) {
                anilistIdByMalId.put(String.valueOf(media.get("idMal")), String.valueOf(media.get("id")));
            }
        }
        return anilistIdByMalId;
    }

    /**
     * The fallback for what the join missed: search AniList by the MAL title and take the
     * first candidate the matcher accepts. An id agreement discovered here is trusted the
     * way the join is; everything else must argue its way past the guards.
     */
    private Map<String, Object> searchByTitle(MediaType mediaType, ExternalItemRef ref) {
        TitleMatcher.Titles sourceTitles = sourceTitles(ref);

        for (Map<String, Object> candidate : client.searchMedia(mediaType, ref.title(), SEARCH_LIMIT)) {
            if (String.valueOf(ref.providerItemId()).equals(String.valueOf(candidate.get("idMal")))) {
                return candidate;
            }
            if (accepts(mediaType, ref, sourceTitles, candidate)) {
                log.debug("Matched MAL {} '{}' onto AniList {} by title", ref.providerItemId(), ref.title(), candidate.get("id"));
                return candidate;
            }
        }
        return null;
    }

    private boolean accepts(
            MediaType mediaType, ExternalItemRef ref, TitleMatcher.Titles source, Map<String, Object> candidate) {

        TitleMatcher.Titles candidateTitles = candidateTitles(candidate);

        if (mediaType == MediaType.MANGA) {
            return TitleMatcher.sameManga(
                    source,
                    hint(ref, MalLibraryImportAdapter.HINT_CHAPTERS),
                    hint(ref, MalLibraryImportAdapter.HINT_VOLUMES),
                    candidateTitles,
                    count(candidate.get("chapters")),
                    count(candidate.get("volumes")));
        }

        int sourceEpisodes = hint(ref, MalLibraryImportAdapter.HINT_EPISODES);
        int candidateEpisodes = count(candidate.get("episodes"));
        return TitleMatcher.sameAnime(source, sourceEpisodes, candidateTitles, candidateEpisodes)
                && !TitleMatcher.specialMatchedToSeries(source, sourceEpisodes, candidateTitles, candidateEpisodes);
    }

    /** MAL's main title is romaji by convention; English and native travel as hints. */
    private TitleMatcher.Titles sourceTitles(ExternalItemRef ref) {
        return new TitleMatcher.Titles(
                ref.hints().get(MalLibraryImportAdapter.HINT_TITLE_EN),
                ref.hints().get(MalLibraryImportAdapter.HINT_TITLE_JA),
                ref.title());
    }

    private TitleMatcher.Titles candidateTitles(Map<String, Object> candidate) {
        if (!(candidate.get("title") instanceof Map<?, ?> titles)) {
            return new TitleMatcher.Titles(null, null, null);
        }
        return new TitleMatcher.Titles(
                stringOrNull(titles.get("english")),
                stringOrNull(titles.get("native")),
                stringOrNull(titles.get("romaji")));
    }

    private MediaType typeOf(ExternalItemRef ref) {
        String hint = ref.hints().get(MalLibraryImportAdapter.HINT_MEDIA_TYPE);
        return MediaType.MANGA.name().equals(hint) ? MediaType.MANGA : MediaType.ANIME;
    }

    private int hint(ExternalItemRef ref, String key) {
        try {
            return Integer.parseInt(ref.hints().getOrDefault(key, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int count(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private String stringOrNull(Object value) {
        return value == null ? null : value.toString();
    }
}
