package dev.nexus.core.account;

import dev.nexus.auth.AgePolicy;
import dev.nexus.auth.AppUser;
import dev.nexus.auth.AppUserRepository;
import dev.nexus.auth.DateOfBirthRejectedException;
import dev.nexus.auth.RegistrationConflictException;
import dev.nexus.core.account.AccountRequests.PasswordChange;
import dev.nexus.core.account.AccountRequests.ProfileUpdate;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reader's own account: what it is called, how it is signed into, and the two things
 * data-protection law entitles them to — a copy of everything, and its removal.
 */
@Service
public class AccountService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AccountExport exportOf;

    public AccountService(
            AppUserRepository users,
            PasswordEncoder passwordEncoder,
            AccountExport exportOf) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.exportOf = exportOf;
    }

    private AppUser require(long userId) {
        return users.findById(userId).orElseThrow(() -> new AccountNotFoundException(userId));
    }

    @Transactional
    public AppUser updateProfile(long userId, ProfileUpdate update) {
        AppUser user = require(userId);

        // Normalised the same way registration does, or the two could disagree about whether
        // an address is already taken.
        String email = normalised(update.email());
        if (email != null) {
            email = email.toLowerCase(java.util.Locale.ROOT);
            if (!email.equals(user.getEmail())) {
                if (users.existsByEmailIgnoreCase(email)) {
                    throw new RegistrationConflictException("email", "That email is already registered.");
                }
                user.changeEmail(email);
            }
        }

        String username = normalised(update.username());
        if (username != null && !username.equals(user.getUsername())) {
            if (users.existsByUsernameIgnoreCase(username)) {
                throw new RegistrationConflictException("username", "That username is taken.");
            }
            user.rename(username);
        }

        return users.save(user);
    }

    /**
     * Gives an account from before V23 its date of birth, once.
     *
     * <p>No age floor here, unlike registration: the account predates the rule, and turning an
     * existing library away over it is not this setting's job. An age under 18 simply keeps
     * adult titles out of reach.
     */
    @Transactional
    public void declareDateOfBirth(long userId, LocalDate dateOfBirth) {
        require(userId);
        if (!AgePolicy.isPlausible(dateOfBirth)) {
            throw DateOfBirthRejectedException.implausible();
        }
        if (users.declareDateOfBirthIfUnset(userId, dateOfBirth) == 0) {
            throw new DateOfBirthAlreadySetException();
        }
    }

    /** Answers with the account so the caller can put it back into a session of its own. */
    @Transactional
    public AppUser changePassword(long userId, PasswordChange change) {
        AppUser user = require(userId);

        if (!passwordEncoder.matches(change.currentPassword(), user.getPasswordHash())) {
            throw new PasswordMismatchException();
        }

        user.changePasswordHash(passwordEncoder.encode(change.newPassword()));
        return users.save(user);
    }

    /**
     * Everything held about this reader, as one document. Assembled by {@link AccountExport},
     * which is where the list of what that means — and of what is deliberately left out — is
     * kept. This method exists so the caller still asks the account for its own data.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> export(long userId) {
        return exportOf.of(require(userId));
    }

    /**
     * Removes the account and everything hanging off it.
     *
     * <p>One delete does it: every table that references a reader does so with a cascade, so
     * there is no order to get wrong and nothing left behind for a later audit to find.
     */
    @Transactional
    public void delete(long userId, String password) {
        AppUser user = require(userId);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new PasswordMismatchException();
        }

        users.delete(user);
    }

    private static String normalised(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
