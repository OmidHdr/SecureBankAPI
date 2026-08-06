package ir.h0p3.securebankapi.auth;

import ir.h0p3.securebankapi.common.exception.AccountLockedException;
import ir.h0p3.securebankapi.user.User;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final String ACCOUNT_LOCKED_MESSAGE =
            "Account is temporarily locked. Try again in 15 minutes";

    private final LoginAttemptService service = new LoginAttemptService(
            CLOCK,
            new LoginAttemptProperties(MAX_FAILED_ATTEMPTS, LOCK_DURATION)
    );

    @Test
    void fifthConsecutiveFailureLocksAccount() {
        User user = User.builder()
                .failedAttempts(4)
                .build();

        assertThatThrownBy(() -> service.recordFailedAttempt(user))
                .isInstanceOf(AccountLockedException.class)
                .hasMessage(ACCOUNT_LOCKED_MESSAGE);

        assertThat(user.getFailedAttempts())
                .isEqualTo(MAX_FAILED_ATTEMPTS);
        assertThat(user.isAccountLocked()).isTrue();
        assertThat(user.getLockTime())
                .isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    void activeLockRejectsLoginWithoutChangingState() {
        LocalDateTime lockTime = LocalDateTime.ofInstant(
                NOW.minusSeconds(60),
                ZoneOffset.UTC
        );
        User user = User.builder()
                .failedAttempts(5)
                .accountLocked(true)
                .lockTime(lockTime)
                .build();

        assertThatThrownBy(() -> service.ensureLoginAllowed(user))
                .isInstanceOf(AccountLockedException.class);
        assertThat(user.getFailedAttempts()).isEqualTo(5);
        assertThat(user.getLockTime()).isEqualTo(lockTime);
    }

    @Test
    void expiredLockIsCleared() {
        User user = User.builder()
                .failedAttempts(5)
                .accountLocked(true)
                .lockTime(LocalDateTime.ofInstant(
                        NOW.minus(LOCK_DURATION),
                        ZoneOffset.UTC
                ))
                .build();

        assertThatCode(() -> service.ensureLoginAllowed(user))
                .doesNotThrowAnyException();

        assertReset(user);
    }

    @Test
    void successfulLoginResetsFailedAttemptsAndLockState() {
        User user = User.builder()
                .failedAttempts(3)
                .accountLocked(false)
                .build();

        service.recordSuccessfulLogin(user);

        assertReset(user);
    }

    private void assertReset(User user) {
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.isAccountLocked()).isFalse();
        assertThat(user.getLockTime()).isNull();
    }
}
