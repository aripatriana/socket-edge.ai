package id.co.jalin.seconsole.service;

import id.co.jalin.seconsole.domain.Session;
import id.co.jalin.seconsole.domain.User;
import id.co.jalin.seconsole.dto.response.LoginResponse;
import id.co.jalin.seconsole.dto.response.UserDto;
import id.co.jalin.seconsole.repository.SessionRepository;
import id.co.jalin.seconsole.repository.UserRepository;
import id.co.jalin.seconsole.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * Auth orchestration. All persistent state transitions happen here.
 * Account lockout (Foundation 9.3): 5 consecutive failed logins → locked.
 * Password policy (Foundation 9.2): min 8 chars, 1 upper / 1 lower / 1 digit, ≠ username.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Pattern UPPER = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWER = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT = Pattern.compile(".*[0-9].*");

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       SessionRepository sessionRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.auditService = auditService;
    }

    // --- Login ---------------------------------------------------------------

    @Transactional
    public LoginResponse login(String username, String password, String sourceIp, String userAgent) {
        User user = userRepository.findByUsername(username).orElse(null);

        if (user == null) {
            auditService.logLoginFailure(username, "user_not_found", sourceIp, userAgent);
            throw new AuthException.BadCredentials();
        }

        if (!user.isActive()) {
            auditService.logLoginFailure(username, "account_disabled", sourceIp, userAgent);
            throw new AuthException.AccountDisabled();
        }

        if (user.getFailedLoginCount() >= MAX_FAILED_ATTEMPTS) {
            // Lockout policy. 15-minute cooldown logic is not yet implemented;
            // for now, admin must reset manually by clearing failed_login_count.
            auditService.logLoginFailure(username, "account_locked", sourceIp, userAgent);
            throw new AuthException.AccountLocked(user.getFailedLoginCount());
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.recordFailedLogin();
            userRepository.save(user);
            auditService.logLoginFailure(username, "bad_password", sourceIp, userAgent);
            throw new AuthException.BadCredentials();
        }

        // Success path.
        user.recordSuccessfulLogin(sourceIp);
        userRepository.save(user);

        String roleDb = user.getRole().dbValue();

        JwtTokenProvider.IssuedToken access = jwtTokenProvider.issueAccessToken(user.getUsername(), roleDb);
        JwtTokenProvider.IssuedToken refresh = jwtTokenProvider.issueRefreshToken(user.getUsername(), roleDb);

        // Persist access-token session for revocation. Refresh token session is
        // not persisted because /api/auth/refresh is not yet implemented.
        sessionRepository.save(new Session(
                access.jti(), user.getId(), access.issuedAt(), access.expiresAt(), sourceIp));

        auditService.logLoginSuccess(user.getId(), user.getUsername(), sourceIp, userAgent);
        log.info("Login success: user={} jti={}", user.getUsername(), access.jti());

        return new LoginResponse(
                access.token(),
                refresh.token(),
                UserDto.from(user),
                user.isMustChangePassword()
        );
    }

    // --- Change password -----------------------------------------------------

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword,
                               String sourceIp, String userAgent) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(AuthException.BadCredentials::new);

        if (!user.isActive()) {
            throw new AuthException.AccountDisabled();
        }

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            auditService.logFailure(user.getId(), user.getUsername(), "CHANGE_PASSWORD",
                    "user", String.valueOf(user.getId()),
                    "{\"reason\":\"bad_current_password\"}", sourceIp, userAgent);
            throw new AuthException.BadCredentials();
        }

        validatePasswordPolicy(newPassword, user.getUsername());

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new AuthException.SamePassword();
        }

        user.applyPasswordChange(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        auditService.logPasswordChange(user.getId(), user.getUsername(), sourceIp, userAgent);
        log.info("Password changed: user={}", user.getUsername());
    }

    // --- Logout --------------------------------------------------------------

    @Transactional
    public void logout(String jti, Long userId, String username, String sourceIp, String userAgent) {
        sessionRepository.findById(jti).ifPresent(s -> {
            s.revoke();
            sessionRepository.save(s);
        });
        auditService.logLogout(userId, username, jti, sourceIp, userAgent);
        log.info("Logout: user={} jti={}", username, jti);
    }

    // --- Password policy (Foundation 9.2) ------------------------------------

    private static void validatePasswordPolicy(String pw, String username) {
        if (pw == null || pw.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (!UPPER.matcher(pw).matches()) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter");
        }
        if (!LOWER.matcher(pw).matches()) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter");
        }
        if (!DIGIT.matcher(pw).matches()) {
            throw new IllegalArgumentException("Password must contain at least one digit");
        }
        if (pw.equalsIgnoreCase(username)) {
            throw new IllegalArgumentException("Password must not match username");
        }
    }
}
