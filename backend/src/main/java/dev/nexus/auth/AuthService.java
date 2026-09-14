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
    private final EmailPolicy emailPolicy;
    private final boolean verificationRequired;
    private final String decoyHash;

    public AuthService(
            AppUserRepository users,
            PasswordEncoder passwordEncoder,
            EmailPolicy emailPolicy,
            @org.springframework.beans.factory.annotation.Value("${nexus.verification.required:false}")
                    boolean verificationRequired) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.emailPolicy = emailPolicy;
        this.verificationRequired = verificationRequired;
        this.decoyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AppUser register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        // First of all, so a request about to be refused for age never learns whether its
        // address is already registered.
        if (!AgePolicy.isPlausible(request.dateOfBirth())) {
            throw DateOfBirthRejectedException.implausible();
        }
        if (!AgePolicy.hasTurned(request.dateOfBirth(), AgePolicy.MINIMUM, AgePolicy.today())) {
            throw DateOfBirthRejectedException.tooYoung();
        }

        // Before the conflict check, so an address that could never work is told so rather
        // than being told it is taken — which would also answer a question about who is here.
        emailPolicy.check(email);

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
        user.declareDateOfBirth(request.dateOfBirth());

        return users.save(user);
    }

    @Transactional(readOnly = true)
    public AppUser authenticate(LoginRequest request) {
        Optional<AppUser> match = users.findByEmail(normalizeEmail(request.email()));

        // Always spend a full bcrypt comparison, even with no account matched: skipping it
        // would make unregistered emails answer measurably faster and leak who has an account.
        String hash = match.map(AppUser::getPasswordHash).orElse(decoyHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        AppUser user = match.filter(candidate -> passwordMatches)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid email or password."));

        // After the password, never before. Answering "confirm your email" to a wrong password
        // would tell whoever guessed it that the address has an account here.
        if (verificationRequired && !user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }

        return user;
    }

    @Transactional(readOnly = true)
    public AppUser requireById(Long id) {
        return users.findById(id).orElseThrow(() -> new AuthenticationFailedException("Account no longer exists."));
    }

    private String normalizeEmail(String email) {
        return EmailAddresses.normalise(email);
    }
}
