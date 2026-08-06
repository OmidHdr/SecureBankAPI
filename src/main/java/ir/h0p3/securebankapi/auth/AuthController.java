package ir.h0p3.securebankapi.auth;

import ir.h0p3.securebankapi.auth.dto.AuthResponse;
import ir.h0p3.securebankapi.auth.dto.LoginRequest;
import ir.h0p3.securebankapi.auth.dto.RefreshTokenRequest;
import ir.h0p3.securebankapi.auth.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import ir.h0p3.securebankapi.common.response.ApiError;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
        name = "Authentication",
        description = "User registration and authentication endpoints"
)
@SecurityRequirements
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Register a user",
            description = "Creates a user, starts an authenticated session, and returns a token pair."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Email already exists",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "IP rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @Operation(
            summary = "Log in",
            description = """
                    Creates an independent authenticated session and returns \
                    session-bound access and refresh tokens. The configured \
                    failed-attempt threshold (five by default) locks the account \
                    for the configured duration (15 minutes by default). A \
                    successful login resets the failed-attempt and lock state.
                    """
    )
    @ApiResponse(responseCode = "401", description = "Invalid credentials",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "200", description = "New device session created",
            content = @Content(schema = @Schema(implementation = AuthResponse.class),
                    examples = @ExampleObject(value = """
                            {"accessToken":"eyJ...example","refreshToken":"eyJ...example","tokenType":"Bearer"}
                            """)))
    @ApiResponse(responseCode = "400", description = "Request validation failed",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "423",
            description = "Account locked after the configured failed-attempt threshold",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "429", description = "IP rate limit exceeded",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(
            summary = "Refresh authentication tokens",
            description = """
                    Rotates the refresh token while retaining the same \
                    authenticated session. The previous refresh token is \
                    invalidated; attempting to reuse it is rejected and \
                    triggers the configured replay protection.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens rotated",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid, expired, revoked, or reused token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(responseCode = "429", description = "IP rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
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
                    sessions remain active. Access and refresh tokens from \
                    this session immediately become invalid.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Session revoked"),
            @ApiResponse(responseCode = "400", description = "Request validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid or revoked refresh token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(responseCode = "429", description = "IP rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        authService.logout(request);
    }
}
