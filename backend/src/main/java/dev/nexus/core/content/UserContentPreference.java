package dev.nexus.core.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One reader's adult-content switches. Written only once a reader changes one; see V25. */
@Entity
@Table(name = "user_content_preference")
public class UserContentPreference {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "show_adult", nullable = false)
    private boolean showAdult;

    @Column(name = "blur_adult", nullable = false)
    private boolean blurAdult = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserContentPreference() {
        // JPA
    }

    public UserContentPreference(Long userId) {
        this.userId = userId;
    }

    public boolean isShowAdult() {
        return showAdult;
    }

    public boolean isBlurAdult() {
        return blurAdult;
    }

    public void setShowAdult(boolean showAdult) {
        this.showAdult = showAdult;
        this.updatedAt = Instant.now();
    }

    public void setBlurAdult(boolean blurAdult) {
        this.blurAdult = blurAdult;
        this.updatedAt = Instant.now();
    }
}
