package id.co.jalin.seconsole.engine.history;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EngineJvmSnapshotRepository extends JpaRepository<EngineJvmSnapshotEntity, Long> {

    /** Newest-first, typically bounded by the caller's pagination. */
    List<EngineJvmSnapshotEntity> findTop500ByOrderByCapturedAtDesc();

    /** Samples captured within a window, oldest-first — for chart backfill. */
    List<EngineJvmSnapshotEntity> findByCapturedAtBetweenOrderByCapturedAtAsc(
            Instant from, Instant to);

    /** Drop everything older than the retention cut-off. */
    @Modifying
    @Query("delete from EngineJvmSnapshotEntity e where e.capturedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
