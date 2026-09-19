package dev.nexus.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * Filled by the column's own default, so every account is stamped by one clock. Read back
     * on insert because registering answers with this date too, and the entity behind that one
     * response has never been re-read from the row it just wrote.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    /** Null for an account created before consent was recorded; see V21. */
    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(name = "terms_version", length = 32)
    private String termsVersion;

    /** Null for an account made before V23 asked, which reads as an unknown age. */
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /**
     * When the address on this account was shown to reach its owner. Null until a verification
     * link is followed, and null is the honest default: an account that has never proved its
     * address is not the same as one that proved it at an unknown time.
     */
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    protected AppUser() {
        // JPA
    }

    public AppUser(String email, String username, String passwordHash) {
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    /** Records which text was accepted, and when. Set once, at registration. */
    public void acceptTerms(String version, Instant at) {
        this.termsVersion = version;
        this.termsAcceptedAt = at;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    /**
     * Set once and never changed from a request. An editable birth date is no age check: the
     * account that wants past the 18+ setting would simply edit it.
     */
    public void declareDateOfBirth(LocalDate dateOfBirth) {
        if (this.dateOfBirth == null) {
            this.dateOfBirth = dateOfBirth;
        }
    }

    /** Unknown counts as no: an account that never gave a date cannot be shown to be old enough. */
    public boolean hasTurned(int years, LocalDate on) {
        return AgePolicy.hasTurned(dateOfBirth, years, on);
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    /**
     * Idempotent on purpose: two clicks on the same link, or a link followed while already
     * verified, must not move the date. When it was first proved is the fact worth keeping.
     */
    public void verifyEmail(Instant at) {
        if (this.emailVerifiedAt == null) {
            this.emailVerifiedAt = at;
        }
    }

    /**
     * A changed address is an unproved address. Called by whatever changes the email, so the
     * two can never drift apart into an account verified against something nobody typed.
     */
    public void unverifyEmail() {
        this.emailVerifiedAt = null;
    }

    public void rename(String username) {
        this.username = username;
    }

    /**
     * Changing the address unproves it in the same call, so the two cannot drift apart. Doing
     * this at the call sites instead would mean every future one has to remember, and the one
     * that forgets leaves an account verified against an address nobody ever confirmed.
     */
    public void changeEmail(String email) {
        this.email = email;
        unverifyEmail();
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
