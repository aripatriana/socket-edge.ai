package id.co.jalin.seconsole.domain;

/**
 * Console user role. Persisted as lowercase string in users.role.
 * Feature matrix defined in Foundation Guide section 6.2.
 */
public enum Role {
    ADMIN,
    OPERATOR,
    VIEWER;

    public String dbValue() {
        return name().toLowerCase();
    }

    public static Role fromDbValue(String s) {
        if (s == null) throw new IllegalArgumentException("role is null");
        return Role.valueOf(s.toUpperCase());
    }

    /** Spring Security authority — "ROLE_ADMIN", etc. */
    public String authority() {
        return "ROLE_" + name();
    }
}
