package ir.h0p3.securebankapi.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import io.github.bucket4j.TimeMeter;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class InMemoryRateLimitBucketStore implements RateLimitBucketStore {

    private final RateLimitProperties properties;
    private final Cache<BucketKey, Bucket> buckets;
    private final TimeMeter timeMeter;

    public InMemoryRateLimitBucketStore(
            RateLimitProperties properties,
            Clock clock
    ) {
        this.properties = properties;
        this.timeMeter = new ClockTimeMeter(clock);
        this.buckets = Caffeine.newBuilder()
                .maximumSize(properties.maximumBuckets())
                .expireAfterAccess(properties.bucketExpiration())
                .ticker(() -> timeMeter.currentTimeNanos())
                .build();
    }

    @Override
    public RateLimitDecision consume(
            String clientIp,
            RateLimitedEndpoint endpoint
    ) {
        RateLimitProperties.EndpointLimit limit = limitFor(endpoint);
        Bucket bucket = buckets.get(
                new BucketKey(clientIp, endpoint),
                ignored -> createBucket(limit)
        );
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        long retryAfterSeconds = probe.isConsumed()
                ? 0
                : secondsRoundedUp(probe.getNanosToWaitForRefill());
        return new RateLimitDecision(
                probe.isConsumed(),
                limit.requests(),
                probe.getRemainingTokens(),
                retryAfterSeconds
        );
    }

    long activeBucketCount() {
        return buckets.estimatedSize();
    }

    void cleanUp() {
        buckets.cleanUp();
    }

    private Bucket createBucket(RateLimitProperties.EndpointLimit limit) {
        Bandwidth bandwidth = Bandwidth.classic(
                limit.requests(),
                Refill.greedy(limit.requests(), limit.window())
        );
        return Bucket.builder()
                .withCustomTimePrecision(timeMeter)
                .addLimit(bandwidth)
                .build();
    }

    private long secondsRoundedUp(long nanoseconds) {
        long wholeSeconds = TimeUnit.NANOSECONDS.toSeconds(nanoseconds);
        return Math.max(1, wholeSeconds
                + (nanoseconds % TimeUnit.SECONDS.toNanos(1) == 0 ? 0 : 1));
    }

    private RateLimitProperties.EndpointLimit limitFor(
            RateLimitedEndpoint endpoint
    ) {
        return switch (endpoint) {
            case LOGIN -> properties.login();
            case REGISTER -> properties.register();
            case REFRESH -> properties.refresh();
            case LOGOUT -> properties.logout();
        };
    }

    private record BucketKey(
            String clientIp,
            RateLimitedEndpoint endpoint
    ) {
    }

    private record ClockTimeMeter(Clock clock) implements TimeMeter {
        @Override
        public long currentTimeNanos() {
            return TimeUnit.MILLISECONDS.toNanos(clock.millis());
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
