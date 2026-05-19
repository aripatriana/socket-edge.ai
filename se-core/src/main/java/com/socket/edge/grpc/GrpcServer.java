package com.socket.edge.grpc;

import com.socket.edge.core.AiWeightRegistry;
import com.socket.edge.core.TelemetryRegistry;
import com.socket.edge.core.socket.ChannelGroupRegistry;
import com.socket.edge.grpc.channel.ChannelSnapshotCollector;
import com.socket.edge.grpc.jvm.JvmMetricsCollector;
import com.socket.edge.grpc.os.OsMetricsCollector;
import com.socket.edge.grpc.os.SystemInfoCollector;
import com.socket.edge.http.service.AdminHttpService;
import com.socket.edge.http.service.ReloadCfgService;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Manages the gRPC server lifecycle within se-core.
 *
 * Start:  grpcServer.start(port, intervalMs)
 * Stop:   grpcServer.stop()
 *
 * Default port: 9090 (override via -Dgrpc.port=XXXX)
 * Default interval: 2000ms (override via -Dgrpc.metrics.interval.ms=XXXX)
 *
 * grpcurl examples (server reflection enabled by default):
 *   grpcurl -plaintext localhost:9090 list
 *   grpcurl -plaintext localhost:9090 socket.edge.CoreService/GetSystemInfo
 *   grpcurl -plaintext -d '{"channel_name":"bni"}' localhost:9090 socket.edge.CoreService/StartChannel
 */
public class GrpcServer {

    private static final Logger log = LoggerFactory.getLogger(GrpcServer.class);

    private final AdminHttpService     adminService;
    private final ReloadCfgService     reloadService;
    private final TelemetryRegistry    telemetryRegistry;
    private final AiWeightRegistry     aiWeightRegistry;
    private final ChannelGroupRegistry groupRegistry;
    private final boolean              clusterEnabled;

    private Server             server;
    private MetricsBroadcaster broadcaster;

    public GrpcServer(AdminHttpService adminService,
                      ReloadCfgService reloadService,
                      TelemetryRegistry telemetryRegistry,
                      AiWeightRegistry aiWeightRegistry,
                      ChannelGroupRegistry groupRegistry,
                      boolean clusterEnabled) {
        this.adminService      = adminService;
        this.reloadService     = reloadService;
        this.telemetryRegistry = telemetryRegistry;
        this.aiWeightRegistry  = aiWeightRegistry;
        this.groupRegistry     = groupRegistry;
        this.clusterEnabled    = clusterEnabled;
    }

    public void start(int port, long intervalMs) throws IOException {
        oshi.SystemInfo oshi = new oshi.SystemInfo();

        SystemInfoCollector infoCollector = new SystemInfoCollector(oshi);
        SystemInfo systemInfo = infoCollector.collect();

        OsMetricsCollector      osCollector      = new OsMetricsCollector(oshi);
        JvmMetricsCollector     jvmCollector     = new JvmMetricsCollector();
        ChannelSnapshotCollector channelCollector = new ChannelSnapshotCollector(telemetryRegistry);

        String nodeId = System.getProperty("node.id", systemInfo.getHostname());

        broadcaster = new MetricsBroadcaster(osCollector, jvmCollector, channelCollector, intervalMs, nodeId);
        broadcaster.start();

        CoreServiceImpl coreService = new CoreServiceImpl(
                systemInfo, broadcaster, adminService, reloadService, aiWeightRegistry, groupRegistry, clusterEnabled);

        server = NettyServerBuilder.forPort(port)
                .addService(coreService)
                .addService(ProtoReflectionService.newInstance())
                .maxInboundMessageSize(1024 * 1024)
                .build()
                .start();

        log.info("gRPC server started: port={} interval={}ms node={}", port, intervalMs, nodeId);
    }

    public void stop() {
        if (broadcaster != null) {
            broadcaster.stop();
        }
        if (server != null) {
            server.shutdown();
            try {
                if (!server.awaitTermination(10, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                server.shutdownNow();
            }
            log.info("gRPC server stopped");
        }
    }
}
