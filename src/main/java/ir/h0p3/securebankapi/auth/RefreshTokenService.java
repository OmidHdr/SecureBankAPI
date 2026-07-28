package ir.h0p3.securebankapi.auth;

import ir.h0p3.securebankapi.auth.security.JwtProperties;
import ir.h0p3.securebankapi.auth.security.JwtIdentity;
import ir.h0p3.securebankapi.auth.security.JwtService;
import ir.h0p3.securebankapi.auth.security.AuthenticationMessages;
import ir.h0p3.securebankapi.common.exception.UnauthorizedException;
import ir.h0p3.securebankapi.user.User;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int MAXIMUM_TOKEN_LENGTH = 512;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final JwtService jwtService;
    private final SessionService sessionService;

    @Transactional
    public String generateToken(User user, Session session) {
        return createToken(
                user,
                session,
                LocalDateTime.now()
        ).getToken();
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public RefreshTokenRotation rotateToken(String token) {
        validateTokenValue(token);

        RefreshToken currentToken = refreshTokenRepository
                .findByTokenForUpdate(token)
                .orElseThrow(() -> new UnauthorizedException(
                        AuthenticationMessages.INVALID_REFRESH_TOKEN
                ));

        if (Boolean.TRUE.equals(currentToken.getRevoked())) {
            refreshTokenRepository.revokeTokenChain(currentToken.getId());
            log.warn(
                    "Refresh token reuse detected: tokenId={}, userId={}",
                    currentToken.getId(),
                    currentToken.getUser().getId()
            );
            throw new UnauthorizedException(AuthenticationMessages.INVALID_REFRESH_TOKEN);
        }

        LocalDateTime now = LocalDateTime.now();

        if (!currentToken.getExpiresAt().isAfter(now)) {
            currentToken.setRevoked(true);
            throw new UnauthorizedException("Refresh token has expired");
        }

        JwtIdentity identity = validateRefreshToken(token);
        validateTokenOwnership(currentToken, identity);
        Session session = sessionService.requireActiveSessionForUpdate(
                identity.sessionId(),
                identity.email(),
                now
        );
        session.setLastActivityAt(now);

        RefreshToken replacementToken = createToken(
                currentToken.getUser(),
                session,
                now
        );

        currentToken.setRevoked(true);
        currentToken.setReplacedBy(replacementToken);
        currentToken.setLastUsedAt(now);

        return new RefreshTokenRotation(
                replacementToken.getToken(),
                identity.email(),
                identity.sessionId()
        );
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public void revokeSession(String token) {
        validateTokenValue(token);

        RefreshToken currentToken = refreshTokenRepository
                .findByTokenForUpdate(token)
                .orElseThrow(() -> new UnauthorizedException(
                        AuthenticationMessages.INVALID_REFRESH_TOKEN
                ));

        if (Boolean.TRUE.equals(currentToken.getRevoked())) {
            refreshTokenRepository.revokeTokenChain(currentToken.getId());
            throw new UnauthorizedException(AuthenticationMessages.INVALID_REFRESH_TOKEN);
        }

        LocalDateTime now = LocalDateTime.now();

        if (!currentToken.getExpiresAt().isAfter(now)) {
            currentToken.setRevoked(true);
            throw new UnauthorizedException("Refresh token has expired");
        }

        JwtIdentity identity = validateRefreshToken(token);
        validateTokenOwnership(currentToken, identity);
        Session session = sessionService.requireActiveSessionForUpdate(
                identity.sessionId(),
                identity.email(),
                now
        );

        session.setRevoked(true);
        session.setLastActivityAt(now);
        refreshTokenRepository.revokeAllBySessionId(session.getId());
    }

    private RefreshToken createToken(
            User user,
            Session session,
            LocalDateTime now
    ) {
        LocalDateTime refreshTokenExpiresAt = now.plus(
                Duration.ofMillis(jwtProperties.refreshExpiration())
        );

        if (refreshTokenExpiresAt.isAfter(session.getExpiresAt())) {
            refreshTokenExpiresAt = session.getExpiresAt();
        }

        RefreshToken refreshToken = RefreshToken.builder()
                .token(jwtService.generateRefreshToken(
                        user.getEmail(),
                        session.getId()
                ))
                .user(user)
                .session(session)
                .issuedAt(now)
                .expiresAt(refreshTokenExpiresAt)
                .revoked(false)
                .createdAt(now)
                .build();

        refreshTokenRepository.save(refreshToken);

        return refreshToken;
    }

    private void validateTokenValue(String token) {
        if (token == null
                || token.isBlank()
                || token.length() > MAXIMUM_TOKEN_LENGTH) {
            throw new UnauthorizedException(AuthenticationMessages.INVALID_REFRESH_TOKEN);
        }
    }

    private JwtIdentity validateRefreshToken(String token) {
        try {
            return jwtService.validateRefreshToken(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new UnauthorizedException(AuthenticationMessages.INVALID_REFRESH_TOKEN);
        }
    }

    private void validateTokenOwnership(
            RefreshToken refreshToken,
            JwtIdentity identity
    ) {
        if (!refreshToken.getSession().getId()
                .equals(identity.sessionId())
                || !refreshToken.getUser().getEmail()
                .equals(identity.email())) {
            throw new UnauthorizedException(AuthenticationMessages.INVALID_REFRESH_TOKEN);
        }
    }
}
