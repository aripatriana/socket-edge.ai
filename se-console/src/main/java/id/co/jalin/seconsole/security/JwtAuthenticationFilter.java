package id.co.jalin.seconsole.security;

import id.co.jalin.seconsole.domain.Session;
import id.co.jalin.seconsole.repository.SessionRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

/**
 * Extracts a Bearer JWT, verifies it, checks the session table for revocation,
 * and populates SecurityContextHolder. Any failure leaves the context empty
 * (anonymous); SecurityConfig's authorization rules handle 401/403 downstream.
 *
 * Only ACCESS tokens are accepted here. Refresh tokens would be consumed by a
 * dedicated /api/auth/refresh endpoint (not yet built).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final SessionRepository sessionRepository;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   UserDetailsService userDetailsService,
                                   SessionRepository sessionRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.sessionRepository = sessionRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        String token = extractBearer(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtTokenProvider.ParsedToken parsed = jwtTokenProvider.parseAndVerify(token);

                if (!parsed.isAccessToken()) {
                    log.debug("Non-access token on protected endpoint: type={}", parsed.type());
                } else {
                    Optional<Session> sessionOpt = sessionRepository.findById(parsed.jti());
                    if (sessionOpt.isEmpty()) {
                        log.debug("JWT jti {} not in sessions table (logged out or never issued)", parsed.jti());
                    } else if (!sessionOpt.get().isValidAt(Instant.now())) {
                        log.debug("Session {} is revoked or expired", parsed.jti());
                    } else {
                        UserDetails userDetails = loadUserOrNull(parsed.username());
                        if (userDetails != null && userDetails.isEnabled()) {
                            UsernamePasswordAuthenticationToken auth =
                                    new UsernamePasswordAuthenticationToken(
                                            userDetails, null, userDetails.getAuthorities());
                            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        }
                    }
                }
            } catch (JwtException e) {
                // Invalid signature, expired, malformed, etc. — leave context anonymous.
                log.debug("JWT verification failed: {}", e.getMessage());
            } catch (Exception e) {
                // Any other unexpected failure should not break the filter chain.
                log.warn("Unexpected error in JWT filter", e);
            }
        }

        chain.doFilter(request, response);
    }

    private UserDetails loadUserOrNull(String username) {
        try {
            return userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            return null;
        }
    }

    private static String extractBearer(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String value = header.substring(BEARER_PREFIX.length()).trim();
        return value.isEmpty() ? null : value;
    }
}
