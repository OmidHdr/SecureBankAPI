package ir.h0p3.securebankapi.auth;

import ir.h0p3.securebankapi.auth.dto.AuthResponse;
import ir.h0p3.securebankapi.auth.dto.LoginRequest;
import ir.h0p3.securebankapi.auth.dto.RegisterRequest;
import ir.h0p3.securebankapi.auth.security.JwtService;
import ir.h0p3.securebankapi.common.exception.ConflictException;
import ir.h0p3.securebankapi.user.User;
import ir.h0p3.securebankapi.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private RefreshTokenService refreshTokenService;
    private SessionService sessionService;
    private LoginAttemptService loginAttemptService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        sessionService = mock(SessionService.class);
        loginAttemptService = mock(LoginAttemptService.class);
        service = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                refreshTokenService,
                sessionService,
                loginAttemptService
        );
    }

    @Test
    void duplicateRegistrationIsRejectedBeforeEncodingPassword() {
        RegisterRequest request = new RegisterRequest(
                "Existing User", "user@example.com", "Password123!"
        );
        when(userRepository.findByEmail(request.email()))
                .thenReturn(Optional.of(User.builder().build()));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email already exists");
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void unknownUserAndWrongPasswordUseSameCredentialError() {
        LoginRequest request = new LoginRequest(
                "user@example.com", "Password123!"
        );
        when(userRepository.findByEmailForUpdate(request.email()))
                .thenReturn(Optional.empty());
        assertInvalidCredentials(request);

        User user = User.builder()
                .email(request.email())
                .passwordHash("encoded")
                .build();
        when(userRepository.findByEmailForUpdate(request.email()))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), "encoded"))
                .thenReturn(false);
        assertInvalidCredentials(request);
        verify(loginAttemptService).ensureLoginAllowed(user);
        verify(loginAttemptService).recordFailedAttempt(user);
    }

    @Test
    void successfulLoginCreatesSessionAndReturnsSessionBoundTokens() {
        LoginRequest request = new LoginRequest(
                "user@example.com", "Password123!"
        );
        User user = User.builder()
                .id(1L)
                .email(request.email())
                .passwordHash("encoded")
                .build();
        UUID sessionId = UUID.randomUUID();
        Session session = Session.builder().id(sessionId).user(user).build();
        when(userRepository.findByEmailForUpdate(request.email()))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), "encoded"))
                .thenReturn(true);
        when(sessionService.createSession(user)).thenReturn(session);
        when(jwtService.generateAccessToken(request.email(), sessionId))
                .thenReturn("access-token");
        when(refreshTokenService.generateToken(user, session))
                .thenReturn("refresh-token");

        AuthResponse response = service.login(request);

        assertThat(response).isEqualTo(new AuthResponse(
                "access-token", "refresh-token", "Bearer"
        ));
        verify(sessionService).createSession(user);
        verify(refreshTokenService).generateToken(user, session);
        verify(loginAttemptService).ensureLoginAllowed(user);
        verify(loginAttemptService).recordSuccessfulLogin(user);
    }

    private void assertInvalidCredentials(LoginRequest request) {
        assertThatThrownBy(() -> service.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }
}
