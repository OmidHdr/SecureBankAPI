package ir.h0p3.securebankapi.common.config;

import ir.h0p3.securebankapi.auth.LoginAttemptProperties;
import ir.h0p3.securebankapi.auth.security.JwtProperties;
import ir.h0p3.securebankapi.common.ratelimit.RateLimitProperties;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationPropertiesValidationTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void productionRejectsCredentialsWithWildcardCorsOrigin() {
        CorsProperties properties = new CorsProperties(
                true,
                List.of("*"),
                List.of("GET"),
                List.of("Authorization"),
                true,
                Duration.ofHours(1)
        );

        assertThat(validator.validate(properties))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("productionConfigurationSafe");
    }

    @Test
    void weakJwtSecretIsInvalid() {
        JwtProperties properties = new JwtProperties("too-short", 1, 1);

        assertThat(validator.validate(properties)).isNotEmpty();
    }

    @Test
    void nonPositiveRateLimitDurationsAreInvalid() {
        RateLimitProperties.EndpointLimit invalidEndpoint =
                new RateLimitProperties.EndpointLimit(1, Duration.ZERO);
        RateLimitProperties properties = new RateLimitProperties(
                false,
                Duration.ZERO,
                100,
                invalidEndpoint,
                invalidEndpoint,
                invalidEndpoint,
                invalidEndpoint
        );

        assertThat(validator.validate(properties)).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void nonPositiveLoginLockoutSettingsAreInvalid() {
        LoginAttemptProperties properties =
                new LoginAttemptProperties(0, Duration.ZERO);

        assertThat(validator.validate(properties)).hasSize(2);
    }
}
