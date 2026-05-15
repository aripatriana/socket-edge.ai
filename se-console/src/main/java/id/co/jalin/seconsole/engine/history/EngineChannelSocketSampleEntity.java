package id.co.jalin.seconsole.engine.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per (snapshot, socket) — the per-socket time-series sample.
 *
 * <p>{@code snapshot_header_id} FK to {@link EngineChannelSnapshotEntity};
 * delete cascades from header to child at the DB level (see Flyway V3).
 *
 * <p>We de-normalise {@code captured_at} here so "chart TPS of bindingId X for
 * last hour" is a single-table range scan without joining the header —
 * cheaper at query time, at a small cost in disk (8 bytes × ~2M rows/day ≈
 * 16 MB/day, trivial).
 */
@Entity
@Table(
    name = "engine_channel_socket_sample",
    indexes = {
        @Index(name = "idx_ecss_header", columnList = "snapshot_header_id"),
        @Index(name = "idx_ecss_binding_time", columnList = "binding_id,captured_at DESC")
    }
)
public class EngineChannelSocketSampleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "snapshot_header_id", nullable = false)
    private Long snapshotHeaderId;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    // Identity
    @Column(name = "binding_id", length = 16, nullable = false) private String bindingId;
    @Column(name = "socket_id", length = 128, nullable = false) private String socketId;
    @Column(name = "channel_name", length = 64, nullable = false) private String channelName;
    @Column(name = "socket_type", length = 8, nullable = false) private String socketType;

    // Runtime
    @Column(name = "state", length = 16, nullable = false)     private String state;
    @Column(name = "local_host", length = 64)                  private String localHost;
    @Column(name = "remote_host", length = 256)                private String remoteHost;
    @Column(name = "active_channels", nullable = false)        private int activeChannels;
    @Column(name = "start_time", nullable = false)             private long startTime;
    @Column(name = "last_connect", nullable = false)           private long lastConnect;
    @Column(name = "last_disconnect", nullable = false)        private long lastDisconnect;

    // Queue
    @Column(name = "msg_in", nullable = false)                 private long msgIn;
    @Column(name = "msg_out", nullable = false)                private long msgOut;
    @Column(name = "queue_depth", nullable = false)            private long queueDepth;
    @Column(name = "err_count", nullable = false)              private long errCount;
    @Column(name = "last_err", nullable = false)               private long lastErr;
    @Column(name = "last_msg", nullable = false)               private long lastMsg;

    // Latency (ns)
    @Column(name = "lat_avg_ns", nullable = false)             private long latAvgNs;
    @Column(name = "lat_min_ns", nullable = false)             private long latMinNs;
    @Column(name = "lat_max_ns", nullable = false)             private long latMaxNs;
    @Column(name = "lat_p90_ns", nullable = false)             private long latP90Ns;
    @Column(name = "lat_p95_ns", nullable = false)             private long latP95Ns;

    // Pressure TPS
    @Column(name = "pressure_avg", nullable = false)           private long pressureAvg;
    @Column(name = "pressure_min", nullable = false)           private long pressureMin;
    @Column(name = "pressure_max", nullable = false)           private long pressureMax;
    @Column(name = "pressure_p90", nullable = false)           private long pressureP90;
    @Column(name = "pressure_p95", nullable = false)           private long pressureP95;

    // Throughput TPS
    @Column(name = "throughput_avg", nullable = false)         private long throughputAvg;
    @Column(name = "throughput_min", nullable = false)         private long throughputMin;
    @Column(name = "throughput_max", nullable = false)         private long throughputMax;
    @Column(name = "throughput_p90", nullable = false)         private long throughputP90;
    @Column(name = "throughput_p95", nullable = false)         private long throughputP95;

    public EngineChannelSocketSampleEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSnapshotHeaderId() { return snapshotHeaderId; }
    public void setSnapshotHeaderId(Long snapshotHeaderId) { this.snapshotHeaderId = snapshotHeaderId; }
    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }

    public String getBindingId() { return bindingId; }
    public void setBindingId(String bindingId) { this.bindingId = bindingId; }
    public String getSocketId() { return socketId; }
    public void setSocketId(String socketId) { this.socketId = socketId; }
    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }
    public String getSocketType() { return socketType; }
    public void setSocketType(String socketType) { this.socketType = socketType; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getLocalHost() { return localHost; }
    public void setLocalHost(String localHost) { this.localHost = localHost; }
    public String getRemoteHost() { return remoteHost; }
    public void setRemoteHost(String remoteHost) { this.remoteHost = remoteHost; }
    public int getActiveChannels() { return activeChannels; }
    public void setActiveChannels(int activeChannels) { this.activeChannels = activeChannels; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public long getLastConnect() { return lastConnect; }
    public void setLastConnect(long lastConnect) { this.lastConnect = lastConnect; }
    public long getLastDisconnect() { return lastDisconnect; }
    public void setLastDisconnect(long lastDisconnect) { this.lastDisconnect = lastDisconnect; }

    public long getMsgIn() { return msgIn; }
    public void setMsgIn(long msgIn) { this.msgIn = msgIn; }
    public long getMsgOut() { return msgOut; }
    public void setMsgOut(long msgOut) { this.msgOut = msgOut; }
    public long getQueueDepth() { return queueDepth; }
    public void setQueueDepth(long queueDepth) { this.queueDepth = queueDepth; }
    public long getErrCount() { return errCount; }
    public void setErrCount(long errCount) { this.errCount = errCount; }
    public long getLastErr() { return lastErr; }
    public void setLastErr(long lastErr) { this.lastErr = lastErr; }
    public long getLastMsg() { return lastMsg; }
    public void setLastMsg(long lastMsg) { this.lastMsg = lastMsg; }

    public long getLatAvgNs() { return latAvgNs; }
    public void setLatAvgNs(long latAvgNs) { this.latAvgNs = latAvgNs; }
    public long getLatMinNs() { return latMinNs; }
    public void setLatMinNs(long latMinNs) { this.latMinNs = latMinNs; }
    public long getLatMaxNs() { return latMaxNs; }
    public void setLatMaxNs(long latMaxNs) { this.latMaxNs = latMaxNs; }
    public long getLatP90Ns() { return latP90Ns; }
    public void setLatP90Ns(long latP90Ns) { this.latP90Ns = latP90Ns; }
    public long getLatP95Ns() { return latP95Ns; }
    public void setLatP95Ns(long latP95Ns) { this.latP95Ns = latP95Ns; }

    public long getPressureAvg() { return pressureAvg; }
    public void setPressureAvg(long pressureAvg) { this.pressureAvg = pressureAvg; }
    public long getPressureMin() { return pressureMin; }
    public void setPressureMin(long pressureMin) { this.pressureMin = pressureMin; }
    public long getPressureMax() { return pressureMax; }
    public void setPressureMax(long pressureMax) { this.pressureMax = pressureMax; }
    public long getPressureP90() { return pressureP90; }
    public void setPressureP90(long pressureP90) { this.pressureP90 = pressureP90; }
    public long getPressureP95() { return pressureP95; }
    public void setPressureP95(long pressureP95) { this.pressureP95 = pressureP95; }

    public long getThroughputAvg() { return throughputAvg; }
    public void setThroughputAvg(long throughputAvg) { this.throughputAvg = throughputAvg; }
    public long getThroughputMin() { return throughputMin; }
    public void setThroughputMin(long throughputMin) { this.throughputMin = throughputMin; }
    public long getThroughputMax() { return throughputMax; }
    public void setThroughputMax(long throughputMax) { this.throughputMax = throughputMax; }
    public long getThroughputP90() { return throughputP90; }
    public void setThroughputP90(long throughputP90) { this.throughputP90 = throughputP90; }
    public long getThroughputP95() { return throughputP95; }
    public void setThroughputP95(long throughputP95) { this.throughputP95 = throughputP95; }
}
