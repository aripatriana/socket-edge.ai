package id.co.jalin.seconsole.controller;

import id.co.jalin.seconsole.dto.request.LoginRequest;
import id.co.jalin.seconsole.dto.response.LoginResponse;
import id.co.jalin.seconsole.dto.response.UserDto;
import id.co.jalin.seconsole.security.JwtTokenProvider;
import id.co.jalin.seconsole.security.SecurityUser;
import id.co.jalin.seconsole.service.AuthService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(AuthService authService, JwtTokenProvider jwtTokenProvider) {
        this.authService = authService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest http) {
        LoginResponse response = authService.login(
                request.username(),
                request.password(),
                clientIp(http),
                userAgent(http)
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Change password. Permitted for unauthenticated callers too — a user with
     * must_change_password=true gets rejected by /api/auth/me until they've
     * changed the password, but they need to be able to call this endpoint to
     * do so. Current password is verified server-side.
     *
     * Typical flow: login → server returns mustChangePassword=true →
     * client shows change-password screen → client calls this endpoint with
     * username + current + new password → on success, client either keeps the
     * existing JWT or redirects to re-login.
     */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequestWithUsername request,
                                               HttpServletRequest http,
                                               Authentication auth) {
        // Resolve username: prefer the authenticated principal, fall back to
        // the request body (covers the must-change-password flow where the
        // caller may still be authenticated from login but could also be a
        // fresh caller).
        String username = auth != null && auth.getPrincipal() instanceof SecurityUser su
                ? su.getUsername()
                : request.username();

        authService.changePassword(username, request.currentPassword(), request.newPassword(),
                clientIp(http), userAgent(http));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal SecurityUser principal,
                                       HttpServletRequest http) {
        String bearer = extractBearer(http);
        if (bearer != null && principal != null) {
            try {
                JwtTokenProvider.ParsedToken parsed = jwtTokenProvider.parseAndVerify(bearer);
                authService.logout(
                        parsed.jti(),
                        principal.getUserId(),
                        principal.getUsername(),
                        clientIp(http),
                        userAgent(http)
                );
            } catch (JwtException ignored) {
                // If the token can't even be parsed, there's nothing to revoke.
            }
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal SecurityUser principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(UserDto.from(principal.getDomainUser()));
    }

    // --- helpers -------------------------------------------------------------

    private static String extractBearer(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) return null;
        String value = header.substring(BEARER_PREFIX.length()).trim();
        return value.isEmpty() ? null : value;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // First entry is the original client.
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }

    private static String userAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) return null;
        // Cap to match audit_entries.user_agent VARCHAR(512).
        return ua.length() > 512 ? ua.substring(0, 512) : ua;
    }

    /**
     * Change-password request when caller may not have an authentication context
     * yet (first-login flow). Username is optional — if authenticated, it's
     * derived from the principal.
     */
    public record ChangePasswordRequestWithUsername(
            String username,
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 8, max = 128) String newPassword
    ) {}
}
