package ir.h0p3.securebankapi.auth;

import ir.h0p3.securebankapi.auth.security.JwtProperties;
import ir.h0p3.securebankapi.common.exception.UnauthorizedException;
import ir.h0p3.securebankapi.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionServiceTest {

    private SessionRepository repository;
    private SessionService service;
    private User user;

    @BeforeEach
    void setUp() {
        repository = mock(SessionRepository.class);
        service = new SessionService(
                repository,
                new JwtProperties(
                        "0123456789abcdef0123456789abcdef",
                        60_000,
                        120_000
                )
        );
        user = User.builder().id(1L).email("user@example.com").build();
    }

    @Test
    void createSessionPersistsActiveSession() {
        when(repository.save(org.mockito.ArgumentMatchers.any(Session.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Session session = service.createSession(user);

        assertThat(session.getUser()).isSameAs(user);
        assertThat(session.getRevoked()).isFalse();
        assertThat(session.getExpiresAt()).isAfter(session.getCreatedAt());
        verify(repository).save(session);
    }

    @Test
    void validActiveSessionIsAcceptedAndTouched() {
        UUID id = UUID.randomUUID();
        Session session = session(id, false, LocalDateTime.now().plusMinutes(5));
        LocalDateTime previousActivity = LocalDateTime.now().minusMinutes(1);
        session.setLastActivityAt(previousActivity);
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(session));

        service.validateAndTouch(id, user.getEmail());

        assertThat(session.getLastActivityAt()).isAfter(previousActivity);
    }

    @Test
    void nonexistentRevokedExpiredAndWrongOwnerSessionsAreRejected() {
        UUID missingId = UUID.randomUUID();
        when(repository.findByIdForUpdate(missingId)).thenReturn(Optional.empty());
        assertRejected(missingId, user.getEmail());

        UUID revokedId = UUID.randomUUID();
        when(repository.findByIdForUpdate(revokedId))
                .thenReturn(Optional.of(session(
                        revokedId, true, LocalDateTime.now().plusMinutes(5)
                )));
        assertRejected(revokedId, user.getEmail());

        UUID expiredId = UUID.randomUUID();
        when(repository.findByIdForUpdate(expiredId))
                .thenReturn(Optional.of(session(
                        expiredId, false, LocalDateTime.now().minusMinutes(1)
                )));
        assertRejected(expiredId, user.getEmail());

        UUID wrongOwnerId = UUID.randomUUID();
        when(repository.findByIdForUpdate(wrongOwnerId))
                .thenReturn(Optional.of(session(
                        wrongOwnerId, false, LocalDateTime.now().plusMinutes(5)
                )));
        assertRejected(wrongOwnerId, "other@example.com");
    }

    private void assertRejected(UUID id, String email) {
        assertThatThrownBy(() -> service.validateAndTouch(id, email))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid or expired session");
    }

    private Session session(UUID id, boolean revoked, LocalDateTime expiresAt) {
        return Session.builder()
                .id(id)
                .user(user)
                .revoked(revoked)
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .lastActivityAt(LocalDateTime.now().minusMinutes(1))
                .expiresAt(expiresAt)
                .build();
    }
}
