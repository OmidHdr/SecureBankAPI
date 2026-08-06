package ir.h0p3.securebankapi.common.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        boolean production,
        @NotEmpty List<String> allowedOrigins,
        @NotEmpty List<String> allowedMethods,
        @NotEmpty List<String> allowedHeaders,
        boolean allowCredentials,
        Duration maxAge
) {
    @AssertTrue(message = "Production CORS must not combine credentials with wildcard origins")
    public boolean isProductionConfigurationSafe() {
        return !production
                || !allowCredentials
                || (allowedOrigins != null
                && allowedOrigins.stream().noneMatch("*"::equals));
    }

    @AssertTrue(message = "CORS max age must not be negative")
    public boolean isMaxAgeValid() {
        return maxAge != null && !maxAge.isNegative();
    }

    @AssertTrue(message = "CORS origins must be wildcard or absolute HTTP(S) origins")
    public boolean areOriginsValid() {
        return allowedOrigins != null
                && allowedOrigins.stream().allMatch(this::isValidOrigin);
    }

    private boolean isValidOrigin(String origin) {
        if ("*".equals(origin)) {
            return true;
        }
        try {
            URI uri = new URI(origin);
            return uri.isAbsolute()
                    && uri.getHost() != null
                    && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
