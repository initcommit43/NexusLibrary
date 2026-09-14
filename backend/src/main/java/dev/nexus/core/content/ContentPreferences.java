package dev.nexus.core.content;

import dev.nexus.auth.AgePolicy;
import dev.nexus.auth.AppUser;
import dev.nexus.auth.AppUserRepository;
import dev.nexus.core.account.AccountNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may see adult titles, and how their covers are drawn.
 *
 * <p>Every search, shelf, feed and detail asks here rather than reading the row, so the rule is
 * written once.
 */
@Service
public class ContentPreferences {

    /**
     * What a reader may see, resolved.
     *
     * @param showAdult whether adult titles appear at all, age already taken into account
     * @param blurAdult whether their covers are blurred; kept as stored while showAdult is off,
     *     so switching titles back on restores the reader's own choice
     * @param adultAllowed whether this account may turn showAdult on, so a switch it cannot use
     *     can say why
     * @param dateOfBirthSet whether the account has a date of birth at all; one made before it
     *     was asked can still give it once
     */
    public record Visibility(boolean showAdult, boolean blurAdult, boolean adultAllowed, boolean dateOfBirthSet) {}

    private final UserContentPreferenceRepository preferences;
    private final AppUserRepository users;

    public ContentPreferences(UserContentPreferenceRepository preferences, AppUserRepository users) {
        this.preferences = preferences;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Visibility forUser(long userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> new AccountNotFoundException(userId));
        boolean allowed = user.hasTurned(AgePolicy.ADULT, AgePolicy.today());
        boolean dateOfBirthSet = user.getDateOfBirth() != null;

        return preferences
                .findByUserId(userId)
                // Age checked on read as well as on write: a stored "on" must never be what decides.
                .map(row -> new Visibility(row.isShowAdult() && allowed, row.isBlurAdult(), allowed, dateOfBirthSet))
                .orElseGet(() -> new Visibility(false, true, allowed, dateOfBirthSet));
    }

    /** Applies whichever switch the request carried; null means leave it as it is. */
    @Transactional
    public Visibility update(long userId, Boolean showAdult, Boolean blurAdult) {
        if (Boolean.TRUE.equals(showAdult) && !forUser(userId).adultAllowed()) {
            throw new AdultContentNotPermittedException();
        }

        UserContentPreference row =
                preferences.findByUserId(userId).orElseGet(() -> new UserContentPreference(userId));
        if (showAdult != null) {
            row.setShowAdult(showAdult);
        }
        if (blurAdult != null) {
            row.setBlurAdult(blurAdult);
        }
        preferences.save(row);

        return forUser(userId);
    }
}
