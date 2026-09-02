package dev.nexus.auth.dto;

/**
 * How a native client presents its refresh token, since it has no cookie to present it in.
 * The body is absent altogether for a browser, whose cookie the server reads instead.
 */
public record RefreshRequest(String refreshToken) {}
