package ir.h0p3.securebankapi.common.ratelimit;

import java.util.Arrays;
import java.util.Optional;

public enum RateLimitedEndpoint {
    LOGIN("/api/auth/login"),
    REGISTER("/api/auth/register"),
    REFRESH("/api/auth/refresh"),
    LOGOUT("/api/auth/logout");

    private final String path;

    RateLimitedEndpoint(String path) {
        this.path = path;
    }

    public static Optional<RateLimitedEndpoint> fromPath(String path) {
        return Arrays.stream(values())
                .filter(endpoint -> endpoint.path.equals(path))
                .findFirst();
    }
}
