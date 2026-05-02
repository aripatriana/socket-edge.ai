package id.co.jalin.seconsole.users;

import id.co.jalin.seconsole.domain.Role;
import id.co.jalin.seconsole.domain.User;
import id.co.jalin.seconsole.domain.UserStatus;
import id.co.jalin.seconsole.dto.request.CreateUserRequest;
import id.co.jalin.seconsole.dto.request.UpdateUserRoleRequest;
import id.co.jalin.seconsole.dto.response.CreateUserResponse;
import id.co.jalin.seconsole.dto.response.UserManagementDto;
import id.co.jalin.seconsole.security.SecurityUser;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Admin-only user management REST API. All endpoints require
 * {@code ROLE_ADMIN}; the class-level {@code @PreAuthorize} ensures
 * anyone not holding that role gets 403 before any method body runs.
 *
 * <p>Endpoint conventions:
 * <ul>
 *   <li>{@code GET /api/users} — list with optional filters</li>
 *   <li>{@code POST /api/users} — create (returns plaintext temp password once)</li>
 *   <li>{@code PATCH /api/users/{id}/role} — change role</li>
 *   <li>{@code PATCH /api/users/{id}/status} — enable/disable (soft delete)</li>
 *   <li>{@code POST /api/users/{id}/reset-password} — force change on next login</li>
 *   <li>{@code DELETE /api/users/{id}} — alias for disable, for REST ergonomics</li>
 * </ul>
 *
 * <p>{@code DELETE} is mapped to soft-disable rather than row removal —
 * audit entries reference {@code user_id}, and cascading delete would
 * either lose audit integrity or fail on FK constraints. Disable is
 * the only deletion semantics this API supports.
 *
 * <p>{@code IllegalArgumentException} from the service layer (validation,
 * self-mutation guards, not-found) is caught here and mapped to 400.
 * Spring's default handler would map it to 500.
 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserManagementController {

    private static final Logger log = LoggerFactory.getLogger(UserManagementController.class);

    private final UserManagementService service;

    public UserManagementController(UserManagementService service) {
        this.service = service;
    }

    // -----------------------------------------------------------------------
    // GET /api/users
    // -----------------------------------------------------------------------

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status
    ) {
        Role roleFilter = null;
        UserStatus statusFilter = null;
        try {
            if (role != null && !role.isBlank()) roleFilter = Role.fromDbValue(role);
            if (status != null && !status.isBlank()) statusFilter = UserStatus.fromDbValue(status);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "bad_filter", "message", ex.getMessage()));
        }

        List<User> users = service.list(search, roleFilter, statusFilter);
        List<UserManagementDto> dtos = users.stream().map(UserManagementDto::from).toList();
        return ResponseEntity.ok(Map.of("users", dtos));
    }

    // -----------------------------------------------------------------------
    // POST /api/users
    // -----------------------------------------------------------------------

    @PostMapping
    public ResponseEntity<?> create(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal SecurityUser actor
    ) {
        try {
            UserManagementService.CreateUserResult result = service.create(
                    request.username(),
                    Role.fromDbValue(request.role()),
                    actor.getDomainUser().getId(),
                    actor.getDomainUser().getUsername()
            );
            CreateUserResponse response = new CreateUserResponse(
                    toLoginResponseDto(result.user()),
                    result.temporaryPassword(),
                    "User created. Share the temporary password securely — "
                            + "it will not be shown again. The user will be required "
                            + "to change it on first login."
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_request",
                    "message", ex.getMessage()
            ));
        }
    }

    // -----------------------------------------------------------------------
    // PATCH /api/users/{id}/role
    // -----------------------------------------------------------------------

    @PatchMapping("/{id}/role")
    public ResponseEntity<?> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRoleRequest request,
            @AuthenticationPrincipal SecurityUser actor
    ) {
        try {
            User updated = service.updateRole(
                    id,
                    Role.fromDbValue(request.role()),
                    actor.getDomainUser().getId(),
                    actor.getDomainUser().getUsername()
            );
            return ResponseEntity.ok(UserManagementDto.from(updated));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_request",
                    "message", ex.getMessage()
            ));
        }
    }

    // -----------------------------------------------------------------------
    // PATCH /api/users/{id}/status
    // -----------------------------------------------------------------------

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> setStatus(
            @PathVariable Long id,
            @RequestParam String value,
            @AuthenticationPrincipal SecurityUser actor
    ) {
        try {
            UserStatus newStatus = UserStatus.fromDbValue(value);
            User updated = service.setStatus(
                    id,
                    newStatus,
                    actor.getDomainUser().getId(),
                    actor.getDomainUser().getUsername()
            );
            return ResponseEntity.ok(UserManagementDto.from(updated));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_request",
                    "message", ex.getMessage()
            ));
        }
    }

    // -----------------------------------------------------------------------
    // DELETE /api/users/{id} — alias for status=disabled
    // -----------------------------------------------------------------------

    @DeleteMapping("/{id}")
    public ResponseEntity<?> softDelete(
            @PathVariable Long id,
            @AuthenticationPrincipal SecurityUser actor
    ) {
        try {
            User updated = service.setStatus(
                    id,
                    UserStatus.DISABLED,
                    actor.getDomainUser().getId(),
                    actor.getDomainUser().getUsername()
            );
            return ResponseEntity.ok(UserManagementDto.from(updated));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_request",
                    "message", ex.getMessage()
            ));
        }
    }

    // -----------------------------------------------------------------------
    // POST /api/users/{id}/reset-password
    // -----------------------------------------------------------------------

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<?> resetPassword(
            @PathVariable Long id,
            @AuthenticationPrincipal SecurityUser actor
    ) {
        try {
            User updated = service.resetPassword(
                    id,
                    actor.getDomainUser().getId(),
                    actor.getDomainUser().getUsername()
            );
            return ResponseEntity.ok(Map.of(
                    "user", UserManagementDto.from(updated),
                    "message", "User will be required to change their password on next login."
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_request",
                    "message", ex.getMessage()
            ));
        }
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    /**
     * Build the slim {@link id.co.jalin.seconsole.dto.response.UserDto} for
     * the create response. Reusing the existing login/me DTO is a deliberate
     * choice so the front-end can feed the created user's basic shape into
     * pre-existing types without depending on the richer admin DTO outside
     * the admin page.
     */
    private static id.co.jalin.seconsole.dto.response.UserDto toLoginResponseDto(User u) {
        return id.co.jalin.seconsole.dto.response.UserDto.from(u);
    }
}
