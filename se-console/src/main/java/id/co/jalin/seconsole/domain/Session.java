package id.co.jalin.seconsole.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JWT session record for revocation. Schema: Foundation Guide section 7.2 (sessions).
 * Primary key is the JWT jti (UUID string).
 */
@Entity
@Table(name = "sessions")
public class Session {

    @Id
    @Column(length = 64)
    private String id;  // jti

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "source_ip", length = 64)
    private String sourceIp;

    protected Session() {
        // JPA
    }

    public Session(String jti, Long userId, Instant issuedAt, Instant expiresAt, String sourceIp) {
        this.id = jti;
        this.userId = userId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.sourceIp = sourceIp;
    }

    public void revoke() { this.revoked = true; }

    public boolean isValidAt(Instant now) {
        return !revoked && now.isBefore(expiresAt);
    }

    public String getId() { return id; }
    public Long getUserId() { return userId; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public String getSourceIp() { return sourceIp; }
}
