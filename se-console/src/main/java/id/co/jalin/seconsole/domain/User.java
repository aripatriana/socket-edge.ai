package id.co.jalin.seconsole.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Console user account. Schema: Foundation Guide section 7.2 (users).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    @Convert(converter = RoleConverter.class)
    @Column(nullable = false, length = 16)
    private Role role;

    @Convert(converter = UserStatusConverter.class)
    @Column(nullable = false, length = 16)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "last_login_ip", length = 64)
    private String lastLoginIp;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    protected User() {
        // JPA
    }

    public User(String username, String passwordHash, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = UserStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public void recordSuccessfulLogin(String sourceIp) {
        this.lastLoginAt = Instant.now();
        this.lastLoginIp = sourceIp;
        this.failedLoginCount = 0;
    }

    public void recordFailedLogin() {
        this.failedLoginCount++;
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    public void applyPasswordChange(String newHash) {
        this.passwordHash = newHash;
        this.mustChangePassword = false;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public String getLastLoginIp() { return lastLoginIp; }
    public int getFailedLoginCount() { return failedLoginCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }

    public void setMustChangePassword(boolean v) { this.mustChangePassword = v; }
    public void setStatus(UserStatus status) { this.status = status; }
    public void setRole(Role role) { this.role = role; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
