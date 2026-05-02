package id.co.jalin.seconsole.metrics.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per network-metric poll tick. Mirrors {@code console_network_snapshot}
 * (V4). TCP state + quality counters live as flat columns (charts query them
 * directly); per-interface and per-listening-port detail are JSON because
 * cardinality varies.
 */
@Entity
@Table(name = "console_network_snapshot")
public class ConsoleNetworkSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    // --- TCP state counts ---
    @Column(name = "tcp_total", nullable = false)         private int tcpTotal;
    @Column(name = "tcp_established", nullable = false)   private int tcpEstablished;
    @Column(name = "tcp_time_wait", nullable = false)     private int tcpTimeWait;
    @Column(name = "tcp_close_wait", nullable = false)    private int tcpCloseWait;
    @Column(name = "tcp_listen", nullable = false)        private int tcpListen;
    @Column(name = "tcp_syn_sent", nullable = false)      private int tcpSynSent;
    @Column(name = "tcp_syn_recv", nullable = false)      private int tcpSynRecv;
    @Column(name = "tcp_fin_wait_1", nullable = false)    private int tcpFinWait1;
    @Column(name = "tcp_fin_wait_2", nullable = false)    private int tcpFinWait2;
    @Column(name = "tcp_last_ack", nullable = false)      private int tcpLastAck;
    @Column(name = "tcp_closing", nullable = false)       private int tcpClosing;
    @Column(name = "tcp_other", nullable = false)         private int tcpOther;

    // --- TCP quality ---
    @Column(name = "tcp_retrans_segs")       private Long tcpRetransSegs;
    @Column(name = "tcp_out_segs")           private Long tcpOutSegs;
    @Column(name = "tcp_out_resets")         private Long tcpOutResets;
    @Column(name = "tcp_in_errs")            private Long tcpInErrs;
    @Column(name = "tcp_attempt_fails")      private Long tcpAttemptFails;
    @Column(name = "tcp_estab_resets")       private Long tcpEstabResets;
    @Column(name = "tcp_curr_estab")         private Long tcpCurrEstab;
    @Column(name = "tcp_syncookies_sent")    private Long tcpSyncookiesSent;
    @Column(name = "tcp_listen_drops")       private Long tcpListenDrops;
    @Column(name = "tcp_listen_overflows")   private Long tcpListenOverflows;

    // --- Variable-cardinality JSON blobs ---
    @Lob @Column(name = "interfaces_json")        private String interfacesJson;
    @Lob @Column(name = "listening_ports_json")   private String listeningPortsJson;
    @Lob @Column(name = "tcp_states_raw_json")    private String tcpStatesRawJson;

    public ConsoleNetworkSnapshotEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }

    public int getTcpTotal() { return tcpTotal; }
    public void setTcpTotal(int tcpTotal) { this.tcpTotal = tcpTotal; }
    public int getTcpEstablished() { return tcpEstablished; }
    public void setTcpEstablished(int tcpEstablished) { this.tcpEstablished = tcpEstablished; }
    public int getTcpTimeWait() { return tcpTimeWait; }
    public void setTcpTimeWait(int tcpTimeWait) { this.tcpTimeWait = tcpTimeWait; }
    public int getTcpCloseWait() { return tcpCloseWait; }
    public void setTcpCloseWait(int tcpCloseWait) { this.tcpCloseWait = tcpCloseWait; }
    public int getTcpListen() { return tcpListen; }
    public void setTcpListen(int tcpListen) { this.tcpListen = tcpListen; }
    public int getTcpSynSent() { return tcpSynSent; }
    public void setTcpSynSent(int tcpSynSent) { this.tcpSynSent = tcpSynSent; }
    public int getTcpSynRecv() { return tcpSynRecv; }
    public void setTcpSynRecv(int tcpSynRecv) { this.tcpSynRecv = tcpSynRecv; }
    public int getTcpFinWait1() { return tcpFinWait1; }
    public void setTcpFinWait1(int tcpFinWait1) { this.tcpFinWait1 = tcpFinWait1; }
    public int getTcpFinWait2() { return tcpFinWait2; }
    public void setTcpFinWait2(int tcpFinWait2) { this.tcpFinWait2 = tcpFinWait2; }
    public int getTcpLastAck() { return tcpLastAck; }
    public void setTcpLastAck(int tcpLastAck) { this.tcpLastAck = tcpLastAck; }
    public int getTcpClosing() { return tcpClosing; }
    public void setTcpClosing(int tcpClosing) { this.tcpClosing = tcpClosing; }
    public int getTcpOther() { return tcpOther; }
    public void setTcpOther(int tcpOther) { this.tcpOther = tcpOther; }

    public Long getTcpRetransSegs() { return tcpRetransSegs; }
    public void setTcpRetransSegs(Long tcpRetransSegs) { this.tcpRetransSegs = tcpRetransSegs; }
    public Long getTcpOutSegs() { return tcpOutSegs; }
    public void setTcpOutSegs(Long tcpOutSegs) { this.tcpOutSegs = tcpOutSegs; }
    public Long getTcpOutResets() { return tcpOutResets; }
    public void setTcpOutResets(Long tcpOutResets) { this.tcpOutResets = tcpOutResets; }
    public Long getTcpInErrs() { return tcpInErrs; }
    public void setTcpInErrs(Long tcpInErrs) { this.tcpInErrs = tcpInErrs; }
    public Long getTcpAttemptFails() { return tcpAttemptFails; }
    public void setTcpAttemptFails(Long tcpAttemptFails) { this.tcpAttemptFails = tcpAttemptFails; }
    public Long getTcpEstabResets() { return tcpEstabResets; }
    public void setTcpEstabResets(Long tcpEstabResets) { this.tcpEstabResets = tcpEstabResets; }
    public Long getTcpCurrEstab() { return tcpCurrEstab; }
    public void setTcpCurrEstab(Long tcpCurrEstab) { this.tcpCurrEstab = tcpCurrEstab; }
    public Long getTcpSyncookiesSent() { return tcpSyncookiesSent; }
    public void setTcpSyncookiesSent(Long tcpSyncookiesSent) { this.tcpSyncookiesSent = tcpSyncookiesSent; }
    public Long getTcpListenDrops() { return tcpListenDrops; }
    public void setTcpListenDrops(Long tcpListenDrops) { this.tcpListenDrops = tcpListenDrops; }
    public Long getTcpListenOverflows() { return tcpListenOverflows; }
    public void setTcpListenOverflows(Long tcpListenOverflows) { this.tcpListenOverflows = tcpListenOverflows; }

    public String getInterfacesJson() { return interfacesJson; }
    public void setInterfacesJson(String interfacesJson) { this.interfacesJson = interfacesJson; }
    public String getListeningPortsJson() { return listeningPortsJson; }
    public void setListeningPortsJson(String listeningPortsJson) { this.listeningPortsJson = listeningPortsJson; }
    public String getTcpStatesRawJson() { return tcpStatesRawJson; }
    public void setTcpStatesRawJson(String tcpStatesRawJson) { this.tcpStatesRawJson = tcpStatesRawJson; }
}
