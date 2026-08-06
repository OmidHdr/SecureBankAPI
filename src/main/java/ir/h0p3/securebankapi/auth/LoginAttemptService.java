package ir.h0p3.securebankapi.auth;

import ir.h0p3.securebankapi.common.exception.AccountLockedException;
import ir.h0p3.securebankapi.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    private final Clock clock;
    private final LoginAttemptProperties properties;

    public void ensureLoginAllowed(User user) {
        if (!user.isAccountLocked()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime lockTime = user.getLockTime();

        if (lockTime != null
                && !now.isBefore(lockTime.plus(properties.duration()))) {
            reset(user);
            log.info("Expired login lock cleared: userId={}", user.getId());
            return;
        }

        log.warn("Login rejected for locked account: userId={}", user.getId());
        throw new AccountLockedException(accountLockedMessage());
    }

    public void recordFailedAttempt(User user) {
        int failedAttempts = user.getFailedAttempts() + 1;
        user.setFailedAttempts(failedAttempts);

        if (failedAttempts >= properties.maxFailedAttempts()) {
            user.setFailedAttempts(properties.maxFailedAttempts());
            user.setAccountLocked(true);
            user.setLockTime(LocalDateTime.now(clock));
            log.warn(
                    "Account locked after repeated login failures: userId={}",
                    user.getId()
            );
            throw new AccountLockedException(accountLockedMessage());
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

    private String accountLockedMessage() {
        return "Account is temporarily locked. Try again in "
                + properties.duration().toMinutes() + " minutes";
    }
}
