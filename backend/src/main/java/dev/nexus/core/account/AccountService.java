package dev.nexus.core.account;

import dev.nexus.auth.AppUser;
import dev.nexus.auth.AppUserRepository;
import dev.nexus.auth.RegistrationConflictException;
import dev.nexus.core.account.AccountRequests.PasswordChange;
import dev.nexus.core.account.AccountRequests.ProfileUpdate;
import dev.nexus.core.domain.ActivityRepository;
import dev.nexus.core.domain.ExternalAccountRepository;
import dev.nexus.core.domain.UserEntryRepository;
import dev.nexus.core.tracking.dto.TrackedItemResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Limit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reader's own account: what it is called, how it is signed into, and the two things
 * data-protection law entitles them to — a copy of everything, and its removal.
 */
@Service
public class AccountService {

    /**
     * An export is a file someone downloads once, so it may be large, but it is still one
     * request holding the whole answer in memory. This is where a library stops being one.
     */
    private static final int MAX_EXPORTED_ACTIVITY = 5_000;

    private final AppUserRepository users;
    private final UserEntryRepository entries;
    private final ExternalAccountRepository accounts;
    private final ActivityRepository activity;
    private final PasswordEncoder passwordEncoder;

    public AccountService(
            AppUserRepository users,
            UserEntryRepository entries,
            ExternalAccountRepository accounts,
            ActivityRepository activity,
            PasswordEncoder passwordEncoder) {
        this.users = users;
        this.entries = entries;
        this.accounts = accounts;
        this.activity = activity;
        this.passwordEncoder = passwordEncoder;
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
     * Everything held about this reader, as one document.
     *
     * <p>Connected accounts are named but their tokens are not: a copy of someone's data is
     * not a copy of their keys, and an export is a file that ends up in a downloads folder.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> export(long userId) {
        AppUser user = require(userId);

        List<Map<String, Object>> connections = accounts.findByUserId(userId).stream()
                .map(account -> Map.<String, Object>of(
                        "provider", account.getProvider().name(),
                        "externalUserId", account.getExternalUserId(),
                        "connectedAt", String.valueOf(account.getConnectedAt())))
                .toList();

        List<Map<String, Object>> history = activity
                .findByUserIdOrderByCreatedAtDesc(userId, Limit.of(MAX_EXPORTED_ACTIVITY))
                .stream()
                .map(row -> Map.<String, Object>of(
                        "type", String.valueOf(row.getType()),
                        "createdAt", String.valueOf(row.getCreatedAt())))
                .toList();

        return Map.of(
                "exportedAt", Instant.now().toString(),
                "account",
                        Map.of(
                                "email", user.getEmail(),
                                "username", user.getUsername(),
                                "registeredAt", String.valueOf(user.getCreatedAt())),
                "entries",
                        entries.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                                .map(TrackedItemResponse::from)
                                .toList(),
                "connectedAccounts", connections,
                "activity", history);
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
