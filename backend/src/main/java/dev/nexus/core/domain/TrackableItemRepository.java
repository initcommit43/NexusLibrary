package dev.nexus.core.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrackableItemRepository extends JpaRepository<TrackableItem, Long> {

    Optional<TrackableItem> findBySourceAndExternalId(Source source, String externalId);

    List<TrackableItem> findBySourceAndExternalIdIn(Source source, Collection<String> externalIds);

    /**
     * Episodes that have aired since anyone last looked, and who is waiting for them.
     *
     * <p>Read straight out of what every ongoing title already carries: the next episode and
     * the moment it lands ride on the item so a shelf can count down beside every row, which
     * makes "has it aired yet" a question the database can answer without asking AniList.
     *
     * <p>Every episode for a title on the Watching list; only the premiere for a title kept on
     * any other list. A planned or finished series is worth a word when it starts, not a
     * notification per week for a show nobody on this account is following along with.
     */
    @Query(
            value =
                    """
                    SELECT e.user_id AS userId,
                           i.id AS itemId,
                           (i.metadata -> 'nextEpisode' ->> 'episode')::int AS episode
                      FROM trackable_item i
                      JOIN user_entry e ON e.trackable_item_id = i.id
                     WHERE i.metadata -> 'nextEpisode' ->> 'airingAt' IS NOT NULL
                       AND (i.metadata -> 'nextEpisode' ->> 'airingAt')::bigint <= :now
                       AND (e.status = 'IN_PROGRESS'
                            OR (i.metadata -> 'nextEpisode' ->> 'episode')::int = 1)
                    """,
            nativeQuery = true)
    List<AiredEpisode> airedSince(@Param("now") long now);

    /**
     * When the next episode anyone is waiting for lands, as Unix seconds.
     *
     * <p>What makes the sweep exact rather than periodic: the moment is already on the item,
     * so there is nothing to poll for and nothing to ask a source about. Null when no tracked
     * title has one ahead of it, which is most of a night.
     */
    @Query(
            value =
                    """
                    SELECT MIN((i.metadata -> 'nextEpisode' ->> 'airingAt')::bigint)
                      FROM trackable_item i
                      JOIN user_entry e ON e.trackable_item_id = i.id
                     WHERE i.metadata -> 'nextEpisode' ->> 'airingAt' IS NOT NULL
                       AND (i.metadata -> 'nextEpisode' ->> 'airingAt')::bigint > :now
                    """,
            nativeQuery = true)
    Long nextAiringAfter(@Param("now") long now);

    /** One reader, one title, and the episode of it that has landed. */
    interface AiredEpisode {
        Long getUserId();

        Long getItemId();

        Integer getEpisode();
    }
}
