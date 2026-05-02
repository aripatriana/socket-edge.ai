package id.co.jalin.seconsole.grpc;

import com.socket.edge.grpc.CoreServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Manages the gRPC ManagedChannel to se-core and provides access to service stubs.
 *
 * Channel lifecycle: created on startup, shut down gracefully on context close.
 * Connection is lazy — gRPC connects on first RPC call, not at channel creation.
 */
@Component
public class CoreGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(CoreGrpcClient.class);

    private final ManagedChannel channel;

    public CoreGrpcClient(
            @Value("${seconsole.se-core.grpc-host:localhost}") String host,
            @Value("${seconsole.se-core.grpc-port:9090}")       int    port) {

        this.channel = NettyChannelBuilder.forAddress(host, port)
                .usePlaintext()           // TLS can be enabled later via .useTransportSecurity()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .build();

        log.info("gRPC channel created: {}:{}", host, port);
    }

    /** Async stub — used for server streaming (SubscribeMetrics). */
    public CoreServiceGrpc.CoreServiceStub asyncStub() {
        return CoreServiceGrpc.newStub(channel);
    }

    /** Blocking stub — used for unary RPCs (GetSystemInfo, control commands). */
    public CoreServiceGrpc.CoreServiceBlockingStub blockingStub() {
        return CoreServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            log.info("gRPC channel shut down");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }
}