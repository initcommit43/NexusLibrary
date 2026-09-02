package dev.nexus.auth;

import dev.nexus.config.NexusProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String CLAIM_TOKEN_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    /** A refresh token, and what the store needs to be able to withdraw it later. */
    public record IssuedRefreshToken(String token, UUID jti, Instant expiresAt) {}

    /** Who a refresh token is for, and which token it is. */
    public record RefreshTokenClaims(Long userId, UUID jti) {}

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(NexusProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(properties.jwt().accessTtlMinutes());
        this.refreshTtl = Duration.ofDays(properties.jwt().refreshTtlDays());
    }

    public String issueAccessToken(AppUser user) {
        return issue(user.getId(), TYPE_ACCESS, accessTtl);
    }

    /**
     * A refresh token carries an id of its own so it can be withdrawn. The signature alone
     * says a token was issued here, never that it is still meant to work; the id is what
     * {@code refresh_token} lists, and a token whose id is absent or retired buys nothing.
     */
    public IssuedRefreshToken issueRefreshToken(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(refreshTtl);
        UUID jti = UUID.randomUUID();

        String token = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .id(jti.toString())
                .claim(CLAIM_TOKEN_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();

        return new IssuedRefreshToken(token, jti, expiresAt);
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    public Optional<Long> readAccessToken(String token) {
        return parse(token, TYPE_ACCESS).map(claims -> Long.valueOf(claims.getSubject()));
    }

    /**
     * Empty for a token that was never signed here, has expired, is an access token, or
     * predates tokens carrying an id — the last of which signs out whoever still holds one.
     */
    public Optional<RefreshTokenClaims> readRefreshToken(String token) {
        return parse(token, TYPE_REFRESH).flatMap(claims -> {
            try {
                return Optional.of(
                        new RefreshTokenClaims(Long.valueOf(claims.getSubject()), UUID.fromString(claims.getId())));
            } catch (IllegalArgumentException | NullPointerException e) {
                return Optional.empty();
            }
        });
    }

    private String issue(Long userId, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TOKEN_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * Rejects a token signed for a different purpose, so a long-lived refresh token can
     * never be replayed as an access token.
     */
    private Optional<Claims> parse(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!expectedType.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
