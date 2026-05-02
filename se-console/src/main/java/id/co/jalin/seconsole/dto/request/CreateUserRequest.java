package id.co.jalin.seconsole.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/users}.
 *
 * <p>No password field — the server generates a temporary one and
 * returns it in the response exactly once. This avoids admins picking
 * weak passwords or reusing one across accounts, and sidesteps any
 * concern about plaintext passwords travelling in audit bodies.
 *
 * <p>Username pattern is intentionally conservative (alphanum + a few
 * safe separators) — matches what will read cleanly in audit log rows
 * and avoids injection concerns in any path that later interpolates
 * the username into messages.
 */
public record CreateUserRequest(
        @NotBlank
        @Size(min = 3, max = 64)
        @Pattern(
                regexp = "^[a-zA-Z0-9._-]+$",
                message = "Username may only contain letters, digits, dot, underscore, and hyphen"
        )
        String username,

        @NotBlank
        @Pattern(
                regexp = "^(admin|operator|viewer)$",
                message = "Role must be one of: admin, operator, viewer"
        )
        String role
) {}
