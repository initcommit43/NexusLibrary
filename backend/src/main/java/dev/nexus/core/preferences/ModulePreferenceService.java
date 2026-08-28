package dev.nexus.core.preferences;

import dev.nexus.core.domain.MediaType;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Which modules a reader has switched off, read and written only for that reader. */
@Service
public class ModulePreferenceService {

    private final DisabledModuleRepository repository;

    public ModulePreferenceService(DisabledModuleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Set<MediaType> disabledFor(long userId) {
        return repository.findByUserId(userId).stream()
                .map(DisabledModule::getMediaType)
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(MediaType.class)));
    }

    /**
     * Replaces the whole set rather than adding to it, because the client sends the state of
     * every checkbox at once — a diff would have to be reconstructed here from what is
     * missing, which is the same answer by a longer route.
     */
    @Transactional
    public Set<MediaType> replaceFor(long userId, Collection<MediaType> disabled) {
        Set<MediaType> wanted = disabled == null || disabled.isEmpty()
                ? EnumSet.noneOf(MediaType.class)
                : EnumSet.copyOf(disabled);

        repository.deleteByUserId(userId);
        if (!wanted.isEmpty()) {
            repository.saveAll(
                    wanted.stream().map(type -> new DisabledModule(userId, type)).toList());
        }
        return wanted;
    }

    /** Convenience for callers that want the answer in a stable order. */
    public List<MediaType> asList(Set<MediaType> types) {
        return types.stream().sorted().toList();
    }
}
