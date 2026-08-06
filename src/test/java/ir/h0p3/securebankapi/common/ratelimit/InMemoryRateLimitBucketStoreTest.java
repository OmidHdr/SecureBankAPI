package ir.h0p3.securebankapi.common.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimitBucketStoreTest {

    private MutableClock clock;
    private InMemoryRateLimitBucketStore store;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-08-06T10:00:00Z"));
        store = new InMemoryRateLimitBucketStore(properties(), clock);
    }

    @Test
    void requestsBelowLimitAreAllowed() {
        RateLimitDecision first = store.consume("192.0.2.1", RateLimitedEndpoint.LOGIN);
        RateLimitDecision second = store.consume("192.0.2.1", RateLimitedEndpoint.LOGIN);

        assertThat(first.allowed()).isTrue();
        assertThat(first.remaining()).isEqualTo(2);
        assertThat(second.allowed()).isTrue();
        assertThat(second.remaining()).isEqualTo(1);
    }

    @Test
    void requestThatExhaustsLimitIsAllowedWithNoRemainingTokens() {
        store.consume("192.0.2.1", RateLimitedEndpoint.LOGIN);
        store.consume("192.0.2.1", RateLimitedEndpoint.LOGIN);

        RateLimitDecision decision = store.consume("192.0.2.1", RateLimitedEndpoint.LOGIN);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remaining()).isZero();
    }

    @Test
    void requestAboveLimitIsRejected() {
        for (int request = 0; request < 3; request++) {
            store.consume("192.0.2.1", RateLimitedEndpoint.LOGIN);
        }

        RateLimitDecision decision = store.consume("192.0.2.1", RateLimitedEndpoint.LOGIN);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.remaining()).isZero();
        assertThat(decision.retryAfterSeconds()).isPositive();
    }

    @Test
    void differentIpAddressesHaveIndependentBuckets() {
        for (int request = 0; request < 3; request++) {
            store.consume("192.0.2.1", RateLimitedEndpoint.LOGIN);
        }

        assertThat(store.consume("192.0.2.1", RateLimitedEndpoint.LOGIN).allowed())
                .isFalse();
        assertThat(store.consume("192.0.2.2", RateLimitedEndpoint.LOGIN).allowed())
                .isTrue();
    }

    @Test
    void endpointsUseSeparateBucketsAndLimits() {
        store.consume("192.0.2.1", RateLimitedEndpoint.REGISTER);

        assertThat(store.consume("192.0.2.1", RateLimitedEndpoint.REGISTER).allowed())
                .isFalse();
        assertThat(store.consume("192.0.2.1", RateLimitedEndpoint.LOGIN).allowed())
                .isTrue();
        assertThat(store.consume("192.0.2.1", RateLimitedEndpoint.LOGIN).remaining())
                .isEqualTo(1);
    }

    @Test
    void inactiveBucketsExpireDeterministically() {
        store.consume("192.0.2.1", RateLimitedEndpoint.LOGIN);
        assertThat(store.activeBucketCount()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(6));
        store.cleanUp();

        assertThat(store.activeBucketCount()).isZero();
    }

    private RateLimitProperties properties() {
        return new RateLimitProperties(
                false,
                Duration.ofMinutes(5),
                100,
                new RateLimitProperties.EndpointLimit(3, Duration.ofMinutes(1)),
                new RateLimitProperties.EndpointLimit(1, Duration.ofMinutes(1)),
                new RateLimitProperties.EndpointLimit(4, Duration.ofMinutes(1)),
                new RateLimitProperties.EndpointLimit(4, Duration.ofMinutes(1))
        );
    }
}
