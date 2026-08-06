package ir.h0p3.securebankapi.common.ratelimit;

import ir.h0p3.securebankapi.common.response.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LIMIT_HEADER = "X-RateLimit-Limit";
    private static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    private final ClientIpResolver clientIpResolver;
    private final RateLimitBucketStore bucketStore;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public RateLimitFilter(
            ClientIpResolver clientIpResolver,
            RateLimitBucketStore bucketStore,
            JsonMapper jsonMapper,
            Clock clock
    ) {
        this.clientIpResolver = clientIpResolver;
        this.bucketStore = bucketStore;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        Optional<RateLimitedEndpoint> endpoint = endpointFor(request);
        if (endpoint.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitDecision decision = bucketStore.consume(
                clientIpResolver.resolve(request),
                endpoint.orElseThrow()
        );
        response.setHeader(LIMIT_HEADER, Long.toString(decision.limit()));
        response.setHeader(REMAINING_HEADER, Long.toString(decision.remaining()));

        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(
                HttpHeaders.RETRY_AFTER,
                Long.toString(decision.retryAfterSeconds())
        );
        jsonMapper.writeValue(response.getWriter(), new ApiError(
                LocalDateTime.now(clock),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Too many requests. Please try again later",
                request.getRequestURI(),
                null
        ));
    }

    private Optional<RateLimitedEndpoint> endpointFor(
            HttpServletRequest request
    ) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return Optional.empty();
        }
        String requestPath = request.getRequestURI()
                .substring(request.getContextPath().length());
        return RateLimitedEndpoint.fromPath(requestPath);
    }
}
