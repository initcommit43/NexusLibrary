package dev.nexus.core.importing;

import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.jobs.SyncJob;
import java.util.Optional;

/**
 * Work a module wants done once a library has landed — Steam's achievements today.
 *
 * <p>It runs after the import rather than alongside it on purpose: both talk to the same
 * provider under the same request budget, and racing them would spend that budget twice as
 * fast for no gain. Sequencing also means the achievement pass already knows every game it
 * has to walk.
 *
 * <p>Core knows only that a provider may have follow-up work, never what it is.
 */
public interface PostImportSync {

    Provider provider();

    /** @return a job to watch, or empty when this run had nothing to follow up */
    Optional<SyncJob> startAfter(ExternalAccount account);
}
