package dev.nexus.core.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Scoped by {@code userId} throughout, for the same reason as {@link UserEntryRepository}. */
public interface ExternalAccountRepository extends JpaRepository<ExternalAccount, Long> {

    List<ExternalAccount> findByUserId(Long userId);

    Optional<ExternalAccount> findByUserIdAndProvider(Long userId, Provider provider);

    long deleteByUserIdAndProvider(Long userId, Provider provider);
}
