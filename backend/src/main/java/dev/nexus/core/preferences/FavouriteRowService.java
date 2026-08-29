package dev.nexus.core.preferences;

import dev.nexus.core.domain.MediaType;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The order a reader put their favourite rows in, read and written only for that reader. */
@Service
public class FavouriteRowService {

    private final FavouriteRowRepository repository;

    public FavouriteRowService(FavouriteRowRepository repository) {
        this.repository = repository;
    }

    /** The rows a reader placed, first to last, and which of them sit beside the one before. */
    public record Arrangement(List<MediaType> order, Set<MediaType> paired) {

        static Arrangement of(List<FavouriteRow> rows) {
            Set<MediaType> paired = EnumSet.noneOf(MediaType.class);
            rows.stream().filter(FavouriteRow::sharesLane).forEach(row -> paired.add(row.getMediaType()));

            return new Arrangement(rows.stream().map(FavouriteRow::getMediaType).toList(), paired);
        }
    }

    @Transactional(readOnly = true)
    public Arrangement arrangementFor(long userId) {
        return Arrangement.of(repository.findByUserIdOrderBySortOrderAsc(userId));
    }

    /**
     * Replaces the whole order rather than moving one row within it, because the client sends
     * the arrangement that resulted from the drop — reconstructing that from a single move
     * would be the same answer by a longer route, and replaying the write leaves the same
     * order rather than a different one.
     *
     * <p>A type named twice keeps its first place: the order is a sequence of distinct rows,
     * and there is no reading of a repeat that the reader could have meant.
     */
    @Transactional
    public Arrangement replaceFor(
            long userId, Collection<MediaType> order, Collection<MediaType> paired) {
        List<MediaType> arranged = order == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(order));

        Set<MediaType> sharing = shareable(arranged, paired);

        repository.deleteByUserId(userId);
        // Flushed before the new rows are written: Hibernate orders inserts ahead of deletes
        // within a transaction, so without this a row keeping its media type collides with
        // the copy that is on its way out on (user_id, media_type).
        repository.flush();

        if (!arranged.isEmpty()) {
            repository.saveAll(java.util.stream.IntStream.range(0, arranged.size())
                    .mapToObj(rank -> new FavouriteRow(
                            userId, arranged.get(rank), rank, sharing.contains(arranged.get(rank))))
                    .toList());
        }
        return new Arrangement(arranged, sharing);
    }

    /**
     * Which of the asked-for pairings can actually stand.
     *
     * <p>A band holds two rows and no more, so a row may only share with one that stands
     * alone: asking for three in a row to share leaves the first two paired and the third
     * on its own beneath them. The first row is never paired either, having nothing before
     * it to sit beside. Both are cases a client can send by dragging quickly, and neither
     * is worth an error when the honest reading is plain.
     */
    private Set<MediaType> shareable(List<MediaType> order, Collection<MediaType> paired) {
        Set<MediaType> asked = paired == null ? Set.of() : Set.copyOf(paired);
        Set<MediaType> sharing = EnumSet.noneOf(MediaType.class);

        for (int at = 1; at < order.size(); at++) {
            MediaType row = order.get(at);
            if (asked.contains(row) && !sharing.contains(order.get(at - 1))) {
                sharing.add(row);
            }
        }
        return sharing;
    }
}
