package ir.h0p3.securebankapi.common.ratelimit;

public interface RateLimitBucketStore {

    RateLimitDecision consume(
            String clientIp,
            RateLimitedEndpoint endpoint
    );
}
