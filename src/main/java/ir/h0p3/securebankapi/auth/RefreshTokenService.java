package ir.h0p3.securebankapi.auth;

import ir.h0p3.securebankapi.auth.security.JwtProperties;
import ir.h0p3.securebankapi.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_LENGTH_BYTES = 64;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    public String generateToken(User user) {
        LocalDateTime now = LocalDateTime.now();
        String token = generateTokenValue();

        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .user(user)
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMillis(
                        jwtProperties.refreshExpiration()
                )))
                .revoked(false)
                .createdAt(now)
                .build();

        refreshTokenRepository.save(refreshToken);

        return token;
    }

    private String generateTokenValue() {
        byte[] tokenBytes = new byte[TOKEN_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(tokenBytes);
    }
}
