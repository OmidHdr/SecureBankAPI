package ir.h0p3.securebankapi.auth;

import ir.h0p3.securebankapi.common.exception.AccountLockedException;
import ir.h0p3.securebankapi.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    static final String ACCOUNT_LOCKED_MESSAGE =
            "Account is temporarily locked. Try again in 15 minutes";

    private final Clock clock;

    public void ensureLoginAllowed(User user) {
        if (!user.isAccountLocked()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime lockTime = user.getLockTime();

        if (lockTime != null
                && !now.isBefore(lockTime.plus(LOCK_DURATION))) {
            reset(user);
            log.info("Expired login lock cleared: userId={}", user.getId());
            return;
        }

        log.warn("Login rejected for locked account: userId={}", user.getId());
        throw new AccountLockedException(ACCOUNT_LOCKED_MESSAGE);
    }

    public void recordFailedAttempt(User user) {
        int failedAttempts = user.getFailedAttempts() + 1;
        user.setFailedAttempts(failedAttempts);

        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            user.setFailedAttempts(MAX_FAILED_ATTEMPTS);
            user.setAccountLocked(true);
            user.setLockTime(LocalDateTime.now(clock));
            log.warn(
                    "Account locked after repeated login failures: userId={}",
                    user.getId()
            );
            throw new AccountLockedException(ACCOUNT_LOCKED_MESSAGE);
        }
    }

    public void recordSuccessfulLogin(User user) {
        reset(user);
    }

    private void reset(User user) {
        user.setFailedAttempts(0);
        user.setAccountLocked(false);
        user.setLockTime(null);
    }
}
