package id.co.jalin.seconsole.service;

import id.co.jalin.seconsole.dto.response.JvmMetricsDto;
import id.co.jalin.seconsole.dto.response.SystemMetricsDto;
import id.co.jalin.seconsole.grpc.GrpcMetricsSubscriber;
import id.co.jalin.seconsole.grpc.JvmSnapshotMapper;
import id.co.jalin.seconsole.grpc.OsSnapshotMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Provides system metrics to MetricsController.
 *
 * v2: Data source migrated from local OSHI probing to se-core gRPC stream.
 *     se-core owns all metrics collection (OS + JVM + channel).
 *     This service is now a thin facade over GrpcMetricsSubscriber.
 */
@Service
public class SystemMetricsService {

    private static final Logger log = LoggerFactory.getLogger(SystemMetricsService.class);

    private final GrpcMetricsSubscriber subscriber;
    private final OsSnapshotMapper      osMapper;
    private final JvmSnapshotMapper     jvmMapper;

    public SystemMetricsService(GrpcMetricsSubscriber subscriber,
                                OsSnapshotMapper osMapper,
                                JvmSnapshotMapper jvmMapper) {
        this.subscriber = subscriber;
        this.osMapper   = osMapper;
        this.jvmMapper  = jvmMapper;
    }

    /**
     * Returns the latest OS metrics snapshot received from se-core.
     * Returns null if se-core has not yet sent a bundle (startup, or se-core is down).
     */
    public SystemMetricsDto snapshot() {
        var bundle = subscriber.getLatestBundle();
        if (bundle == null) {
            log.debug("No metrics bundle yet from se-core");
            return null;
        }
        return osMapper.toDto(bundle, subscriber.getCachedSystemInfo());
    }

    /**
     * Returns the latest JVM metrics snapshot received from se-core.
     * Returns null if se-core has not yet sent a bundle or JVM snapshot is absent.
     */
    public JvmMetricsDto jvmSnapshot() {
        var bundle = subscriber.getLatestBundle();
        if (bundle == null || !bundle.hasJvm()) {
            log.debug("No JVM metrics bundle yet from se-core");
            return null;
        }
        return jvmMapper.toDto(bundle.getJvm());
    }
}