package dev.nexus.core.activity;

import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.UserEntryRepository;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A reader's year, day by day.
 *
 * <p>Built from when things were started and finished rather than from the activity log:
 * the log holds what was done inside this app, and an imported library brings dates going
 * back years, which is the history worth drawing.
 *
 * <p>Games are left out, and not as an oversight. Steam knows how long a game has been
 * played and never when: an imported library of two hundred games carries no date at all,
 * so a map including them would show two hundred titles as a blank year.
 */
@Service
public class HistoryService {

    static final Set<MediaType> WITHOUT_DATES = Set.of(MediaType.GAME);

    private final UserEntryRepository entries;

    public HistoryService(UserEntryRepository entries) {
        this.entries = entries;
    }

    /**
     * One entry per day that saw anything, oldest first; empty days are simply absent.
     *
     * @param wanted the media types to count, or empty for everything that keeps dates
     */
    @Transactional(readOnly = true)
    public List<UserEntryRepository.DayTally> since(
            long userId, LocalDate from, Collection<MediaType> wanted) {

        Set<MediaType> counted = EnumSet.copyOf(
                wanted == null || wanted.isEmpty() ? Arrays.asList(MediaType.values()) : wanted);
        counted.removeAll(WITHOUT_DATES);

        // Asked for as what to leave out, because that is the shorter list and the one the
        // query can hold: a media type added later is counted without anyone remembering to.
        Set<MediaType> excluded = EnumSet.allOf(MediaType.class);
        excluded.removeAll(counted);

        return entries.tallyDaysSince(userId, from, excluded.stream().map(Enum::name).toList());
    }
}
