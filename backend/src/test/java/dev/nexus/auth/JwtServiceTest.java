package dev.nexus.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.config.NexusProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-signing-key-long-enough-for-hs256-0123456789";

    private JwtService jwtService;
    private AppUser user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new NexusProperties(
                new NexusProperties.Jwt(SECRET, 15, 30),
                new NexusProperties.Security(false, List.of(), "http://localhost:5173", true, 0),
                new NexusProperties.RateLimit(10, 30, 3)));

        user = new AppUser("player@example.com", "player", "hash");
        setId(user, 42L);
    }

    @Test
    void accessTokenRoundTripsTheUserId() {
        String token = jwtService.issueAccessToken(user);

        assertThat(jwtService.readAccessToken(token)).contains(42L);
    }

    @Test
    void refreshTokenIsRejectedWhereAnAccessTokenIsExpected() {
        String refreshToken = jwtService.issueRefreshToken(user).token();

        assertThat(jwtService.readRefreshToken(refreshToken).map(JwtService.RefreshTokenClaims::userId))
                .contains(42L);
        assertThat(jwtService.readAccessToken(refreshToken)).isEmpty();
    }

    /** The id is what the store lists, so a token whose id cannot be read back is unusable. */
    @Test
    void refreshTokenCarriesTheIdItWasIssuedWith() {
        JwtService.IssuedRefreshToken issued = jwtService.issueRefreshToken(user);

        assertThat(jwtService.readRefreshToken(issued.token()).map(JwtService.RefreshTokenClaims::jti))
                .contains(issued.jti());
    }

    /** Two sessions must be separately withdrawable, which they are not if they share an id. */
    @Test
    void everyRefreshTokenGetsItsOwnId() {
        assertThat(jwtService.issueRefreshToken(user).jti())
                .isNotEqualTo(jwtService.issueRefreshToken(user).jti());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService attacker = new JwtService(new NexusProperties(
                new NexusProperties.Jwt("a-completely-different-signing-key-0123456789", 15, 30),
                new NexusProperties.Security(false, List.of(), "http://localhost:5173", true, 0),
                new NexusProperties.RateLimit(10, 30, 3)));

        String forged = attacker.issueAccessToken(user);

        assertThat(jwtService.readAccessToken(forged)).isEmpty();
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.issueAccessToken(user);
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThat(jwtService.readAccessToken(tampered)).isEmpty();
    }

    @Test
    void garbageIsRejectedWithoutThrowing() {
        assertThat(jwtService.readAccessToken("not-a-jwt")).isEmpty();
        assertThat(jwtService.readAccessToken("")).isEmpty();
    }

    private static void setId(AppUser user, Long id) {
        try {
            var field = AppUser.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
