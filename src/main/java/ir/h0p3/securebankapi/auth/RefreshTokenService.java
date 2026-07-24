package ir.h0p3.securebankapi.auth;

import ir.h0p3.securebankapi.auth.security.JwtProperties;
import ir.h0p3.securebankapi.common.exception.UnauthorizedException;
import ir.h0p3.securebankapi.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_LENGTH_BYTES = 64;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    @Transactional
    public String generateToken(User user) {
        return createToken(user, LocalDateTime.now()).getToken();
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public RefreshTokenRotation rotateToken(String token) {
        validateTokenValue(token);

        RefreshToken currentToken = refreshTokenRepository
                .findByTokenForUpdate(token)
                .orElseThrow(() -> new UnauthorizedException(
                        "Invalid refresh token"
                ));

        if (Boolean.TRUE.equals(currentToken.getRevoked())) {
            refreshTokenRepository.revokeTokenChain(currentToken.getId());
            log.warn(
                    "Refresh token reuse detected: tokenId={}, userId={}",
                    currentToken.getId(),
                    currentToken.getUser().getId()
            );
            throw new UnauthorizedException("Invalid refresh token");
        }

        LocalDateTime now = LocalDateTime.now();

        if (!currentToken.getExpiresAt().isAfter(now)) {
            currentToken.setRevoked(true);
            throw new UnauthorizedException("Refresh token has expired");
        }

        RefreshToken replacementToken = createToken(
                currentToken.getUser(),
                now
        );

        currentToken.setRevoked(true);
        currentToken.setReplacedBy(replacementToken);
        currentToken.setLastUsedAt(now);

        return new RefreshTokenRotation(
                replacementToken.getToken(),
                replacementToken.getUser().getEmail()
        );
    }

    private RefreshToken createToken(User user, LocalDateTime now) {
        RefreshToken refreshToken = RefreshToken.builder()
                .token(generateTokenValue())
                .user(user)
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMillis(
                        jwtProperties.refreshExpiration()
                )))
                .revoked(false)
                .createdAt(now)
                .build();

        refreshTokenRepository.save(refreshToken);

        return refreshToken;
    }

    private String generateTokenValue() {
        byte[] tokenBytes = new byte[TOKEN_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(tokenBytes);
    }

    private void validateTokenValue(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        try {
            byte[] decodedToken = Base64.getUrlDecoder().decode(token);

            if (decodedToken.length != TOKEN_LENGTH_BYTES) {
                throw new UnauthorizedException("Invalid refresh token");
            }
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException("Invalid refresh token");
        }
    }
}
