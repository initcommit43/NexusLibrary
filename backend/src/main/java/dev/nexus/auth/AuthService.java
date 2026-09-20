package dev.nexus.auth;

import dev.nexus.auth.agreements.AgreementPlatform;
import dev.nexus.auth.agreements.AgreementService;
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
    private final AgreementService agreements;
    private final boolean verificationRequired;
    private final String decoyHash;

    public AuthService(
            AppUserRepository users,
            PasswordEncoder passwordEncoder,
            EmailPolicy emailPolicy,
            AgreementService agreements,
            @org.springframework.beans.factory.annotation.Value("${nexus.verification.required:false}")
                    boolean verificationRequired) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.emailPolicy = emailPolicy;
        this.agreements = agreements;
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
        Instant now = Instant.now();
        // Recorded in the same transaction as the account. An account that exists without a
        // consent record beside it is one we could not answer an Art. 7(1) question about.
        //
        // Still written, though agreement_acceptance is now the fuller record: these two
        // columns are what every account registered before V27 has, and a reader of them
        // should not have to know which era an account comes from.
        user.acceptTerms(PolicyVersion.CURRENT, now);
        user.declareDateOfBirth(request.dateOfBirth());

        AppUser saved = users.save(user);
        recordAgreements(saved, request, now);
        return saved;
    }

    /**
     * What the new account agreed to, per document and per platform.
     *
     * <p>Two shapes arrive here. A client that sends {@code acceptedAgreements} is telling us
     * exactly which texts it rendered, and that is recorded as sent — a version that is not
     * current is refused rather than corrected. A client that predates the field sends only
     * the old boolean, and that box named the terms and the privacy policy, so those two are
     * what it records. Nothing else is inferred from it: an EULA and a cookie notice were
     * never on that form, and they fall outstanding for the reader's next sign-in.
     */
    private void recordAgreements(AppUser user, RegisterRequest request, Instant at) {
        AgreementPlatform platform = AgreementPlatform.of(request.client());

        if (request.acceptedAgreements().isEmpty()) {
            agreements.acceptTermsAndPrivacy(user.getId(), platform, at);
            return;
        }
        agreements.accept(user.getId(), platform, request.acceptedAgreements());
    }

    @Transactional(readOnly = true)
    public AppUser authenticate(LoginRequest request) {
        // A username cannot contain an @, so one in the identifier means an email address.
        String identifier = request.identifier();
        Optional<AppUser> match = identifier.contains("@")
                ? users.findByEmailIgnoreCase(normalizeEmail(identifier))
                : users.findByUsernameIgnoreCase(identifier);

        // Always spend a full bcrypt comparison, even with no account matched: skipping it
        // would make an unregistered name answer measurably faster and leak who has an account.
        String hash = match.map(AppUser::getPasswordHash).orElse(decoyHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        AppUser user = match.filter(candidate -> passwordMatches)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid username, email or password."));

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
