package dev.nexus.core.preferences;

import dev.nexus.core.domain.MediaType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One media type a reader has switched off.
 *
 * <p>A row is the exception rather than the rule: nothing written means everything is on, so
 * a reader who never opens settings costs no rows, and a module added later arrives enabled.
 */
@Entity
@Table(name = "user_disabled_module")
public class DisabledModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaType mediaType;

    protected DisabledModule() {
        // JPA
    }

    public DisabledModule(Long userId, MediaType mediaType) {
        this.userId = userId;
        this.mediaType = mediaType;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public MediaType getMediaType() {
        return mediaType;
    }
}
