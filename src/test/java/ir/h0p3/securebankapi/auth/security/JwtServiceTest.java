package ir.h0p3.securebankapi.auth.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef";
    private static final String OTHER_SECRET =
            "abcdef0123456789abcdef0123456789";
    private static final long EXPIRATION = 60_000;
    private static final long REFRESH_EXPIRATION = 120_000;

    private final JwtService jwtService =
            new JwtService(new JwtProperties(
                    SECRET,
                    EXPIRATION,
                    REFRESH_EXPIRATION
            ));

    @Test
    void generateTokenCreatesAValidToken() {
        String token = jwtService.generateToken("user@example.com");

        assertThat(token).isNotBlank();
        assertThat(jwtService.validateToken(token)).isTrue();
    }

    @Test
    void extractUsernameReturnsTokenSubject() {
        String token = jwtService.generateToken("user@example.com");

        assertThat(jwtService.extractUsername(token))
                .isEqualTo("user@example.com");
    }

    @Test
    void validateTokenAcceptsValidToken() {
        String token = jwtService.generateToken("user@example.com");

        assertThat(jwtService.validateToken(token)).isTrue();
    }

    @Test
    void sessionTokensContainSidAndEnforceTokenPurpose() {
        UUID sessionId = UUID.randomUUID();
        String accessToken = jwtService.generateAccessToken(
                "user@example.com",
                sessionId
        );
        String refreshToken = jwtService.generateRefreshToken(
                "user@example.com",
                sessionId
        );

        assertThat(jwtService.validateAccessToken(accessToken).sessionId())
                .isEqualTo(sessionId);
        assertThat(jwtService.validateRefreshToken(refreshToken).sessionId())
                .isEqualTo(sessionId);
        assertThatThrownBy(
                () -> jwtService.validateRefreshToken(accessToken)
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                () -> jwtService.validateAccessToken(refreshToken)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accessTokenWithoutSidIsRejected() {
        String token = jwtService.generateToken("user@example.com");

        assertThatThrownBy(() -> jwtService.validateAccessToken(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT session id is missing");
    }

    @Test
    void accessTokenWithInvalidSidFormatIsRejected() {
        Date now = new Date();
        String token = Jwts.builder()
                .subject("user@example.com")
                .claim(JwtService.SESSION_ID_CLAIM, "not-a-uuid")
                .claim("token_type", "access")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + EXPIRATION))
                .signWith(
                        Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256
                )
                .compact();

        assertThatThrownBy(() -> jwtService.validateAccessToken(token))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        String token = Jwts.builder()
                .subject("user@example.com")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(
                        Keys.hmacShaKeyFor(
                                SECRET.getBytes(StandardCharsets.UTF_8)
                        ),
                        Jwts.SIG.HS256
                )
                .compact();

        assertThatThrownBy(() -> jwtService.validateToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void malformedTokenIsRejected() {
        assertThatThrownBy(() -> jwtService.validateToken("not-a-jwt"))
                .isInstanceOf(MalformedJwtException.class);
    }

    @Test
    void tokenWithInvalidSignatureIsRejected() {
        JwtService otherJwtService =
                new JwtService(new JwtProperties(
                        OTHER_SECRET,
                        EXPIRATION,
                        REFRESH_EXPIRATION
                ));
        String token = otherJwtService.generateToken("user@example.com");

        assertThatThrownBy(() -> jwtService.validateToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(
                () -> new JwtService(
                        new JwtProperties(
                                "too-short",
                                EXPIRATION,
                                REFRESH_EXPIRATION
                        )
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
