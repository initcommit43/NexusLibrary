package dev.nexus.auth;

import dev.nexus.auth.dto.LoginRequest;
import dev.nexus.auth.dto.RegisterRequest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String decoyHash;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.decoyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AppUser register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (users.existsByEmailIgnoreCase(email)) {
            throw new RegistrationConflictException("email", "That email is already registered.");
        }
        if (users.existsByUsernameIgnoreCase(request.username())) {
            throw new RegistrationConflictException("username", "That username is taken.");
        }

        AppUser user = new AppUser(email, request.username(), passwordEncoder.encode(request.password()));
        // Recorded in the same transaction as the account. An account that exists without a
        // consent record beside it is one we could not answer an Art. 7(1) question about.
        user.acceptTerms(PolicyVersion.CURRENT, Instant.now());

        return users.save(user);
    }

    @Transactional(readOnly = true)
    public AppUser authenticate(LoginRequest request) {
        Optional<AppUser> match = users.findByEmail(normalizeEmail(request.email()));

        // Always spend a full bcrypt comparison, even with no account matched: skipping it
        // would make unregistered emails answer measurably faster and leak who has an account.
        String hash = match.map(AppUser::getPasswordHash).orElse(decoyHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        return match.filter(user -> passwordMatches)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid email or password."));
    }

    @Transactional(readOnly = true)
    public AppUser requireById(Long id) {
        return users.findById(id).orElseThrow(() -> new AuthenticationFailedException("Account no longer exists."));
    }

    private String normalizeEmail(String email) {
        return EmailAddresses.normalise(email);
    }
}
