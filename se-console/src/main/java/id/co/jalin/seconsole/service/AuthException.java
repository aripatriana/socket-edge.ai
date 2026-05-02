package id.co.jalin.seconsole.service;

/**
 * Auth-related failures. All map to HTTP 401 or 403 at the controller layer.
 * Sealed so the exception handler can exhaustively switch on subclasses.
 */
public sealed class AuthException extends RuntimeException
        permits AuthException.BadCredentials,
                AuthException.AccountDisabled,
                AuthException.AccountLocked,
                AuthException.SamePassword {

    private final String code;

    protected AuthException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static final class BadCredentials extends AuthException {
        public BadCredentials() {
            super("BAD_CREDENTIALS", "Invalid username or password");
        }
    }

    public static final class AccountDisabled extends AuthException {
        public AccountDisabled() {
            super("ACCOUNT_DISABLED", "Account is disabled");
        }
    }

    public static final class AccountLocked extends AuthException {
        public AccountLocked(int failedCount) {
            super("ACCOUNT_LOCKED",
                    "Account locked after " + failedCount + " failed attempts");
        }
    }

    public static final class SamePassword extends AuthException {
        public SamePassword() {
            super("SAME_PASSWORD", "New password must differ from current password");
        }
    }
}
