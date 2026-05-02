package id.co.jalin.seconsole.dto.response;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        UserDto user,
        boolean mustChangePassword
) {}
