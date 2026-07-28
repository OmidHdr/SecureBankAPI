package ir.h0p3.securebankapi.auth;

import ir.h0p3.securebankapi.auth.dto.AuthResponse;
import ir.h0p3.securebankapi.auth.dto.LoginRequest;
import ir.h0p3.securebankapi.auth.dto.RefreshTokenRequest;
import ir.h0p3.securebankapi.auth.dto.RegisterRequest;
import ir.h0p3.securebankapi.auth.security.JwtService;
import ir.h0p3.securebankapi.auth.security.AuthenticationMessages;
import ir.h0p3.securebankapi.common.exception.ConflictException;
import ir.h0p3.securebankapi.user.User;
import ir.h0p3.securebankapi.user.UserRepository;
import ir.h0p3.securebankapi.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final SessionService sessionService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("User registration requested for email={}", request.email());

        if (userRepository.findByEmail(request.email()).isPresent()) {
            log.warn("Registration failed because email already exists: email={}",
                    request.email());

            throw new ConflictException("Email already exists");
        }

        User user = User.builder()
                .fullName(request.fullName())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.CUSTOMER)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(user);

        log.info(
                "User registered successfully: userId={}, email={}",
                savedUser.getId(),
                savedUser.getEmail()
        );

        return generateAuthResponse(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Login requested for email={}", request.email());

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    log.warn("Login failed for unknown email={}", request.email());

                    return new BadCredentialsException(
                            AuthenticationMessages.INVALID_CREDENTIALS
                    );
                });

        if (!passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        )) {
            log.warn("Login failed because password was invalid: email={}",
                    request.email());

            throw new BadCredentialsException(
                    AuthenticationMessages.INVALID_CREDENTIALS
            );
        }

        log.info(
                "User logged in successfully: userId={}, email={}",
                user.getId(),
                user.getEmail()
        );

        return generateAuthResponse(user);
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshTokenRotation rotation = refreshTokenService.rotateToken(
                request.refreshToken()
        );

        return new AuthResponse(
                jwtService.generateAccessToken(
                        rotation.email(),
                        rotation.sessionId()
                ),
                rotation.refreshToken(),
                TOKEN_TYPE
        );
    }

    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revokeSession(request.refreshToken());
    }

    private AuthResponse generateAuthResponse(User user) {
        Session session = sessionService.createSession(user);
        String accessToken = jwtService.generateAccessToken(
                user.getEmail(),
                session.getId()
        );
        String refreshToken = refreshTokenService.generateToken(
                user,
                session
        );

        return new AuthResponse(
                accessToken,
                refreshToken,
                TOKEN_TYPE
        );
    }
}
