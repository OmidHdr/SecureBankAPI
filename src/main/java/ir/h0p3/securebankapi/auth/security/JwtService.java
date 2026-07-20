package ir.h0p3.securebankapi.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private static final int MINIMUM_HS256_KEY_LENGTH_BYTES = 32;

    private final long expiration;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        byte[] secretBytes = properties.secret()
                .getBytes(StandardCharsets.UTF_8);

        if (secretBytes.length < MINIMUM_HS256_KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "JWT secret must be at least 32 bytes (256 bits) for HS256"
            );
        }
        if (properties.expiration() <= 0) {
            throw new IllegalStateException(
                    "JWT expiration must be greater than zero"
            );
        }

        this.expiration = properties.expiration();
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateToken(String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean validateToken(String token) {
        Claims claims = extractAllClaims(token);
        return claims.getSubject() != null
                && claims.getExpiration() != null
                && claims.getExpiration().after(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
