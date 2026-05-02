package com.socket.edge.grpc.os;

import com.socket.edge.grpc.SystemInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Collects static host information once at startup via OSHI.
 * The result is cached and served via GetSystemInfo() RPC.
 */
public class SystemInfoCollector {

    private static final Logger log = LoggerFactory.getLogger(SystemInfoCollector.class);

    private final HardwareAbstractionLayer hal;
    private final OperatingSystem os;

    public SystemInfoCollector(oshi.SystemInfo oshi) {
        this.hal = oshi.getHardware();
        this.os  = oshi.getOperatingSystem();
    }

    public SystemInfo collect() {
        CentralProcessor cpu = hal.getProcessor();
        CentralProcessor.ProcessorIdentifier id = cpu.getProcessorIdentifier();

        long[] maxFreqs = cpu.getCurrentFreq();
        long maxFreqMhz = 0;
        if (maxFreqs != null && maxFreqs.length > 0) {
            for (long f : maxFreqs) maxFreqMhz = Math.max(maxFreqMhz, f);
            maxFreqMhz = maxFreqMhz / 1_000_000;
        }

        long totalMemory = hal.getMemory().getTotal();
        long bootTime    = os.getSystemBootTime() * 1000L; // OSHI returns seconds → millis

        String osVersion = os.getVersionInfo() != null
                ? os.getVersionInfo().toString()
                : System.getProperty("os.version", "unknown");

        log.info("SystemInfo collected: host={} os={} cpu={} cores={}/{} mem={}MB",
                resolveHostname(), os.getFamily() + " " + osVersion,
                id.getName(),
                cpu.getLogicalProcessorCount(), cpu.getPhysicalProcessorCount(),
                totalMemory / 1024 / 1024);

        return SystemInfo.newBuilder()
                .setHostname(resolveHostname())
                .setOsName(os.getFamily())
                .setOsVersion(osVersion)
                .setArchitecture(System.getProperty("os.arch", "unknown"))
                .setCpuModel(id.getName())
                .setCpuLogical(cpu.getLogicalProcessorCount())
                .setCpuPhysical(cpu.getPhysicalProcessorCount())
                .setCpuMaxFreqMhz(maxFreqMhz)
                .setTotalMemory(totalMemory)
                .setBootTime(bootTime)
                .build();
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            String env = System.getenv("HOSTNAME");
            return env != null ? env : "unknown";
        }
    }
}