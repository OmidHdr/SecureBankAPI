package ir.h0p3.securebankapi.auth.security;

public final class AuthenticationMessages {

    public static final String INVALID_CREDENTIALS = "Invalid email or password";
    public static final String INVALID_TOKEN = "Invalid or expired token";
    public static final String INVALID_REFRESH_TOKEN = "Invalid refresh token";
    public static final String INVALID_SESSION = "Invalid or expired session";

    private AuthenticationMessages() {
    }
}
