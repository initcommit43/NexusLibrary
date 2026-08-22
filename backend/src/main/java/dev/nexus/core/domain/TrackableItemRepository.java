package dev.nexus.core.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackableItemRepository extends JpaRepository<TrackableItem, Long> {

    Optional<TrackableItem> findBySourceAndExternalId(Source source, String externalId);

    List<TrackableItem> findBySourceAndExternalIdIn(Source source, Collection<String> externalIds);
}
