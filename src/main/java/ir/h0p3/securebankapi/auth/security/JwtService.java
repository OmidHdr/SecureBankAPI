package ir.h0p3.securebankapi.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final int MINIMUM_HS256_KEY_LENGTH_BYTES = 32;
    private static final String SESSION_ID_CLAIM = "sid";
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final long expiration;
    private final long refreshExpiration;
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
        if (properties.refreshExpiration() <= 0) {
            throw new IllegalStateException(
                    "JWT refresh expiration must be greater than zero"
            );
        }

        this.expiration = properties.expiration();
        this.refreshExpiration = properties.refreshExpiration();
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateToken(String email) {
        return generateToken(email, null, ACCESS_TOKEN_TYPE, expiration);
    }

    public String generateAccessToken(String email, UUID sessionId) {
        return generateToken(
                email,
                sessionId,
                ACCESS_TOKEN_TYPE,
                expiration
        );
    }

    public String generateRefreshToken(String email, UUID sessionId) {
        return generateToken(
                email,
                sessionId,
                REFRESH_TOKEN_TYPE,
                refreshExpiration
        );
    }

    private String generateToken(
            String email,
            UUID sessionId,
            String tokenType,
            long tokenExpiration
    ) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + tokenExpiration);

        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(email)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(now)
                .expiration(expiryDate);

        if (sessionId != null) {
            builder.claim(SESSION_ID_CLAIM, sessionId.toString());
        }

        return builder
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

    public JwtIdentity validateAccessToken(String token) {
        return validateSessionToken(token, ACCESS_TOKEN_TYPE);
    }

    public JwtIdentity validateRefreshToken(String token) {
        return validateSessionToken(token, REFRESH_TOKEN_TYPE);
    }

    private JwtIdentity validateSessionToken(
            String token,
            String expectedTokenType
    ) {
        Claims claims = extractAllClaims(token);

        if (claims.getSubject() == null
                || claims.getExpiration() == null
                || !claims.getExpiration().after(new Date())
                || !expectedTokenType.equals(
                        claims.get(TOKEN_TYPE_CLAIM, String.class)
                )) {
            throw new IllegalArgumentException("Invalid JWT");
        }

        String sessionId = claims.get(
                SESSION_ID_CLAIM,
                String.class
        );

        if (sessionId == null) {
            throw new IllegalArgumentException(
                    "JWT session id is missing"
            );
        }

        return new JwtIdentity(
                claims.getSubject(),
                UUID.fromString(sessionId)
        );
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
