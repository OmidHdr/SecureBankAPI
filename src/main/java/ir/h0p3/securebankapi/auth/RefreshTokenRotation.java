package ir.h0p3.securebankapi.auth;

import java.util.UUID;

public record RefreshTokenRotation(
        String refreshToken,
        String email,
        UUID sessionId
) {
}
