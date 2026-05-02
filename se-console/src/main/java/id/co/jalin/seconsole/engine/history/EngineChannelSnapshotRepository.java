package id.co.jalin.seconsole.engine.history;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EngineChannelSnapshotRepository extends JpaRepository<EngineChannelSnapshotEntity, Long> {

    /** Most recent header rows — capped, for admin / diagnostics. */
    List<EngineChannelSnapshotEntity> findTop200ByOrderByCapturedAtDesc();

    /**
     * Returns IDs of header rows older than the cutoff, limited to a safe
     * batch size. Deletes in chunks are issued against these IDs — child
     * rows cascade via the FK (see V3 migration).
     */
    @Query(value = "SELECT e.id FROM EngineChannelSnapshotEntity e " +
                   "WHERE e.capturedAt < :cutoff " +
                   "ORDER BY e.capturedAt ASC")
    List<Long> findIdsOlderThan(@Param("cutoff") Instant cutoff,
                                 org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("DELETE FROM EngineChannelSnapshotEntity e WHERE e.id IN :ids")
    int deleteByIds(@Param("ids") List<Long> ids);
}
