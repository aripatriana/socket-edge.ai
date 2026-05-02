package id.co.jalin.seconsole.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Append-only audit log entry. Schema: Foundation Guide section 7.2 (audit_entries).
 */
@Entity
@Table(name = "audit_entries")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 64)
    private String username;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "target_type", length = 32)
    private String targetType;

    @Column(name = "target_id", length = 128)
    private String targetId;

    @Column(nullable = false, length = 16)
    private String result;  // 'success' | 'failed'

    @Lob
    @Column(name = "details_json")
    private String detailsJson;

    @Column(name = "source_ip", length = 64)
    private String sourceIp;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    protected AuditEntry() {
        // JPA
    }

    public AuditEntry(Long userId, String username, String action, String result,
                      String targetType, String targetId, String detailsJson,
                      String sourceIp, String userAgent) {
        this.eventTime = Instant.now();
        this.userId = userId;
        this.username = username;
        this.action = action;
        this.result = result;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detailsJson = detailsJson;
        this.sourceIp = sourceIp;
        this.userAgent = userAgent;
    }

    public Long getId() { return id; }
    public Instant getEventTime() { return eventTime; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getResult() { return result; }
    public String getDetailsJson() { return detailsJson; }
    public String getSourceIp() { return sourceIp; }
    public String getUserAgent() { return userAgent; }
}
