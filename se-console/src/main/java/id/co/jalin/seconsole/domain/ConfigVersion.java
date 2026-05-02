package id.co.jalin.seconsole.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per applied config version. Append-only — rollback creates a new
 * row rather than deleting history, so the audit trail stays intact.
 *
 * <p>Schema lives in {@code V1__initial_schema.sql} (table {@code config_versions}).
 */
@Entity
@Table(name = "config_versions")
public class ConfigVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, length = 128)
    private String fileName;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, columnDefinition = "CLOB")
    private String content;

    @Column(name = "content_sha", nullable = false, length = 64)
    private String contentSha;

    @Column(name = "author_user_id")
    private Long authorUserId;

    @Column(name = "author_username", length = 64)
    private String authorUsername;

    @Column(length = 512)
    private String description;

    @Column(columnDefinition = "CLOB")
    private String reason;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @Column(name = "apply_duration_ms")
    private Integer applyDurationMs;

    /** "success" | "failed" | "rolled_back" */
    @Column(name = "apply_result", nullable = false, length = 16)
    private String applyResult;

    @Column(name = "reload_impact_json", columnDefinition = "CLOB")
    private String reloadImpactJson;

    /** Source version if this row was created by a rollback; null otherwise. */
    @Column(name = "rolled_back_from_version")
    private Integer rolledBackFromVersion;

    @Column(name = "is_milestone", nullable = false)
    private boolean milestone;

    // --- JPA plumbing --------------------------------------------------------

    public ConfigVersion() {
        // JPA
    }

    public ConfigVersion(String fileName, Integer version, String content, String contentSha,
                         Long authorUserId, String authorUsername, String description,
                         String reason, Instant appliedAt, Integer applyDurationMs,
                         String applyResult, String reloadImpactJson,
                         Integer rolledBackFromVersion, boolean milestone) {
        this.fileName = fileName;
        this.version = version;
        this.content = content;
        this.contentSha = contentSha;
        this.authorUserId = authorUserId;
        this.authorUsername = authorUsername;
        this.description = description;
        this.reason = reason;
        this.appliedAt = appliedAt;
        this.applyDurationMs = applyDurationMs;
        this.applyResult = applyResult;
        this.reloadImpactJson = reloadImpactJson;
        this.rolledBackFromVersion = rolledBackFromVersion;
        this.milestone = milestone;
    }

    // --- Getters -------------------------------------------------------------

    public Long getId() { return id; }
    public String getFileName() { return fileName; }
    public Integer getVersion() { return version; }
    public String getContent() { return content; }
    public String getContentSha() { return contentSha; }
    public Long getAuthorUserId() { return authorUserId; }
    public String getAuthorUsername() { return authorUsername; }
    public String getDescription() { return description; }
    public String getReason() { return reason; }
    public Instant getAppliedAt() { return appliedAt; }
    public Integer getApplyDurationMs() { return applyDurationMs; }
    public String getApplyResult() { return applyResult; }
    public String getReloadImpactJson() { return reloadImpactJson; }
    public Integer getRolledBackFromVersion() { return rolledBackFromVersion; }
    public boolean isMilestone() { return milestone; }

    // --- Setters (rollback path needs to re-mark a freshly-created row) ------
    // We intentionally only expose setters for the small set of fields that
    // get mutated post-insert — other fields are immutable by design.

    public void setApplyResult(String applyResult) { this.applyResult = applyResult; }
    public void setRolledBackFromVersion(Integer version) { this.rolledBackFromVersion = version; }
    public void setDescription(String description) { this.description = description; }
    public void setReloadImpactJson(String json) { this.reloadImpactJson = json; }
}
