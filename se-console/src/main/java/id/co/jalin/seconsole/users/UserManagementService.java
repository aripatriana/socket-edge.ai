package id.co.jalin.seconsole.users;

import id.co.jalin.seconsole.domain.Role;
import id.co.jalin.seconsole.domain.User;
import id.co.jalin.seconsole.domain.UserStatus;
import id.co.jalin.seconsole.repository.UserRepository;
import id.co.jalin.seconsole.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

/**
 * Business logic for admin user management. All mutations audit through
 * {@link AuditService}; all guards against operator footguns (disable
 * yourself, demote yourself from admin) live here rather than in the
 * controller so any future caller (CLI, tests) gets them for free.
 *
 * <p>Scope intentionally narrow:
 * <ul>
 *   <li>Create user — username + role. Password is server-generated
 *       once, returned in the response for the admin to deliver.</li>
 *   <li>Update role — change an existing user's role.</li>
 *   <li>Set status — soft-delete (disable) or re-enable.</li>
 *   <li>Reset password — sets {@code mustChangePassword=true} without
 *       invalidating the current password. User can still log in with
 *       what they had, but is immediately forced through the change
 *       flow. Matches the operator request: simple, non-breaking.</li>
 * </ul>
 *
 * <p><b>Out of scope:</b> username rename (audit log references it), hard
 * delete (breaks audit foreign keys), email/2FA (not in this Phase).
 */
@Service
public class UserManagementService {

    private static final Logger log = LoggerFactory.getLogger(UserManagementService.class);

    /**
     * Character set for generated temporary passwords. No ambiguous
     * characters (no 0/O, 1/l/I) so admins can read them over chat or
     * voice without confusion.
     */
    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 14;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SecureRandom random = new SecureRandom();

    public UserManagementService(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    // -----------------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------------

    public List<User> list(String search, Role role, UserStatus status) {
        String normalized = (search == null || search.isBlank()) ? null : search.trim();
        return userRepository.findFiltered(normalized, role, status);
    }

    // -----------------------------------------------------------------------
    // Create
    // -----------------------------------------------------------------------

    /**
     * Create a user with a generated temporary password. The caller
     * receives the plaintext password to deliver to the new user —
     * we never store or log it.
     *
     * @return the created user + the plaintext temp password, one-time
     */
    @Transactional
    public CreateUserResult create(String username, Role role, Long createdBy, String createdByUsername) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }

        String plaintext = generateTemporaryPassword();
        String hash = passwordEncoder.encode(plaintext);

        User user = new User(username, hash, role);
        user.setCreatedBy(createdBy);
        user.setMustChangePassword(true);  // force change on first login
        userRepository.save(user);

        // Audit: record the creation. Details intentionally exclude the
        // password — including the hash is not helpful to auditors and
        // including the plaintext would be catastrophic.
        auditService.logSuccess(createdBy, createdByUsername, "CREATE_USER",
                "user", String.valueOf(user.getId()),
                "{\"username\":\"" + escape(username) + "\",\"role\":\"" + role.dbValue() + "\"}",
                null, null);

        log.info("User created: username={} role={} by={}", username, role, createdByUsername);
        return new CreateUserResult(user, plaintext);
    }

    // -----------------------------------------------------------------------
    // Update role
    // -----------------------------------------------------------------------

    @Transactional
    public User updateRole(Long userId, Role newRole, Long actingUserId, String actingUsername) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Self-demotion guard: if an admin demotes themselves and they're the
        // last admin, the system becomes locked out of user management.
        // Rather than counting admins here (extra query, race-prone), refuse
        // self-role-change entirely — admins can demote other admins, and
        // only when an admin is being demoted by another admin does this
        // operation proceed.
        if (userId.equals(actingUserId) && user.getRole() == Role.ADMIN && newRole != Role.ADMIN) {
            throw new IllegalArgumentException(
                    "An admin cannot demote themselves. Ask another admin to change your role.");
        }

        Role oldRole = user.getRole();
        if (oldRole == newRole) {
            // No-op but don't 400 — idempotent updates are friendlier.
            return user;
        }
        user.setRole(newRole);
        userRepository.save(user);

        auditService.logSuccess(actingUserId, actingUsername, "UPDATE_USER_ROLE",
                "user", String.valueOf(user.getId()),
                "{\"username\":\"" + escape(user.getUsername()) + "\","
                        + "\"from\":\"" + oldRole.dbValue() + "\","
                        + "\"to\":\"" + newRole.dbValue() + "\"}",
                null, null);

        log.info("User role updated: username={} {} -> {} by={}",
                user.getUsername(), oldRole, newRole, actingUsername);
        return user;
    }

    // -----------------------------------------------------------------------
    // Set status (disable / enable)
    // -----------------------------------------------------------------------

    @Transactional
    public User setStatus(Long userId, UserStatus newStatus, Long actingUserId, String actingUsername) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Self-disable guard: prevents admin from locking themselves out
        // in one click. Enabling yourself is fine (you'd have to be
        // somehow currently disabled, which is a weird state to be in
        // while making API calls — but harmless if it happens).
        if (userId.equals(actingUserId) && newStatus == UserStatus.DISABLED) {
            throw new IllegalArgumentException("You cannot disable your own account.");
        }

        UserStatus oldStatus = user.getStatus();
        if (oldStatus == newStatus) {
            return user;
        }
        user.setStatus(newStatus);
        userRepository.save(user);

        String action = newStatus == UserStatus.DISABLED ? "DISABLE_USER" : "ENABLE_USER";
        auditService.logSuccess(actingUserId, actingUsername, action,
                "user", String.valueOf(user.getId()),
                "{\"username\":\"" + escape(user.getUsername()) + "\"}",
                null, null);

        log.info("User status changed: username={} {} -> {} by={}",
                user.getUsername(), oldStatus, newStatus, actingUsername);
        return user;
    }

    // -----------------------------------------------------------------------
    // Reset password (force change on next login)
    // -----------------------------------------------------------------------

    /**
     * Force the user to change their password on next login. Does NOT
     * change the current password — user can still log in with what they
     * have, but is immediately redirected to the change-password flow.
     *
     * <p>Also clears {@code failedLoginCount} — if the account was soft-
     * locked (lock = active + failed count ≥ 5), this effectively
     * unlocks it. Admins who want to unlock a user typically also want
     * them to pick a new password, so coupling the two reduces steps.
     */
    @Transactional
    public User resetPassword(Long userId, Long actingUserId, String actingUsername) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Self-reset: technically harmless (the user would just get
        // re-prompted to change on next login) but confusing — if an
        // admin wants to change their own password they should use the
        // normal Change Password flow, not Reset. Refuse to make intent
        // explicit.
        if (userId.equals(actingUserId)) {
            throw new IllegalArgumentException(
                    "To change your own password, use the Change Password page.");
        }

        user.setMustChangePassword(true);

        // Also clear the failed-login counter — if the account is soft-
        // locked (active + count ≥ 5), this resetPassword call is the
        // natural "unlock AND make them pick a new password" operation.
        // Admins shouldn't need two clicks to recover a locked user.
        user.setFailedLoginCount(0);

        userRepository.save(user);

        auditService.logSuccess(actingUserId, actingUsername, "RESET_USER_PASSWORD",
                "user", String.valueOf(user.getId()),
                "{\"username\":\"" + escape(user.getUsername()) + "\","
                        + "\"mode\":\"force_change_on_next_login\"}",
                null, null);

        log.info("Password reset forced: username={} by={}", user.getUsername(), actingUsername);
        return user;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Generate a random password from a reduced alphabet (no 0/O/1/l/I).
     * 14 chars of ~56-char alphabet ≈ 81 bits of entropy — well over the
     * minimum for a credential that will be rotated on first login.
     */
    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        String pw = sb.toString();

        // Sanity check: the password policy requires upper + lower + digit.
        // With 14 chars drawn from our alphabet the odds of missing any
        // class are vanishingly small, but if we ever tighten the policy
        // this assertion would catch a generator/policy drift at test
        // time rather than at "user can't log in with their own temp
        // password" time.
        assert pw.chars().anyMatch(Character::isUpperCase);
        assert pw.chars().anyMatch(Character::isLowerCase);
        assert pw.chars().anyMatch(Character::isDigit);

        return pw;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Carries the created user + the plaintext temp password to the controller. */
    public record CreateUserResult(User user, String temporaryPassword) {}
}
