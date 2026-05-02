package id.co.jalin.seconsole.dto.response;

import id.co.jalin.seconsole.domain.User;

import java.time.Instant;

public record UserDto(
        Long id,
        String username,
        String role,
        String status,
        boolean mustChangePassword,
        Instant lastLoginAt
) {
    public static UserDto from(User u) {
        return new UserDto(
                u.getId(),
                u.getUsername(),
                u.getRole().dbValue(),
                u.getStatus().dbValue(),
                u.isMustChangePassword(),
                u.getLastLoginAt()
        );
    }
}
