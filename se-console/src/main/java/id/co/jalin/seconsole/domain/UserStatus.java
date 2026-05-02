package id.co.jalin.seconsole.domain;

public enum UserStatus {
    ACTIVE,
    DISABLED;

    public String dbValue() {
        return name().toLowerCase();
    }

    public static UserStatus fromDbValue(String s) {
        if (s == null) throw new IllegalArgumentException("status is null");
        return UserStatus.valueOf(s.toUpperCase());
    }
}
