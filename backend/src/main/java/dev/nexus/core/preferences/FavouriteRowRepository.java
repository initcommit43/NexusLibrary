package dev.nexus.core.preferences;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavouriteRowRepository extends JpaRepository<FavouriteRow, Long> {

    List<FavouriteRow> findByUserIdOrderBySortOrderAsc(Long userId);

    void deleteByUserId(Long userId);
}
