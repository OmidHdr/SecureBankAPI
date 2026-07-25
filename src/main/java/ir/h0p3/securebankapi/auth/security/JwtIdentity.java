package ir.h0p3.securebankapi.auth.security;

import java.util.UUID;

public record JwtIdentity(
        String email,
        UUID sessionId
) {
}
