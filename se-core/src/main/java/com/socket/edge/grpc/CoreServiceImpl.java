package com.socket.edge.grpc;

import com.socket.edge.core.AiWeightRegistry;
import com.socket.edge.core.socket.ChannelGroup;
import com.socket.edge.core.socket.ChannelGroupRegistry;
import com.socket.edge.http.service.AdminHttpService;
import com.socket.edge.http.service.ReloadCfgService;
import com.socket.edge.model.ChannelCfg;
import com.socket.edge.model.ClientChannel;
import com.socket.edge.model.ServerChannel;
import com.socket.edge.model.SocketEndpoint;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * gRPC service implementation hosted in se-core.
 *
 * Implements:
 *   - GetSystemInfo()       → returns cached static host info
 *   - SubscribeMetrics()    → registers caller as metrics subscriber
 *   - GetChannelConfigs()   → returns live channel config from ChannelGroupRegistry
 *   - StartChannel()        → delegates to AdminHttpService
 *   - StopChannel()         → delegates to AdminHttpService
 *   - RestartChannel()      → delegates to AdminHttpService
 *   - ReloadConfig()        → delegates to ReloadCfgService
 */
public class CoreServiceImpl extends CoreServiceGrpc.CoreServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(CoreServiceImpl.class);

    private final SystemInfo          cachedSystemInfo;
    private final MetricsBroadcaster  broadcaster;
    private final AdminHttpService    adminService;
    private final ReloadCfgService    reloadService;
    private final AiWeightRegistry    aiWeightRegistry;
    private final ChannelGroupRegistry groupRegistry;

    public CoreServiceImpl(SystemInfo cachedSystemInfo,
                           MetricsBroadcaster broadcaster,
                           AdminHttpService adminService,
                           ReloadCfgService reloadService,
                           AiWeightRegistry aiWeightRegistry,
                           ChannelGroupRegistry groupRegistry) {
        this.cachedSystemInfo = cachedSystemInfo;
        this.broadcaster      = broadcaster;
        this.adminService     = adminService;
        this.reloadService    = reloadService;
        this.aiWeightRegistry = aiWeightRegistry;
        this.groupRegistry    = groupRegistry;
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

    // ── Static channel config ─────────────────────────────────────────────────

    @Override
    public void getChannelConfigs(Empty request, StreamObserver<ChannelConfigList> responseObserver) {
        try {
            List<ChannelConfig> configs = new ArrayList<>();
            for (ChannelGroup group : groupRegistry.all()) {
                configs.add(toProto(group.config()));
            }
            responseObserver.onNext(ChannelConfigList.newBuilder()
                    .addAllChannels(configs)
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("GetChannelConfigs failed", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to collect channel configs: " + e.getMessage())
                    .asRuntimeException());
        }
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

    // ── AI feedback ──────────────────────────────────────────────────────────

    @Override
    public void updateWeight(WeightUpdate request, StreamObserver<ControlResponse> responseObserver) {
        if (request.getChannelName() == null || request.getChannelName().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("channel_name is required").asRuntimeException());
            return;
        }
        LinkedHashMap<String, Integer> weights = new LinkedHashMap<>();
        for (EndpointWeight ew : request.getWeightsList()) {
            weights.put(ew.getBindingId(), ew.getWeight());
        }
        aiWeightRegistry.update(request.getChannelName(), weights);
        log.info("AI weight update: channel={} reward={} weights={}",
                request.getChannelName(),
                String.format("%.4f", request.getReward()),
                weights);
        respond(responseObserver, true, "Weight updated: " + request.getChannelName());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ChannelConfig toProto(ChannelCfg cfg) {
        ChannelConfig.Builder b = ChannelConfig.newBuilder()
                .setName(cfg.name() != null ? cfg.name() : "")
                .setType(cfg.type() != null ? cfg.type() : "")
                .setUnknownMti(cfg.unknownMti() != null ? cfg.unknownMti() : "");

        if (cfg.profiles() != null) {
            b.addAllProfiles(cfg.profiles());
        }
        if (cfg.server() != null) {
            b.setServer(toServerProto(cfg.server()));
        }
        if (cfg.client() != null) {
            b.setClient(toClientProto(cfg.client()));
        }
        return b.build();
    }

    private static ServerChannelConfig toServerProto(ServerChannel s) {
        ServerChannelConfig.Builder b = ServerChannelConfig.newBuilder()
                .setListenHost(s.listenHost() != null ? s.listenHost() : "")
                .setListenPort(s.listenPort())
                .setStrategy(s.strategy() != null ? s.strategy() : "");
        if (s.pool() != null) {
            for (SocketEndpoint ep : s.pool()) b.addPool(toEndpointProto(ep));
        }
        return b.build();
    }

    private static ClientChannelConfig toClientProto(ClientChannel c) {
        ClientChannelConfig.Builder b = ClientChannelConfig.newBuilder()
                .setStrategy(c.strategy() != null ? c.strategy() : "");
        if (c.endpoints() != null) {
            for (SocketEndpoint ep : c.endpoints()) b.addEndpoints(toEndpointProto(ep));
        }
        return b.build();
    }

    private static SocketEndpointConfig toEndpointProto(SocketEndpoint ep) {
        return SocketEndpointConfig.newBuilder()
                .setHost(ep.host() != null ? ep.host() : "")
                .setPort(ep.port())
                .setWeight(ep.weight())
                .setPriority(ep.priority())
                .setMaxfails(ep.maxfails())
                .setFailTimeout(ep.failTimeout())
                .build();
    }

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
