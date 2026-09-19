package dev.nexus.auth.dto;

import dev.nexus.auth.AppUser;
import java.time.Instant;

/** {@code createdAt} is the account's own start date — what a profile means by "member since". */
public record UserResponse(Long id, String email, String username, Instant createdAt) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getUsername(), user.getCreatedAt());
    }
}
