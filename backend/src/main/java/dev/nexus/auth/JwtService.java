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
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String CLAIM_TOKEN_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

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

    public String issueRefreshToken(AppUser user) {
        return issue(user.getId(), TYPE_REFRESH, refreshTtl);
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    public Optional<Long> readAccessToken(String token) {
        return readSubject(token, TYPE_ACCESS);
    }

    public Optional<Long> readRefreshToken(String token) {
        return readSubject(token, TYPE_REFRESH);
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
    private Optional<Long> readSubject(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!expectedType.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
