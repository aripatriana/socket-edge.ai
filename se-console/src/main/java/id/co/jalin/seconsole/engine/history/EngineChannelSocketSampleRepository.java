package id.co.jalin.seconsole.engine.history;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface EngineChannelSocketSampleRepository
        extends JpaRepository<EngineChannelSocketSampleEntity, Long> {

    /** Time-series for one socket within a window, oldest-first for charting. */
    @Query("SELECT s FROM EngineChannelSocketSampleEntity s " +
           "WHERE s.hashId = :hashId AND s.capturedAt BETWEEN :from AND :to " +
           "ORDER BY s.capturedAt ASC")
    List<EngineChannelSocketSampleEntity> findForSocketBetween(
            @Param("hashId") String hashId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /** Time-series for a set of sockets (one channel's sockets) in one go. */
    @Query("SELECT s FROM EngineChannelSocketSampleEntity s " +
           "WHERE s.hashId IN :hashIds AND s.capturedAt BETWEEN :from AND :to " +
           "ORDER BY s.capturedAt ASC")
    List<EngineChannelSocketSampleEntity> findForSocketsBetween(
            @Param("hashIds") Collection<String> hashIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /** For diagnostics — not used at runtime. */
    long countBySnapshotHeaderId(Long snapshotHeaderId);
}
