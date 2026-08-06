package ir.h0p3.securebankapi;

import com.jayway.jsonpath.JsonPath;
import ir.h0p3.securebankapi.auth.RefreshToken;
import ir.h0p3.securebankapi.auth.RefreshTokenRepository;
import ir.h0p3.securebankapi.auth.Session;
import ir.h0p3.securebankapi.auth.SessionRepository;
import ir.h0p3.securebankapi.auth.security.JwtService;
import ir.h0p3.securebankapi.user.User;
import ir.h0p3.securebankapi.user.UserRepository;
import ir.h0p3.securebankapi.user.UserRole;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@TestConstructor(
        autowireMode = TestConstructor.AutowireMode.ALL
)
@SpringBootTest(properties = {
        "jwt.secret=test-secret-key-test-secret-key-test-secret-key",
        "jwt.expiration=86400000",
        "jwt.refresh-expiration=604800000",
        "rate-limit.login.requests=1000",
        "rate-limit.register.requests=1000",
        "rate-limit.refresh.requests=1000",
        "rate-limit.logout.requests=1000"
})
class SessionAuthenticationIntegrationTest {

    private static final String EMAIL = "session-test@example.com";
    private static final String PASSWORD = "SecurePassword123!";
    private static final String JWT_SECRET =
            "test-secret-key-test-secret-key-test-secret-key";

    private final MockMvc mockMvc;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    SessionAuthenticationIntegrationTest(
            MockMvc mockMvc,
            PasswordEncoder passwordEncoder,
            UserRepository userRepository,
            SessionRepository sessionRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService
    ) {
        this.mockMvc = mockMvc;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
    }

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.saveAndFlush(
                User.builder()
                        .fullName("Session Test User")
                        .email(EMAIL)
                        .passwordHash(
                                passwordEncoder.encode(PASSWORD)
                        )
                        .role(UserRole.CUSTOMER)
                        .enabled(true)
                        .createdAt(LocalDateTime.now())
                        .build()
        );
    }

    @Test
    @Transactional
    void shouldCreateSessionWhenLoginSucceeds() throws Exception {
        Tokens tokens = login();
        var identity = jwtService.validateAccessToken(
                tokens.accessToken()
        );

        assertThat(sessionRepository.findById(identity.sessionId()))
                .isPresent()
                .get()
                .extracting(Session::getRevoked)
                .isEqualTo(false);
        assertThat(jwtService.validateRefreshToken(
                tokens.refreshToken()
        ).sessionId()).isEqualTo(identity.sessionId());
        RefreshToken savedRefreshToken = refreshTokenRepository
                .findByToken(tokens.refreshToken()).orElseThrow();
        assertThat(savedRefreshToken.getSession().getId())
                .isEqualTo(identity.sessionId());
        assertThat(savedRefreshToken.getUser().getEmail()).isEqualTo(EMAIL);

        accessAccounts(tokens.accessToken(), 200);
    }

    @Test
    void registrationSucceedsAndPersistsSessionAndRefreshToken() throws Exception {
        MvcResult result = register(
                "new-user@example.com",
                PASSWORD,
                201
        );
        Tokens tokens = readTokens(result);
        UUID sessionId = jwtService.validateAccessToken(
                tokens.accessToken()
        ).sessionId();

        assertThat(userRepository.findByEmail("new-user@example.com")).isPresent();
        assertThat(sessionRepository.findById(sessionId)).isPresent();
        assertThat(refreshTokenRepository.findByToken(tokens.refreshToken()))
                .isPresent()
                .get()
                .extracting(token -> token.getSession().getId())
                .isEqualTo(sessionId);
    }

    @Test
    void registrationRejectsDuplicateAndInvalidRequests() throws Exception {
        register(EMAIL, PASSWORD, 409);
        register("not-an-email", PASSWORD, 400);
        register("blank-password@example.com", "", 400);
        register("weak-password@example.com", "short", 400);
    }

    @Test
    void loginRejectsUnknownUserWrongPasswordAndInvalidBody() throws Exception {
        login("unknown@example.com", PASSWORD, 401);
        login(EMAIL, "WrongPassword123!", 401);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    @Test
    void protectedEndpointRejectsMissingMalformedExpiredAndRevokedSessionTokens()
            throws Exception {
        accessAccounts(null, 401);
        accessAccounts("malformed-token", 401);

        Tokens tokens = login();
        UUID sessionId = jwtService.validateAccessToken(tokens.accessToken()).sessionId();
        accessAccounts(expiredToken(EMAIL, sessionId, "access"), 401);

        Session session = sessionRepository.findById(sessionId).orElseThrow();
        session.setRevoked(true);
        sessionRepository.saveAndFlush(session);
        accessAccounts(tokens.accessToken(), 401);
    }

    @Test
    void refreshKeepsSessionAndRotatesRefreshToken() throws Exception {
        Tokens original = login();
        Tokens refreshed = refresh(original.refreshToken(), 200);

        assertThat(refreshed.refreshToken())
                .isNotEqualTo(original.refreshToken());
        assertThat(jwtService.validateAccessToken(
                refreshed.accessToken()
        ).sessionId()).isEqualTo(
                jwtService.validateAccessToken(
                        original.accessToken()
                ).sessionId()
        );
        accessAccounts(refreshed.accessToken(), 200);
        assertThat(refreshTokenRepository.findByToken(original.refreshToken()))
                .isPresent()
                .get()
                .extracting(RefreshToken::getRevoked)
                .isEqualTo(true);
        refresh(original.refreshToken(), 401);
    }

    @Test
    void refreshRejectsMalformedExpiredAndRevokedSessionTokens() throws Exception {
        refresh("not-a-jwt", 401);

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        Session session = sessionRepository.saveAndFlush(Session.builder()
                .user(user)
                .revoked(false)
                .createdAt(LocalDateTime.now().minusDays(2))
                .lastActivityAt(LocalDateTime.now().minusDays(2))
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build());
        String expired = expiredToken(EMAIL, session.getId(), "refresh");
        refreshTokenRepository.saveAndFlush(RefreshToken.builder()
                .token(expired)
                .user(user)
                .session(session)
                .issuedAt(LocalDateTime.now().minusDays(2))
                .expiresAt(LocalDateTime.now().minusDays(1))
                .revoked(false)
                .createdAt(LocalDateTime.now().minusDays(2))
                .build());
        refresh(expired, 401);

        Tokens tokens = login();
        UUID sessionId = jwtService.validateRefreshToken(tokens.refreshToken()).sessionId();
        Session revoked = sessionRepository.findById(sessionId).orElseThrow();
        revoked.setRevoked(true);
        sessionRepository.saveAndFlush(revoked);
        refresh(tokens.refreshToken(), 401);
    }

    @Test
    void shouldRejectAccessTokenAfterLogout() throws Exception {
        Tokens tokens = login();
        var sessionId = jwtService.validateAccessToken(
                tokens.accessToken()
        ).sessionId();

        logout(tokens.refreshToken());

        assertThat(sessionRepository.findById(sessionId))
                .isPresent()
                .get()
                .extracting(Session::getRevoked)
                .isEqualTo(true);
        assertThat(refreshTokenRepository.findByToken(
                tokens.refreshToken()
        ))
                .isPresent()
                .get()
                .extracting(RefreshToken::getRevoked)
                .isEqualTo(true);
        accessAccounts(tokens.accessToken(), 401);
        refresh(tokens.refreshToken(), 401);
    }

    @Test
    void shouldKeepSecondDeviceAuthenticatedAfterFirstDeviceLogout() throws Exception {
        Tokens laptop = login();
        Tokens phone = login();

        logout(laptop.refreshToken());

        assertThat(jwtService.validateAccessToken(laptop.accessToken()).sessionId())
                .isNotEqualTo(jwtService.validateAccessToken(phone.accessToken()).sessionId());
        accessAccounts(laptop.accessToken(), 401);
        refresh(laptop.refreshToken(), 401);
        accessAccounts(phone.accessToken(), 200);
        Tokens refreshedPhone = refresh(phone.refreshToken(), 200);
        accessAccounts(refreshedPhone.accessToken(), 200);
    }

    @Test
    void shouldRejectReusedRefreshTokenAndRevokeTokenChain() throws Exception {
        Tokens original = login();
        Tokens rotated = refresh(original.refreshToken(), 200);

        refresh(original.refreshToken(), 401);

        assertThat(refreshTokenRepository.findByToken(original.refreshToken()))
                .isPresent().get().extracting(RefreshToken::getRevoked).isEqualTo(true);
        assertThat(refreshTokenRepository.findByToken(rotated.refreshToken()))
                .isPresent().get().extracting(RefreshToken::getRevoked).isEqualTo(true);
        refresh(rotated.refreshToken(), 401);
        accessAccounts(rotated.accessToken(), 200);
    }

    private Tokens login() throws Exception {
        return readTokens(login(EMAIL, PASSWORD, 200));
    }

    private MvcResult login(String email, String password, int expectedStatus)
            throws Exception {
        return mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "%s",
                                          "password": "%s"
                                        }
                                        """.formatted(email, password))
                )
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private MvcResult register(String email, String password, int expectedStatus)
            throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Integration User",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private Tokens refresh(
            String refreshToken,
            int expectedStatus
    ) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(refreshTokenRequest(refreshToken))
                )
                .andExpect(status().is(expectedStatus))
                .andReturn();

        return expectedStatus == 200 ? readTokens(result) : null;
    }

    private void logout(String refreshToken) throws Exception {
        mockMvc.perform(
                        post("/api/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(refreshTokenRequest(refreshToken))
                )
                .andExpect(status().isNoContent());
    }

    private void accessAccounts(
            String accessToken,
            int expectedStatus
    ) throws Exception {
        var request = get("/api/accounts");
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        var result = mockMvc.perform(request)
                .andExpect(status().is(expectedStatus));
        if (expectedStatus == 401) {
            result.andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").value("Invalid or expired token"))
                    .andExpect(jsonPath("$.path").value("/api/accounts"));
        }
    }

    private String expiredToken(String email, UUID sessionId, String tokenType) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(email)
                .claim("sid", sessionId.toString())
                .claim("token_type", tokenType)
                .issuedAt(new Date(now - 120_000))
                .expiration(new Date(now - 60_000))
                .signWith(
                        Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256
                )
                .compact();
    }

    private String refreshTokenRequest(String refreshToken) {
        return """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken);
    }

    private Tokens readTokens(MvcResult result) throws Exception {
        String response = result.getResponse().getContentAsString();

        return new Tokens(
                JsonPath.read(response, "$.accessToken"),
                JsonPath.read(response, "$.refreshToken")
        );
    }

    private record Tokens(
            String accessToken,
            String refreshToken
    ) {
    }
}
