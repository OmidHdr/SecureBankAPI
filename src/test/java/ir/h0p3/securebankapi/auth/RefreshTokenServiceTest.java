package ir.h0p3.securebankapi.auth;

import ir.h0p3.securebankapi.auth.security.JwtProperties;
import ir.h0p3.securebankapi.user.User;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenServiceTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef";
    private static final long ACCESS_EXPIRATION = 60_000;
    private static final long REFRESH_EXPIRATION = 120_000;

    @Test
    void generateTokenStoresRefreshTokenWithConfiguredExpiration() {
        AtomicReference<RefreshToken> savedTokenReference =
                new AtomicReference<>();
        RefreshTokenRepository refreshTokenRepository =
                createRepository(savedTokenReference);
        RefreshTokenService refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                new JwtProperties(
                        SECRET,
                        ACCESS_EXPIRATION,
                        REFRESH_EXPIRATION
                )
        );
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .build();

        String token = refreshTokenService.generateToken(user);

        RefreshToken savedToken = savedTokenReference.get();
        assertThat(token).isNotBlank();
        assertThat(savedToken.getToken()).isEqualTo(token);
        assertThat(savedToken.getUser()).isSameAs(user);
        assertThat(savedToken.getRevoked()).isFalse();
        assertThat(savedToken.getCreatedAt())
                .isEqualTo(savedToken.getIssuedAt());
        assertThat(Duration.between(
                savedToken.getIssuedAt(),
                savedToken.getExpiresAt()
        )).isEqualTo(Duration.ofMillis(REFRESH_EXPIRATION));
        assertThat(savedToken.getReplacedBy()).isNull();
        assertThat(savedToken.getLastUsedAt()).isNull();
    }

    private RefreshTokenRepository createRepository(
            AtomicReference<RefreshToken> savedTokenReference
    ) {
        return (RefreshTokenRepository) Proxy.newProxyInstance(
                RefreshTokenRepository.class.getClassLoader(),
                new Class<?>[]{RefreshTokenRepository.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("save")) {
                        RefreshToken refreshToken =
                                (RefreshToken) arguments[0];
                        savedTokenReference.set(refreshToken);
                        return refreshToken;
                    }

                    throw new UnsupportedOperationException(
                            method.getName()
                    );
                }
        );
    }
}
