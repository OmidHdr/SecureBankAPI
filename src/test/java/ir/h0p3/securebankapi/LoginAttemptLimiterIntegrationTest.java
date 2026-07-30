package ir.h0p3.securebankapi;

import ir.h0p3.securebankapi.auth.RefreshTokenRepository;
import ir.h0p3.securebankapi.auth.SessionRepository;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
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
        "jwt.refresh-expiration=604800000"
})
class LoginAttemptLimiterIntegrationTest {

    private static final String EMAIL = "login-limit@example.com";
    private static final String PASSWORD = "SecurePassword123!";
    private static final String WRONG_PASSWORD = "WrongPassword123!";

    private final MockMvc mockMvc;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    LoginAttemptLimiterIntegrationTest(
            MockMvc mockMvc,
            PasswordEncoder passwordEncoder,
            UserRepository userRepository,
            SessionRepository sessionRepository,
            RefreshTokenRepository refreshTokenRepository
    ) {
        this.mockMvc = mockMvc;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.saveAndFlush(User.builder()
                .fullName("Login Limit User")
                .email(EMAIL)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(UserRole.CUSTOMER)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Test
    void fifthWrongPasswordPersistsLockAndReturnsLocked() throws Exception {
        for (int attempt = 1; attempt < 5; attempt++) {
            login(WRONG_PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
        }

        login(WRONG_PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.status").value(423))
                .andExpect(jsonPath("$.error").value("Locked"))
                .andExpect(jsonPath("$.message").value(
                        "Account is temporarily locked. Try again in 15 minutes"
                ))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));

        User lockedUser = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(lockedUser.getFailedAttempts()).isEqualTo(5);
        assertThat(lockedUser.isAccountLocked()).isTrue();
        assertThat(lockedUser.getLockTime()).isNotNull();

        login(PASSWORD).andExpect(status().isLocked());
        assertThat(sessionRepository.count()).isZero();
    }

    @Test
    void successfulLoginResetsPersistedFailures() throws Exception {
        login(WRONG_PASSWORD).andExpect(status().isUnauthorized());
        login(WRONG_PASSWORD).andExpect(status().isUnauthorized());

        login(PASSWORD).andExpect(status().isOk());

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.isAccountLocked()).isFalse();
        assertThat(user.getLockTime()).isNull();
        assertThat(sessionRepository.count()).isEqualTo(1);
    }

    @Test
    void expiredLockAllowsLoginAndClearsPersistedLock() throws Exception {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        user.setFailedAttempts(5);
        user.setAccountLocked(true);
        user.setLockTime(LocalDateTime.now().minusMinutes(16));
        userRepository.saveAndFlush(user);

        login(PASSWORD).andExpect(status().isOk());

        User unlockedUser = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(unlockedUser.getFailedAttempts()).isZero();
        assertThat(unlockedUser.isAccountLocked()).isFalse();
        assertThat(unlockedUser.getLockTime()).isNull();
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String password
    ) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """.formatted(EMAIL, password)));
    }
}
