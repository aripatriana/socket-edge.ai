package com.socket.edge.grpc;

import com.socket.edge.http.service.AdminHttpService;
import com.socket.edge.http.service.ReloadCfgService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC service implementation hosted in se-core.
 *
 * Implements:
 *   - GetSystemInfo()     → returns cached static host info
 *   - SubscribeMetrics()  → registers caller as metrics subscriber
 *   - StartChannel()      → delegates to AdminHttpService
 *   - StopChannel()       → delegates to AdminHttpService
 *   - RestartChannel()    → delegates to AdminHttpService
 *   - ReloadConfig()      → delegates to ReloadCfgService
 */
public class CoreServiceImpl extends CoreServiceGrpc.CoreServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(CoreServiceImpl.class);

    private final SystemInfo       cachedSystemInfo;
    private final MetricsBroadcaster broadcaster;
    private final AdminHttpService adminService;
    private final ReloadCfgService reloadService;

    public CoreServiceImpl(SystemInfo cachedSystemInfo,
                           MetricsBroadcaster broadcaster,
                           AdminHttpService adminService,
                           ReloadCfgService reloadService) {
        this.cachedSystemInfo = cachedSystemInfo;
        this.broadcaster      = broadcaster;
        this.adminService     = adminService;
        this.reloadService    = reloadService;
    }

    // ── Static info ───────────────────────────────────────────────────────────

    @Override
    public void getSystemInfo(Empty request, StreamObserver<SystemInfo> responseObserver) {
        responseObserver.onNext(cachedSystemInfo);
        responseObserver.onCompleted();
    }

    // ── Metrics streaming ─────────────────────────────────────────────────────

    @Override
    public void subscribeMetrics(Empty request, StreamObserver<MetricsBundle> responseObserver) {
        log.info("New metrics subscriber connected");
        broadcaster.addSubscriber(responseObserver);
        // Stream stays open — broadcaster calls onNext periodically.
    }

    // ── Channel control ───────────────────────────────────────────────────────

    @Override
    public void startChannel(ChannelRequest request, StreamObserver<ControlResponse> responseObserver) {
        handleControl("start", request.getChannelName(), responseObserver,
                () -> adminService.startSocketByName(request.getChannelName()));
    }

    @Override
    public void stopChannel(ChannelRequest request, StreamObserver<ControlResponse> responseObserver) {
        handleControl("stop", request.getChannelName(), responseObserver,
                () -> adminService.stopSocketByName(request.getChannelName()));
    }

    @Override
    public void restartChannel(ChannelRequest request, StreamObserver<ControlResponse> responseObserver) {
        handleControl("restart", request.getChannelName(), responseObserver,
                () -> adminService.restartSocketByName(request.getChannelName()));
    }

    @Override
    public void reloadConfig(Empty request, StreamObserver<ControlResponse> responseObserver) {
        try {
            log.info("Config reload requested via gRPC");
            reloadService.reload();
            respond(responseObserver, true, "Config reloaded successfully");
        } catch (Exception e) {
            log.error("Config reload failed", e);
            respond(responseObserver, false, "Reload failed: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void handleControl(String action, String channel,
                                StreamObserver<ControlResponse> obs, Runnable op) {
        if (channel == null || channel.isBlank()) {
            obs.onError(Status.INVALID_ARGUMENT
                    .withDescription("channel_name is required").asRuntimeException());
            return;
        }
        try {
            log.info("Channel {} requested via gRPC: {}", action, channel);
            op.run();
            respond(obs, true, "Channel " + action + " successful: " + channel);
        } catch (Exception e) {
            log.error("Channel {} failed: {}", action, channel, e);
            respond(obs, false, action + " failed: " + e.getMessage());
        }
    }

    private static void respond(StreamObserver<ControlResponse> obs, boolean success, String msg) {
        obs.onNext(ControlResponse.newBuilder().setSuccess(success).setMessage(msg).build());
        obs.onCompleted();
    }
}