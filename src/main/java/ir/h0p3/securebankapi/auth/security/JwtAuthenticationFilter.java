package ir.h0p3.securebankapi.auth.security;

import ir.h0p3.securebankapi.auth.SessionService;
import ir.h0p3.securebankapi.common.exception.UnauthorizedException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final SessionService sessionService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            JwtIdentity identity = jwtService.validateAccessToken(token);

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                sessionService.validateAndTouch(
                        identity.sessionId(),
                        identity.email()
                );
                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(
                                identity.email()
                        );
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (
                JwtException |
                IllegalArgumentException |
                UnauthorizedException exception
        ) {
            SecurityContextHolder.clearContext();
            log.warn(
                    "JWT authentication failed: method={}, path={}, reason={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    exception.getClass().getSimpleName(),
                    exception
            );
            authenticationEntryPoint.writeUnauthorizedResponse(response);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
