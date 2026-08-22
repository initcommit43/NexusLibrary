package dev.nexus.auth;

/**
 * The authenticated principal. Every user-scoped query must derive its user id from
 * here rather than from a request parameter.
 */
public record CurrentUser(Long id) {}
