package id.co.jalin.seconsole.engine.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Parent row — one per snapshot poll. Stores engine-level aggregate
 * counters that are cheap to query without joining child rows.
 *
 * <p>Per-socket detail lives in {@link EngineChannelSocketSampleEntity},
 * linked by {@code snapshot_header_id} with {@code ON DELETE CASCADE} —
 * deleting the header deletes all its samples.
 */
@Entity
@Table(name = "engine_channel_snapshot")
public class EngineChannelSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "snapshot_id", length = 64)
    private String snapshotId;

    @Column(name = "capture_duration_ms", nullable = false)
    private long captureDurationMs;

    // Aggregate counters — from snapshot.aggregate
    @Column(name = "socket_count", nullable = false)           private int socketCount;
    @Column(name = "sockets_up", nullable = false)             private int socketsUp;
    @Column(name = "sockets_down", nullable = false)           private int socketsDown;
    @Column(name = "total_active_channels", nullable = false)  private int totalActiveChannels;
    @Column(name = "total_msg_in", nullable = false)           private long totalMsgIn;
    @Column(name = "total_msg_out", nullable = false)          private long totalMsgOut;
    @Column(name = "total_queue_depth", nullable = false)      private long totalQueueDepth;
    @Column(name = "total_err_count", nullable = false)        private long totalErrCount;
    @Column(name = "avg_pressure_tps", nullable = false)       private long avgPressureTps;
    @Column(name = "avg_throughput_tps", nullable = false)     private long avgThroughputTps;

    public EngineChannelSnapshotEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }
    public String getSnapshotId() { return snapshotId; }
    public void setSnapshotId(String snapshotId) { this.snapshotId = snapshotId; }
    public long getCaptureDurationMs() { return captureDurationMs; }
    public void setCaptureDurationMs(long captureDurationMs) { this.captureDurationMs = captureDurationMs; }

    public int getSocketCount() { return socketCount; }
    public void setSocketCount(int socketCount) { this.socketCount = socketCount; }
    public int getSocketsUp() { return socketsUp; }
    public void setSocketsUp(int socketsUp) { this.socketsUp = socketsUp; }
    public int getSocketsDown() { return socketsDown; }
    public void setSocketsDown(int socketsDown) { this.socketsDown = socketsDown; }
    public int getTotalActiveChannels() { return totalActiveChannels; }
    public void setTotalActiveChannels(int totalActiveChannels) { this.totalActiveChannels = totalActiveChannels; }
    public long getTotalMsgIn() { return totalMsgIn; }
    public void setTotalMsgIn(long totalMsgIn) { this.totalMsgIn = totalMsgIn; }
    public long getTotalMsgOut() { return totalMsgOut; }
    public void setTotalMsgOut(long totalMsgOut) { this.totalMsgOut = totalMsgOut; }
    public long getTotalQueueDepth() { return totalQueueDepth; }
    public void setTotalQueueDepth(long totalQueueDepth) { this.totalQueueDepth = totalQueueDepth; }
    public long getTotalErrCount() { return totalErrCount; }
    public void setTotalErrCount(long totalErrCount) { this.totalErrCount = totalErrCount; }
    public long getAvgPressureTps() { return avgPressureTps; }
    public void setAvgPressureTps(long avgPressureTps) { this.avgPressureTps = avgPressureTps; }
    public long getAvgThroughputTps() { return avgThroughputTps; }
    public void setAvgThroughputTps(long avgThroughputTps) { this.avgThroughputTps = avgThroughputTps; }
}
