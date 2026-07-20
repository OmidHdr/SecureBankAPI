package ir.h0p3.securebankapi.auth.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef";
    private static final String OTHER_SECRET =
            "abcdef0123456789abcdef0123456789";
    private static final long EXPIRATION = 60_000;

    private final JwtService jwtService =
            new JwtService(new JwtProperties(SECRET, EXPIRATION));

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
                new JwtService(new JwtProperties(OTHER_SECRET, EXPIRATION));
        String token = otherJwtService.generateToken("user@example.com");

        assertThatThrownBy(() -> jwtService.validateToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(
                () -> new JwtService(
                        new JwtProperties("too-short", EXPIRATION)
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
