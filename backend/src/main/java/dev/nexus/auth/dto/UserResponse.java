package dev.nexus.auth.dto;

import dev.nexus.auth.AppUser;

public record UserResponse(Long id, String email, String username) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getUsername());
    }
}
