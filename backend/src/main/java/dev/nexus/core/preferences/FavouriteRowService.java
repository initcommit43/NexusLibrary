package dev.nexus.core.preferences;

import dev.nexus.core.domain.MediaType;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The order a reader put their favourite rows in, read and written only for that reader. */
@Service
public class FavouriteRowService {

    private final FavouriteRowRepository repository;

    public FavouriteRowService(FavouriteRowRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<MediaType> orderFor(long userId) {
        return repository.findByUserIdOrderBySortOrderAsc(userId).stream()
                .map(FavouriteRow::getMediaType)
                .toList();
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
    public List<MediaType> replaceFor(long userId, Collection<MediaType> order) {
        List<MediaType> arranged = order == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(order));

        repository.deleteByUserId(userId);
        // Flushed before the new rows are written: Hibernate orders inserts ahead of deletes
        // within a transaction, so without this a row keeping its media type collides with
        // the copy that is on its way out on (user_id, media_type).
        repository.flush();

        if (!arranged.isEmpty()) {
            repository.saveAll(java.util.stream.IntStream.range(0, arranged.size())
                    .mapToObj(rank -> new FavouriteRow(userId, arranged.get(rank), rank))
                    .toList());
        }
        return arranged;
    }
}
