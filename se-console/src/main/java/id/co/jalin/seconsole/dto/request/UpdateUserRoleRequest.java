package id.co.jalin.seconsole.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateUserRoleRequest(
        @NotBlank
        @Pattern(
                regexp = "^(admin|operator|viewer)$",
                message = "Role must be one of: admin, operator, viewer"
        )
        String role
) {}
