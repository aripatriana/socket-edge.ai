package com.socket.edge.core;

import com.socket.edge.utils.JsonUtil;
import com.socket.edge.utils.PciMaskUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Structured audit trail writer for ISO 8583 message routing decisions.
 *
 * <p>Writes JSON-lines entries via a dedicated SLF4J logger ({@code "audit"}).
 * Async delivery and file routing are delegated entirely to the logging
 * framework — configure a dedicated appender for the {@code audit} logger
 * in {@code logback.xml} / {@code log4j2.xml} backed by the Disruptor
 * async pipeline.
 *
 * <p>Features:
 * <ul>
 *   <li>Non-blocking — no internal queue or writer thread</li>
 *   <li>PCI field masking applied before writing</li>
 *   <li>Configurable field selection via {@link SystemConfig.AuditConfig}</li>
 * </ul>
 *
 * @author Ari Patriana
 * @since 3.0.0
 */
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);
    private static final Logger auditLog = LoggerFactory.getLogger("audit");

    private final boolean enabled;
    private final Set<String> fields;
    private final PciMaskUtil pciMaskUtil;

    /**
     * Creates a new AuditLogger.
     *
     * @param config      audit configuration (enabled flag + field list)
     * @param pciMaskUtil PCI masking utility
     */
    public AuditLogger(SystemConfig.AuditConfig config, PciMaskUtil pciMaskUtil) {
        this.enabled = config.enabled();
        this.fields = Set.copyOf(config.fields());
        this.pciMaskUtil = pciMaskUtil;

        if (enabled) {
            log.info("Audit logger started — routing via SLF4J logger 'audit'");
        }
    }

    /** Returns a disabled (no-op) audit logger. */
    public static AuditLogger disabled() {
        return new AuditLogger();
    }

    private AuditLogger() {
        this.enabled = false;
        this.fields = Set.of();
        this.pciMaskUtil = PciMaskUtil.disabled();
    }

    /**
     * Logs an audit entry for a processed message.
     * The entry is serialized to JSON and handed to the {@code "audit"} SLF4J
     * logger; the logging framework handles async delivery via the Disruptor.
     *
     * @param ctx message context
     */
    public void log(MessageContext ctx) {
        if (!enabled || ctx == null) return;

        Map<String, Object> entry = new LinkedHashMap<>();

        for (String field : fields) {
            switch (field) {
                case "mti"             -> entry.put("mti", ctx.field("de1"));
                case "correlation-key" -> entry.put("correlationKey", ctx.getCorrelationKey());
                case "channel"         -> entry.put("channel", ctx.getChannelName());
                case "direction"       -> entry.put("direction",
                        ctx.getDirection() != null ? ctx.getDirection().name() : null);
                case "timestamp"       -> entry.put("timestamp", Instant.now().toString());
                case "latency-ns"      -> {
                    Object receivedTime = ctx.getProperty("received_time_ns");
                    if (receivedTime instanceof Long startNs) {
                        entry.put("latencyNs", System.nanoTime() - startNs);
                    }
                }
                case "source-ip" -> {
                    if (ctx.getRemoteAddress() != null) {
                        entry.put("sourceIp", ctx.getRemoteAddress().getHostString()
                                + ":" + ctx.getRemoteAddress().getPort());
                    }
                }
                case "dest-ip" -> {
                    if (ctx.getLocalAddress() != null) {
                        entry.put("destIp", ctx.getLocalAddress().getHostString()
                                + ":" + ctx.getLocalAddress().getPort());
                    }
                }
                default -> {
                    if (field.startsWith("de")) {
                        String value = ctx.field(field);
                        if (value != null) {
                            entry.put(field, pciMaskUtil.mask(field, value));
                        }
                    }
                }
            }
        }

        auditLog.info(JsonUtil.toJson(entry));
    }
}
