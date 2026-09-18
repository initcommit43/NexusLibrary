package dev.nexus.auth.dto;

import jakarta.validation.constraints.Size;

/**
 * How a native client presents its refresh token, since it has no cookie to present it in.
 * The body is absent altogether for a browser, whose cookie the server reads instead.
 *
 * <p>No {@code @NotBlank}: the token is optional here (a browser's request carries none), and
 * its absence is handled by whichever caller reads it. The token itself is a signed JWT, well
 * under this bound in practice; the cap exists only to refuse an unbounded string before it is
 * ever handed to the parser.
 */
public record RefreshRequest(@Size(max = 2048) String refreshToken) {}
