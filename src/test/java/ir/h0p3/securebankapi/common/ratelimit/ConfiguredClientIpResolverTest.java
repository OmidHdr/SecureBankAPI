package ir.h0p3.securebankapi.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguredClientIpResolverTest {

    @Test
    void forwardedHeaderIsIgnoredWhenTrustIsDisabled() {
        MockHttpServletRequest request = requestWithForwardedHeader();
        ConfiguredClientIpResolver resolver = new ConfiguredClientIpResolver(
                properties(false)
        );

        assertThat(resolver.resolve(request)).isEqualTo("192.0.2.10");
    }

    @Test
    void firstForwardedAddressIsUsedWhenTrustIsExplicitlyEnabled() {
        MockHttpServletRequest request = requestWithForwardedHeader();
        ConfiguredClientIpResolver resolver = new ConfiguredClientIpResolver(
                properties(true)
        );

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.20");
    }

    @Test
    void invalidForwardedValueFallsBackToRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Forwarded-For", "attacker-controlled-value");
        ConfiguredClientIpResolver resolver = new ConfiguredClientIpResolver(
                properties(true)
        );

        assertThat(resolver.resolve(request)).isEqualTo("192.0.2.10");
    }

    private MockHttpServletRequest requestWithForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader(
                "X-Forwarded-For",
                "203.0.113.20, 198.51.100.4"
        );
        return request;
    }

    private RateLimitProperties properties(boolean trustForwardedHeaders) {
        RateLimitProperties.EndpointLimit limit =
                new RateLimitProperties.EndpointLimit(1, Duration.ofMinutes(1));
        return new RateLimitProperties(
                trustForwardedHeaders,
                Duration.ofMinutes(5),
                100,
                limit,
                limit,
                limit,
                limit
        );
    }
}
