package ir.h0p3.securebankapi.auth;

public record RefreshTokenRotation(
        String refreshToken,
        String email
) {
}
