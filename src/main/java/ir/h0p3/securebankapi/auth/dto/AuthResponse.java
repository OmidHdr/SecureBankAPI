package ir.h0p3.securebankapi.auth.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType
) {
}
