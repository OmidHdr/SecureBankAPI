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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@TestConstructor(
        autowireMode = TestConstructor.AutowireMode.ALL
)
@SpringBootTest(properties = {
        "jwt.secret=test-secret-key-test-secret-key-test-secret-key",
        "jwt.expiration=86400000",
        "jwt.refresh-expiration=604800000"
})
class SessionAuthenticationIntegrationTest {

    private static final String EMAIL = "session-test@example.com";
    private static final String PASSWORD = "SecurePassword123!";

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
    void loginCreatesSessionAndAccessTokenAuthenticates() throws Exception {
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

        accessAccounts(tokens.accessToken(), 200);
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
    }

    @Test
    void logoutRevokesSessionAndBothOldTokens() throws Exception {
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
    void logoutDoesNotRevokeAnotherDeviceSession() throws Exception {
        Tokens laptop = login();
        Tokens phone = login();

        logout(laptop.refreshToken());

        accessAccounts(laptop.accessToken(), 401);
        accessAccounts(phone.accessToken(), 200);
        Tokens refreshedPhone = refresh(phone.refreshToken(), 200);
        accessAccounts(refreshedPhone.accessToken(), 200);
    }

    private Tokens login() throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "%s",
                                          "password": "%s"
                                        }
                                        """.formatted(EMAIL, PASSWORD))
                )
                .andExpect(status().isOk())
                .andReturn();

        return readTokens(result);
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
        mockMvc.perform(
                        get("/api/accounts")
                                .header(
                                        "Authorization",
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().is(expectedStatus));
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
