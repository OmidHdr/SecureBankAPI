package ir.h0p3.securebankapi.auth;

import ir.h0p3.securebankapi.auth.dto.AuthResponse;
import ir.h0p3.securebankapi.auth.dto.LoginRequest;
import ir.h0p3.securebankapi.auth.dto.RefreshTokenRequest;
import ir.h0p3.securebankapi.auth.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
        name = "Authentication",
        description = "User registration and authentication endpoints"
)
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Register a user",
            description = "Creates a user and an authenticated session."
    )
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @Operation(
            summary = "Log in",
            description = """
                    Creates an independent authenticated session and returns \
                    session-bound access and refresh tokens.
                    """
    )
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(
            summary = "Refresh authentication tokens",
            description = """
                    Rotates the refresh token while retaining the same \
                    authenticated session.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens rotated"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid, expired, revoked, or reused token"
            )
    })
    @PostMapping("/refresh")
    public AuthResponse refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        return authService.refresh(request);
    }

    @Operation(
            summary = "Log out the current session",
            description = """
                    Revokes the session represented by the refresh token and \
                    all refresh tokens belonging to that session. Other \
                    sessions remain active.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Session revoked"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid or revoked refresh token"
            )
    })
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        authService.logout(request);
    }
}
