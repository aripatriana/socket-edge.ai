package com.socket.edge.grpc;

import com.socket.edge.constant.NodeRole;
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
 *   - StartSocket()         → delegates to AdminHttpService (by bindingId)
 *   - StopSocket()          → delegates to AdminHttpService (by bindingId)
 *   - RestartSocket()       → delegates to AdminHttpService (by bindingId)
 */
public class CoreServiceImpl extends CoreServiceGrpc.CoreServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(CoreServiceImpl.class);

    private final SystemInfo          cachedSystemInfo;
    private final MetricsBroadcaster  broadcaster;
    private final AdminHttpService    adminService;
    private final ReloadCfgService    reloadService;
    private final AiWeightRegistry    aiWeightRegistry;
    private final ChannelGroupRegistry groupRegistry;
    private final boolean              clusterEnabled;

    public CoreServiceImpl(SystemInfo cachedSystemInfo,
                           MetricsBroadcaster broadcaster,
                           AdminHttpService adminService,
                           ReloadCfgService reloadService,
                           AiWeightRegistry aiWeightRegistry,
                           ChannelGroupRegistry groupRegistry,
                           boolean clusterEnabled) {
        this.cachedSystemInfo = cachedSystemInfo;
        this.broadcaster      = broadcaster;
        this.adminService     = adminService;
        this.reloadService    = reloadService;
        this.aiWeightRegistry = aiWeightRegistry;
        this.groupRegistry    = groupRegistry;
        this.clusterEnabled   = clusterEnabled;
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

    // ── Health ────────────────────────────────────────────────────────────────

    @Override
    public void getHealth(Empty request, StreamObserver<HealthResponse> responseObserver) {
        try {
            NodeRole role = adminService.getNodeRole();
            responseObserver.onNext(HealthResponse.newBuilder()
                    .setStatus(role == NodeRole.MASTER ? "OK" : "STANDBY")
                    .setRole(role.name())
                    .setMode(clusterEnabled ? "CLUSTER" : "STANDALONE")
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("GetHealth failed", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Health check failed: " + e.getMessage())
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

    // ── Socket control (by bindingId) ─────────────────────────────────────────

    @Override
    public void startSocket(SocketRequest request, StreamObserver<ControlResponse> responseObserver) {
        handleSocketControl("start", request.getBindingId(), responseObserver,
                () -> adminService.startSocketById(request.getBindingId()));
    }

    @Override
    public void stopSocket(SocketRequest request, StreamObserver<ControlResponse> responseObserver) {
        handleSocketControl("stop", request.getBindingId(), responseObserver,
                () -> adminService.stopSocketById(request.getBindingId()));
    }

    @Override
    public void restartSocket(SocketRequest request, StreamObserver<ControlResponse> responseObserver) {
        handleSocketControl("restart", request.getBindingId(), responseObserver,
                () -> adminService.restartSocketById(request.getBindingId()));
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

    private void handleSocketControl(String action, String bindingId,
                                      StreamObserver<ControlResponse> obs, Runnable op) {
        if (bindingId == null || bindingId.isBlank()) {
            obs.onError(Status.INVALID_ARGUMENT
                    .withDescription("binding_id is required").asRuntimeException());
            return;
        }
        try {
            log.info("Socket {} requested via gRPC: {}", action, bindingId);
            op.run();
            respond(obs, true, "Socket " + action + " successful: " + bindingId);
        } catch (Exception e) {
            log.error("Socket {} failed: {}", action, bindingId, e);
            respond(obs, false, action + " failed: " + e.getMessage());
        }
    }

    private static void respond(StreamObserver<ControlResponse> obs, boolean success, String msg) {
        obs.onNext(ControlResponse.newBuilder().setSuccess(success).setMessage(msg).build());
        obs.onCompleted();
    }
}
