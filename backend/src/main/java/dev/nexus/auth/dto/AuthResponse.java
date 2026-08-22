package dev.nexus.auth.dto;

public record AuthResponse(String accessToken, UserResponse user) {}
