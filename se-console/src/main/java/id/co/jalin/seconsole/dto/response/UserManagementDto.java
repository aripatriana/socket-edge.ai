package id.co.jalin.seconsole.dto.response;

import id.co.jalin.seconsole.domain.User;

import java.time.Instant;

/**
 * Richer user view for the admin Users page. Separate from {@link UserDto}
 * so the login/me endpoints (which return {@link UserDto}) aren't forced
 * to leak admin-only fields like {@code failedLoginCount} to end users.
 *
 * <p>{@code locked} is a derived flag — there is no LOCKED status in the
 * DB, only {@code ACTIVE} or {@code DISABLED}. The UI treats "active with
 * failed count at or above the threshold" as a distinct visual state
 * ("LOCKED") without persisting a new status column. This matches the
 * Foundation Guide's "soft lock via failed count" model.
 */
public record UserManagementDto(
        Long id,
        String username,
        String role,
        String status,               // "active" | "disabled"
        boolean locked,              // failedLoginCount >= threshold (and status=active)
        int failedLoginCount,
        boolean mustChangePassword,
        Instant lastLoginAt,
        String lastLoginIp,
        Instant createdAt,
        Long createdBy
) {
    private static final int LOCK_THRESHOLD = 5;

    public static UserManagementDto from(User u) {
        boolean locked = u.isActive() && u.getFailedLoginCount() >= LOCK_THRESHOLD;
        return new UserManagementDto(
                u.getId(),
                u.getUsername(),
                u.getRole().dbValue(),
                u.getStatus().dbValue(),
                locked,
                u.getFailedLoginCount(),
                u.isMustChangePassword(),
                u.getLastLoginAt(),
                u.getLastLoginIp(),
                u.getCreatedAt(),
                u.getCreatedBy()
        );
    }
}
