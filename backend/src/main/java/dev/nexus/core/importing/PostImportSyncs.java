package dev.nexus.core.importing;

import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.jobs.SyncJob;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Finds the follow-up work a provider's module wants done once its library has landed. */
@Component
public class PostImportSyncs {

    private final List<PostImportSync> syncs;

    public PostImportSyncs(List<PostImportSync> syncs) {
        this.syncs = List.copyOf(syncs);
    }

    public Optional<SyncJob> startAfter(ExternalAccount account) {
        return syncs.stream()
                .filter(sync -> sync.provider() == account.getProvider())
                .findFirst()
                .flatMap(sync -> sync.startAfter(account));
    }
}
