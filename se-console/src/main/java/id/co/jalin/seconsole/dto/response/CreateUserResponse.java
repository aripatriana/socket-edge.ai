package id.co.jalin.seconsole.dto.response;

/**
 * Response for {@code POST /api/users}.
 *
 * <p>This is the <em>only</em> place in the system where a user's
 * plaintext password appears in a response, and only at creation.
 * The server never stores plaintext — the hash is what goes to
 * {@code users.password_hash} — so this response is the admin's
 * single opportunity to capture the credential and deliver it to
 * the new user.
 *
 * <p>Controllers must not include the password in audit payloads
 * or log lines. The audit entry for {@code CREATE_USER} records
 * username + role only.
 */
public record CreateUserResponse(
        UserDto user,
        String temporaryPassword,
        String message
) {}
