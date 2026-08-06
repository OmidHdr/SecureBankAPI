package ir.h0p3.securebankapi.common.ratelimit;

public record RateLimitDecision(
        boolean allowed,
        long limit,
        long remaining,
        long retryAfterSeconds
) {
}
