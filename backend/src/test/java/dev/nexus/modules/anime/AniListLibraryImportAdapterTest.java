package dev.nexus.modules.anime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.nexus.core.adapter.ImportedEntry;
import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.TrackingStatus;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AniListLibraryImportAdapterTest {

    private AniListClient client;
    private AniListLibraryImportAdapter adapter;
    private ExternalAccount account;

    @BeforeEach
    void setUp() {
        client = mock(AniListClient.class);
        adapter = new AniListLibraryImportAdapter(client);

        account = new ExternalAccount(1L, Provider.ANILIST, "reader");
        account.setAccessToken("tok");

        when(client.fetchList(anyString(), any(), anyString())).thenReturn(List.of());
    }

    @Test
    void bothListsArePulledBecauseTheModuleOwnsBothTypes() {
        when(client.fetchList("reader", MediaType.ANIME, "tok")).thenReturn(List.of(row(true)));
        when(client.fetchList("reader", MediaType.MANGA, "tok")).thenReturn(List.of(row(false)));

        assertThat(adapter.pullLibrary(account)).hasSize(2);
    }

    @Test
    void anAnimeEntryCarriesEpisodesAndItsCanonicalId() {
        when(client.fetchList(eq("reader"), eq(MediaType.ANIME), anyString())).thenReturn(List.of(row(true)));

        ImportedEntry entry = adapter.pullLibrary(account).getFirst();

        assertThat(entry.itemRef().provider()).isEqualTo(Provider.ANILIST);
        assertThat(entry.itemRef().providerItemId()).isEqualTo("21");
        assertThat(entry.itemRef().title()).isEqualTo("One Piece");
        assertThat(entry.status()).isEqualTo(TrackingStatus.IN_PROGRESS);
        assertThat(entry.progressUnit()).isEqualTo(ProgressUnit.EPISODES);
        assertThat(entry.progressCurrent()).isEqualTo(430);
        assertThat(entry.progressMax()).isEqualTo(1000);
        assertThat(entry.rawRating()).isEqualTo(85);
        assertThat(entry.rawRatingMax()).isEqualTo(100);
        assertThat(entry.startedAt()).isEqualTo(LocalDate.of(2021, 3, 4));
    }

    @Test
    void aMangaEntryCountsChaptersInstead() {
        when(client.fetchList(eq("reader"), eq(MediaType.MANGA), anyString())).thenReturn(List.of(row(false)));

        ImportedEntry entry = adapter.pullLibrary(account).getFirst();

        assertThat(entry.progressUnit()).isEqualTo(ProgressUnit.CHAPTERS);
        assertThat(entry.progressMax()).isEqualTo(370);
    }

    /** The hint costs nothing here and spares the MyAnimeList import a lookup later. */
    @Test
    void theMalIdTravelsAlongAsAHint() {
        when(client.fetchList(eq("reader"), eq(MediaType.ANIME), anyString())).thenReturn(List.of(row(true)));

        assertThat(adapter.pullLibrary(account).getFirst().itemRef().hints()).containsEntry("malId", "21");
    }

    /** A rewatch is still watching; the distinction is AniList's and has nowhere to live here. */
    @Test
    void repeatingCountsAsInProgress() {
        assertThat(statusFor("REPEATING")).isEqualTo(TrackingStatus.IN_PROGRESS);
        assertThat(statusFor("CURRENT")).isEqualTo(TrackingStatus.IN_PROGRESS);
        assertThat(statusFor("COMPLETED")).isEqualTo(TrackingStatus.COMPLETED);
        assertThat(statusFor("PAUSED")).isEqualTo(TrackingStatus.PAUSED);
        assertThat(statusFor("DROPPED")).isEqualTo(TrackingStatus.DROPPED);
        assertThat(statusFor("PLANNING")).isEqualTo(TrackingStatus.PLANNING);
    }

    /** AniList reports an unscored entry as zero, which is a rating nobody meant to give. */
    @Test
    void anUnscoredEntryHasNoRatingRatherThanZero() {
        Map<String, Object> unscored = row(true);
        unscored.put("score", 0);
        when(client.fetchList(eq("reader"), eq(MediaType.ANIME), anyString())).thenReturn(List.of(unscored));

        assertThat(adapter.pullLibrary(account).getFirst().rawRating()).isNull();
    }

    @Test
    void aHalfKnownDateIsLeftEmpty() {
        Map<String, Object> partial = row(true);
        partial.put("startedAt", new HashMap<>(Map.of("year", 2021)));
        when(client.fetchList(eq("reader"), eq(MediaType.ANIME), anyString())).thenReturn(List.of(partial));

        assertThat(adapter.pullLibrary(account).getFirst().startedAt()).isNull();
    }

    @Test
    void aRowWithoutMediaIsSkippedRatherThanImportedEmpty() {
        when(client.fetchList(eq("reader"), eq(MediaType.ANIME), anyString()))
                .thenReturn(List.of(new HashMap<>(Map.of("status", "CURRENT"))));

        assertThat(adapter.pullLibrary(account)).isEmpty();
    }

    private TrackingStatus statusFor(String anilistStatus) {
        Map<String, Object> row = row(true);
        row.put("status", anilistStatus);
        when(client.fetchList(eq("reader"), eq(MediaType.ANIME), anyString())).thenReturn(List.of(row));
        return adapter.pullLibrary(account).getFirst().status();
    }

    private static Map<String, Object> row(boolean anime) {
        Map<String, Object> media = new HashMap<>();
        media.put("id", 21);
        media.put("idMal", 21);
        media.put("type", anime ? "ANIME" : "MANGA");
        media.put("episodes", 1000);
        media.put("chapters", 370);
        media.put("title", new HashMap<>(Map.of("english", "One Piece", "romaji", "One Piece")));

        Map<String, Object> row = new HashMap<>();
        row.put("status", "CURRENT");
        row.put("progress", 430);
        row.put("score", 85);
        row.put("startedAt", new HashMap<>(Map.of("year", 2021, "month", 3, "day", 4)));
        row.put("media", media);
        return row;
    }
}
