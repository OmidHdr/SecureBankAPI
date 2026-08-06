package ir.h0p3.securebankapi.auth;

import ir.h0p3.securebankapi.auth.security.JwtProperties;
import ir.h0p3.securebankapi.auth.security.AuthenticationMessages;
import ir.h0p3.securebankapi.common.exception.UnauthorizedException;
import ir.h0p3.securebankapi.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final JwtProperties jwtProperties;

    @Transactional
    public Session createSession(User user) {
        LocalDateTime now = LocalDateTime.now();

        return sessionRepository.save(
                Session.builder()
                        .user(user)
                        .revoked(false)
                        .createdAt(now)
                        .lastActivityAt(now)
                        .expiresAt(now.plus(Duration.ofMillis(
                                jwtProperties.refreshExpiration()
                        )))
                        .build()
        );
    }

    @Transactional
    public void validateAndTouch(UUID sessionId, String email) {
        LocalDateTime now = LocalDateTime.now();
        Session session = requireActiveSessionForUpdate(
                sessionId,
                email,
                now
        );
        session.setLastActivityAt(now);
    }

    Session requireActiveSessionForUpdate(
            UUID sessionId,
            String email,
            LocalDateTime now
    ) {
        Session session = sessionRepository
                .findByIdForUpdate(sessionId)
                .orElseThrow(() -> new UnauthorizedException(
                        AuthenticationMessages.INVALID_SESSION
                ));

        if (Boolean.TRUE.equals(session.getRevoked())
                || !session.getExpiresAt().isAfter(now)
                || !session.getUser().getEmail().equals(email)) {
            throw new UnauthorizedException(
                    AuthenticationMessages.INVALID_SESSION
            );
        }

        return session;
    }
}
