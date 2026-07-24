package ir.h0p3.securebankapi.auth;

import ir.h0p3.securebankapi.auth.security.JwtProperties;
import ir.h0p3.securebankapi.common.exception.UnauthorizedException;
import ir.h0p3.securebankapi.user.User;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenServiceTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef";
    private static final long ACCESS_EXPIRATION = 60_000;
    private static final long REFRESH_EXPIRATION = 120_000;

    @Test
    void generateTokenStoresRefreshTokenWithConfiguredExpiration() {
        InMemoryRefreshTokenRepository repository =
                new InMemoryRefreshTokenRepository();
        RefreshTokenService refreshTokenService = createService(repository);
        User user = createUser();

        String token = refreshTokenService.generateToken(user);

        RefreshToken savedToken = repository.tokens.getFirst();
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

    @Test
    void rotateTokenRevokesAndLinksPreviousToken() {
        InMemoryRefreshTokenRepository repository =
                new InMemoryRefreshTokenRepository();
        RefreshTokenService refreshTokenService = createService(repository);
        User user = createUser();
        String previousToken = refreshTokenService.generateToken(user);

        RefreshTokenRotation rotation =
                refreshTokenService.rotateToken(previousToken);

        RefreshToken previous = repository.tokens.get(0);
        RefreshToken replacement = repository.tokens.get(1);
        assertThat(rotation.refreshToken())
                .isEqualTo(replacement.getToken())
                .isNotEqualTo(previousToken);
        assertThat(rotation.email()).isEqualTo(user.getEmail());
        assertThat(previous.getRevoked()).isTrue();
        assertThat(previous.getReplacedBy()).isSameAs(replacement);
        assertThat(previous.getLastUsedAt()).isNotNull();
        assertThat(replacement.getRevoked()).isFalse();
    }

    @Test
    void reusedTokenRevokesReplacementChainAndIsRejected() {
        InMemoryRefreshTokenRepository repository =
                new InMemoryRefreshTokenRepository();
        RefreshTokenService refreshTokenService = createService(repository);
        String previousToken =
                refreshTokenService.generateToken(createUser());
        refreshTokenService.rotateToken(previousToken);

        assertThatThrownBy(
                () -> refreshTokenService.rotateToken(previousToken)
        )
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");

        assertThat(repository.tokens)
                .allMatch(RefreshToken::getRevoked);
    }

    private RefreshTokenService createService(
            InMemoryRefreshTokenRepository repository
    ) {
        return new RefreshTokenService(
                repository.proxy(),
                new JwtProperties(
                        SECRET,
                        ACCESS_EXPIRATION,
                        REFRESH_EXPIRATION
                )
        );
    }

    private User createUser() {
        return User.builder()
                .id(1L)
                .email("user@example.com")
                .build();
    }

    private static final class InMemoryRefreshTokenRepository
            implements InvocationHandler {

        private final List<RefreshToken> tokens = new ArrayList<>();
        private long nextId = 1;

        private RefreshTokenRepository proxy() {
            return (RefreshTokenRepository) Proxy.newProxyInstance(
                    RefreshTokenRepository.class.getClassLoader(),
                    new Class<?>[]{RefreshTokenRepository.class},
                    this
            );
        }

        @Override
        public Object invoke(
                Object proxy,
                Method method,
                Object[] arguments
        ) {
            return switch (method.getName()) {
                case "save" -> save((RefreshToken) arguments[0]);
                case "findByToken", "findByTokenForUpdate" ->
                        findByToken((String) arguments[0]);
                case "revokeTokenChain" ->
                        revokeTokenChain((Long) arguments[0]);
                default -> throw new UnsupportedOperationException(
                        method.getName()
                );
            };
        }

        private RefreshToken save(RefreshToken refreshToken) {
            if (refreshToken.getId() == null) {
                refreshToken.setId(nextId++);
                tokens.add(refreshToken);
            }

            return refreshToken;
        }

        private Optional<RefreshToken> findByToken(String token) {
            return tokens.stream()
                    .filter(candidate -> candidate.getToken().equals(token))
                    .findFirst();
        }

        private int revokeTokenChain(Long tokenId) {
            RefreshToken current = tokens.stream()
                    .filter(token -> token.getId().equals(tokenId))
                    .findFirst()
                    .orElse(null);
            int revokedCount = 0;

            while (current != null) {
                current.setRevoked(true);
                revokedCount++;
                current = current.getReplacedBy();
            }

            return revokedCount;
        }
    }
}
