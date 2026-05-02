package id.co.jalin.seconsole.service;

import id.co.jalin.seconsole.domain.AuditEntry;
import id.co.jalin.seconsole.repository.AuditEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit logging. Kept intentionally minimal for Chat 2 — just login-related events.
 * Later phases will add channel start/stop, config apply, user management, etc.
 *
 * Each call is REQUIRES_NEW so an audit entry is persisted even if the caller's
 * transaction rolls back (e.g. failed login where we want the failure recorded
 * regardless of whether the User update succeeds).
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_FAILED = "failed";

    private final AuditEntryRepository repository;

    public AuditService(AuditEntryRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(Long userId, String username, String action,
                           String targetType, String targetId,
                           String detailsJson, String sourceIp, String userAgent) {
        persist(userId, username, action, RESULT_SUCCESS, targetType, targetId,
                detailsJson, sourceIp, userAgent);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(Long userId, String username, String action,
                           String targetType, String targetId,
                           String detailsJson, String sourceIp, String userAgent) {
        persist(userId, username, action, RESULT_FAILED, targetType, targetId,
                detailsJson, sourceIp, userAgent);
    }

    // --- Convenience overloads for login -------------------------------------

    public void logLoginSuccess(Long userId, String username, String sourceIp, String userAgent) {
        logSuccess(userId, username, "LOGIN", "user", String.valueOf(userId),
                null, sourceIp, userAgent);
    }

    public void logLoginFailure(String username, String reason, String sourceIp, String userAgent) {
        // userId is null — user may not exist
        String details = reason == null ? null : "{\"reason\":\"" + escape(reason) + "\"}";
        logFailure(null, username, "LOGIN", "user", null, details, sourceIp, userAgent);
    }

    public void logLogout(Long userId, String username, String jti, String sourceIp, String userAgent) {
        logSuccess(userId, username, "LOGOUT", "session", jti, null, sourceIp, userAgent);
    }

    public void logPasswordChange(Long userId, String username, String sourceIp, String userAgent) {
        logSuccess(userId, username, "CHANGE_PASSWORD", "user", String.valueOf(userId),
                null, sourceIp, userAgent);
    }

    // --- Internal ------------------------------------------------------------

    private void persist(Long userId, String username, String action, String result,
                         String targetType, String targetId, String detailsJson,
                         String sourceIp, String userAgent) {
        try {
            AuditEntry entry = new AuditEntry(userId, username, action, result,
                    targetType, targetId, detailsJson, sourceIp, userAgent);
            repository.save(entry);
        } catch (Exception e) {
            // Audit logging must never break the user-facing operation.
            log.error("Failed to persist audit entry: action={} user={} result={}",
                    action, username, result, e);
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
