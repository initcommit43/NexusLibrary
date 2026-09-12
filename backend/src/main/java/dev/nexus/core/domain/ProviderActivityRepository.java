package dev.nexus.core.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProviderActivityRepository extends JpaRepository<ProviderActivity, Long> {

    /**
     * Which of the events a sync is about to write are already stored.
     *
     * <p>Asked as a batch rather than a row at a time: a reader with five years of AniList
     * behind them brings thousands of events, and an exists-check apiece is thousands of
     * round trips to learn that the second import changes nothing.
     */
    List<ProviderActivity> findByUserIdAndProviderAndExternalIdIn(
            Long userId, Provider provider, Collection<String> externalIds);

    long countByUserIdAndProvider(Long userId, Provider provider);

    /** Newest first, for the half of the feed that came in with a library. */
    List<ProviderActivity> findByUserIdOrderByHappenedOnDescIdDesc(Long userId, Limit limit);

    /** Scoped to the owner in the query itself, so an id alone can never reach another's row. */
    long deleteByIdAndUserId(Long id, Long userId);

    /**
     * The last day anything happened to each of a reader's titles, as their provider recorded
     * it.
     *
     * <p>One query for the whole library rather than one per entry: the shelves are ordered by
     * this, and a query a title would be a query a title on every load of the home page.
     */
    @Query("SELECT p.itemId AS itemId, max(p.happenedOn) AS day FROM ProviderActivity p "
            + "WHERE p.userId = :userId GROUP BY p.itemId")
    List<LatestActivity> latestPerItem(@Param("userId") Long userId);

    /** One title, and the last day its provider recorded anything about it. */
    interface LatestActivity {
        Long getItemId();

        LocalDate getDay();
    }

    /**
     * A reader's imported history for the named mediums.
     *
     * <p>This is the third source of the activity map beside an entry's start and finish dates,
     * and it outlives the entries: it is keyed to the catalogue item, not to the entry, so
     * emptying a shelf left every imported day standing on the map. Scoped by owner here.
     */
    @Modifying
    @Query(
            value = "DELETE FROM provider_activity a USING trackable_item i"
                    + " WHERE a.trackable_item_id = i.id AND a.user_id = :userId AND i.media_type IN (:mediaTypes)",
            nativeQuery = true)
    int deleteForMediaTypes(@Param("userId") Long userId, @Param("mediaTypes") Collection<String> mediaTypes);
}
