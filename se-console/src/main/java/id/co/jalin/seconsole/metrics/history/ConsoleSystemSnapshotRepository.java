package id.co.jalin.seconsole.metrics.history;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ConsoleSystemSnapshotRepository
        extends JpaRepository<ConsoleSystemSnapshotEntity, Long> {

    List<ConsoleSystemSnapshotEntity> findTop500ByOrderByCapturedAtDesc();

    List<ConsoleSystemSnapshotEntity> findByCapturedAtBetweenOrderByCapturedAtAsc(
            Instant from, Instant to);

    @Modifying
    @Query("delete from ConsoleSystemSnapshotEntity e where e.capturedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
