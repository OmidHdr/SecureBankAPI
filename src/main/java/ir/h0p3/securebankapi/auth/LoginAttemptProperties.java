package ir.h0p3.securebankapi.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "auth.login-lockout")
public record LoginAttemptProperties(
        @Positive int maxFailedAttempts,
        @NotNull Duration duration
) {
    @AssertTrue(message = "Login lockout duration must be greater than zero")
    public boolean isDurationValid() {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
