package id.co.jalin.seconsole.repository;

import id.co.jalin.seconsole.domain.AuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Audit entries are append-only — the API only needs read queries. All
 * filter parameters are nullable; callers pass {@code null} to skip that
 * criterion.
 *
 * <p>One query rather than a matrix of {@code findByXxxAndYyy...} methods
 * because the UI lets any combination of filters be active or omitted.
 * JPQL with {@code :param IS NULL OR column = :param} is the standard
 * idiom for conditional predicates with Spring Data, and Hibernate
 * optimises away the redundant comparisons at plan time.
 *
 * <p>{@code username} uses {@code LIKE} for substring search — operators
 * often remember a partial name ("adm…" for "admin"), not the exact
 * login.
 */
public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    @Query("""
            SELECT a FROM AuditEntry a
            WHERE (:fromTs IS NULL OR a.eventTime >= :fromTs)
              AND (:toTs   IS NULL OR a.eventTime <= :toTs)
              AND (:action IS NULL OR a.action = :action)
              AND (:result IS NULL OR a.result = :result)
              AND (:targetType IS NULL OR a.targetType = :targetType)
              AND (:username  IS NULL OR LOWER(a.username) LIKE LOWER(CONCAT('%', :username, '%')))
            ORDER BY a.eventTime DESC, a.id DESC
            """)
    Page<AuditEntry> findFiltered(
            @Param("fromTs") Instant fromTs,
            @Param("toTs") Instant toTs,
            @Param("action") String action,
            @Param("result") String result,
            @Param("targetType") String targetType,
            @Param("username") String username,
            Pageable pageable
    );

    /**
     * Non-paginated variant for CSV export. Same filter semantics, but
     * capped to a sane upper bound in the controller — a CSV of tens of
     * millions of rows is not useful and risks OOM / request timeout.
     */
    @Query("""
            SELECT a FROM AuditEntry a
            WHERE (:fromTs IS NULL OR a.eventTime >= :fromTs)
              AND (:toTs   IS NULL OR a.eventTime <= :toTs)
              AND (:action IS NULL OR a.action = :action)
              AND (:result IS NULL OR a.result = :result)
              AND (:targetType IS NULL OR a.targetType = :targetType)
              AND (:username  IS NULL OR LOWER(a.username) LIKE LOWER(CONCAT('%', :username, '%')))
            ORDER BY a.eventTime DESC, a.id DESC
            """)
    List<AuditEntry> findFilteredForExport(
            @Param("fromTs") Instant fromTs,
            @Param("toTs") Instant toTs,
            @Param("action") String action,
            @Param("result") String result,
            @Param("targetType") String targetType,
            @Param("username") String username,
            Pageable pageable
    );

    /** Distinct action values for filter dropdown hints. */
    @Query("SELECT DISTINCT a.action FROM AuditEntry a ORDER BY a.action")
    List<String> findDistinctActions();

    /** Distinct target types for filter dropdown hints. */
    @Query("SELECT DISTINCT a.targetType FROM AuditEntry a WHERE a.targetType IS NOT NULL ORDER BY a.targetType")
    List<String> findDistinctTargetTypes();
}
