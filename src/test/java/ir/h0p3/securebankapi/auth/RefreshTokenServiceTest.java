package ir.h0p3.securebankapi.auth;

import ir.h0p3.securebankapi.auth.security.JwtProperties;
import ir.h0p3.securebankapi.auth.security.JwtService;
import ir.h0p3.securebankapi.common.exception.UnauthorizedException;
import ir.h0p3.securebankapi.user.User;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenServiceTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef";
    private static final long ACCESS_EXPIRATION = 60_000;
    private static final long REFRESH_EXPIRATION = 120_000;

    @Test
    void generateTokenStoresRefreshTokenWithConfiguredExpiration() {
        TestContext context = createContext();

        String token = context.refreshTokenService.generateToken(
                context.user,
                context.session
        );

        RefreshToken savedToken =
                context.refreshTokenRepository.tokens.getFirst();
        assertThat(token).isNotBlank();
        assertThat(savedToken.getToken()).isEqualTo(token);
        assertThat(savedToken.getUser()).isSameAs(context.user);
        assertThat(savedToken.getSession()).isSameAs(context.session);
        assertThat(context.jwtService.validateRefreshToken(token).sessionId())
                .isEqualTo(context.session.getId());
        assertThat(savedToken.getRevoked()).isFalse();
        assertThat(savedToken.getCreatedAt())
                .isEqualTo(savedToken.getIssuedAt());
        assertThat(Duration.between(
                savedToken.getIssuedAt(),
                savedToken.getExpiresAt()
        )).isBetween(
                Duration.ofMillis(REFRESH_EXPIRATION - 1_000),
                Duration.ofMillis(REFRESH_EXPIRATION)
        );
        assertThat(savedToken.getReplacedBy()).isNull();
        assertThat(savedToken.getLastUsedAt()).isNull();
    }

    @Test
    void rotateTokenRevokesAndLinksPreviousToken() {
        TestContext context = createContext();
        String previousToken = context.refreshTokenService.generateToken(
                context.user,
                context.session
        );

        RefreshTokenRotation rotation =
                context.refreshTokenService.rotateToken(previousToken);

        RefreshToken previous =
                context.refreshTokenRepository.tokens.get(0);
        RefreshToken replacement =
                context.refreshTokenRepository.tokens.get(1);
        assertThat(rotation.refreshToken())
                .isEqualTo(replacement.getToken())
                .isNotEqualTo(previousToken);
        assertThat(rotation.email()).isEqualTo(context.user.getEmail());
        assertThat(rotation.sessionId())
                .isEqualTo(context.session.getId());
        assertThat(replacement.getSession())
                .isSameAs(context.session);
        assertThat(previous.getRevoked()).isTrue();
        assertThat(previous.getReplacedBy()).isSameAs(replacement);
        assertThat(previous.getLastUsedAt()).isNotNull();
        assertThat(replacement.getRevoked()).isFalse();
    }

    @Test
    void reusedTokenRevokesReplacementChainAndIsRejected() {
        TestContext context = createContext();
        String previousToken = context.refreshTokenService.generateToken(
                context.user,
                context.session
        );
        context.refreshTokenService.rotateToken(previousToken);

        assertThatThrownBy(
                () -> context.refreshTokenService.rotateToken(previousToken)
        )
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");

        assertThat(context.refreshTokenRepository.tokens)
                .allMatch(RefreshToken::getRevoked);
    }

    @Test
    void revokeSessionInvalidatesFutureRefreshAttempts() {
        TestContext context = createContext();
        String token = context.refreshTokenService.generateToken(
                context.user,
                context.session
        );

        context.refreshTokenService.revokeSession(token);

        RefreshToken revokedToken =
                context.refreshTokenRepository.tokens.getFirst();
        assertThat(revokedToken.getRevoked()).isTrue();
        assertThat(context.session.getRevoked()).isTrue();
        assertThatThrownBy(
                () -> context.refreshTokenService.rotateToken(token)
        )
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void refreshTokenSessionMismatchIsRejected() {
        TestContext context = createContext();
        String token = context.jwtService.generateRefreshToken(
                context.user.getEmail(),
                UUID.randomUUID()
        );
        saveToken(context, token);

        assertThatThrownBy(() -> context.refreshTokenService.rotateToken(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void refreshTokenOwnershipMismatchIsRejected() {
        TestContext context = createContext();
        String token = context.jwtService.generateRefreshToken(
                "other@example.com",
                context.session.getId()
        );
        saveToken(context, token);

        assertThatThrownBy(() -> context.refreshTokenService.rotateToken(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");
    }

    private void saveToken(TestContext context, String token) {
        LocalDateTime now = LocalDateTime.now();
        context.refreshTokenRepository.save(RefreshToken.builder()
                .token(token)
                .user(context.user)
                .session(context.session)
                .issuedAt(now)
                .expiresAt(now.plusMinutes(1))
                .revoked(false)
                .createdAt(now)
                .build());
    }

    private TestContext createContext() {
        JwtProperties properties = new JwtProperties(
                SECRET,
                ACCESS_EXPIRATION,
                REFRESH_EXPIRATION
        );
        JwtService jwtService = new JwtService(properties);
        InMemorySessionRepository sessionRepository =
                new InMemorySessionRepository();
        SessionService sessionService = new SessionService(
                sessionRepository.proxy(),
                properties
        );
        InMemoryRefreshTokenRepository refreshTokenRepository =
                new InMemoryRefreshTokenRepository();
        RefreshTokenService refreshTokenService = new RefreshTokenService(
                refreshTokenRepository.proxy(),
                properties,
                jwtService,
                sessionService
        );
        User user = createUser();
        Session session = sessionService.createSession(user);

        return new TestContext(
                refreshTokenService,
                refreshTokenRepository,
                jwtService,
                user,
                session
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
                case "revokeAllBySessionId" ->
                        revokeAllBySessionId((UUID) arguments[0]);
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

        private int revokeAllBySessionId(UUID sessionId) {
            int revokedCount = 0;

            for (RefreshToken token : tokens) {
                if (token.getSession().getId().equals(sessionId)) {
                    token.setRevoked(true);
                    revokedCount++;
                }
            }

            return revokedCount;
        }
    }

    private static final class InMemorySessionRepository
            implements InvocationHandler {

        private final List<Session> sessions = new ArrayList<>();

        private SessionRepository proxy() {
            return (SessionRepository) Proxy.newProxyInstance(
                    SessionRepository.class.getClassLoader(),
                    new Class<?>[]{SessionRepository.class},
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
                case "save" -> save((Session) arguments[0]);
                case "findByIdForUpdate" ->
                        findById((UUID) arguments[0]);
                default -> throw new UnsupportedOperationException(
                        method.getName()
                );
            };
        }

        private Session save(Session session) {
            if (session.getId() == null) {
                session.setId(UUID.randomUUID());
                sessions.add(session);
            }

            return session;
        }

        private Optional<Session> findById(UUID sessionId) {
            return sessions.stream()
                    .filter(session -> session.getId().equals(sessionId))
                    .findFirst();
        }
    }

    private record TestContext(
            RefreshTokenService refreshTokenService,
            InMemoryRefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            User user,
            Session session
    ) {
    }
}
