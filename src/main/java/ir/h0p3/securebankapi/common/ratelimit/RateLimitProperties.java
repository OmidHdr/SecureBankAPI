package ir.h0p3.securebankapi.common.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(
        boolean trustForwardedHeaders,
        @NotNull Duration bucketExpiration,
        @Positive long maximumBuckets,
        @Valid @NotNull EndpointLimit login,
        @Valid @NotNull EndpointLimit register,
        @Valid @NotNull EndpointLimit refresh,
        @Valid @NotNull EndpointLimit logout
) {
    public record EndpointLimit(
            @Positive long requests,
            @NotNull Duration window
    ) {
    }
}
