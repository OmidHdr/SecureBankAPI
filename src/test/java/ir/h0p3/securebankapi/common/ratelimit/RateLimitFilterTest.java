package ir.h0p3.securebankapi.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    @Test
    void rejectedRequestReturnsProjectErrorBodyAndRateLimitHeaders()
            throws Exception {
        RateLimitBucketStore store = (clientIp, endpoint) ->
                new RateLimitDecision(false, 10, 0, 17);
        RateLimitFilter filter = new RateLimitFilter(
                request -> request.getRemoteAddr(),
                store,
                JsonMapper.builder().findAndAddModules().build(),
                new MutableClock(Instant.parse("2026-08-06T10:00:00Z"))
        );
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/auth/login"
        );
        request.setServletPath("/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("17");
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("10");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(response.getContentAsString())
                .contains("\"status\":429")
                .contains("\"error\":\"Too Many Requests\"")
                .contains("\"path\":\"/api/auth/login\"");
    }

    @Test
    void allowedRequestContinuesThroughFilterChain() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(
                request -> request.getRemoteAddr(),
                (clientIp, endpoint) -> new RateLimitDecision(true, 10, 9, 0),
                JsonMapper.builder().findAndAddModules().build(),
                new MutableClock(Instant.parse("2026-08-06T10:00:00Z"))
        );
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/auth/login"
        );
        request.setServletPath("/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                chainCalled.set(true));

        assertThat(chainCalled).isTrue();
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("9");
    }
}
