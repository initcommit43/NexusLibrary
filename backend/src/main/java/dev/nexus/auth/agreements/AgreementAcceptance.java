package dev.nexus.auth.agreements;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One acceptance, of one document, at one version, on one platform.
 *
 * <p>Write-once. Nothing here has a setter and nothing updates a row: an acceptance that
 * could be edited afterwards would not be worth much as the proof Art. 7(1) GDPR asks for.
 * A new version accepted is a new row, and the old one stays where it is.
 */
@Entity
@Table(name = "agreement_acceptance")
public class AgreementAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The owner. Every read of this table is filtered on it; see {@link AgreementService}. */
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private Agreement document;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private AgreementPlatform platform;

    @Column(nullable = false, updatable = false, length = 32)
    private String version;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    protected AgreementAcceptance() {
        // JPA
    }

    public AgreementAcceptance(
            Long userId, Agreement document, AgreementPlatform platform, String version, Instant acceptedAt) {
        this.userId = userId;
        this.document = document;
        this.platform = platform;
        this.version = version;
        this.acceptedAt = acceptedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Agreement getDocument() {
        return document;
    }

    public AgreementPlatform getPlatform() {
        return platform;
    }

    public String getVersion() {
        return version;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }
}
