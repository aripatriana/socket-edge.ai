package id.co.jalin.seconsole.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves configuration file paths against a whitelist.
 *
 * <p>Current whitelist:
 * <ul>
 *   <li>{@code channel.conf} — custom format, validated on engine reload</li>
 *   <li>{@code system.conf}  — HOCON, validated by FE (TypeScript) before apply</li>
 *   <li>{@code cluster.conf} — HOCON, validated by FE (TypeScript) before apply</li>
 * </ul>
 *
 * <p>The validation split (channel → engine, system/cluster → frontend) exists
 * because channel.conf uses an engine-specific DSL with no standalone parser,
 * while system.conf and cluster.conf are HOCON — for which a lightweight
 * syntax check in the browser is enough to catch typos before they reach the
 * backend. In all cases the BE {@link ConfigValidator} still runs as a
 * defensive gate before disk write, currently as a no-op (see that class's
 * javadoc for rationale).
 *
 * <p>Path is resolved against {@code seconsole.isolb.conf-dir} (default
 * {@code ./conf}) — mirrors the {@code log-dir} pattern used by
 * {@code LogsService}.
 */
@Component
public class ConfigFileRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConfigFileRegistry.class);

    private static final List<WhitelistEntry> WHITELIST = List.of(
            new WhitelistEntry("channel.conf", "Core"),
            new WhitelistEntry("system.conf",  "Core"),
            new WhitelistEntry("cluster.conf", "Core")
    );

    @Value("${seconsole.isolb.conf-dir:./conf}")
    private String confDir;

    /** Resolve a whitelisted file name to an absolute path. Empty if not allowed. */
    public Optional<Path> resolve(String fileName) {
        if (fileName == null || fileName.isBlank()) return Optional.empty();
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            return Optional.empty();
        }
        for (WhitelistEntry e : WHITELIST) {
            if (e.name.equals(fileName)) {
                return Optional.of(Paths.get(confDir, fileName).toAbsolutePath().normalize());
            }
        }
        return Optional.empty();
    }

    public String sectionOf(String fileName) {
        for (WhitelistEntry e : WHITELIST) {
            if (e.name.equals(fileName)) return e.section;
        }
        return "Other";
    }

    /**
     * List whitelisted files with metadata. Files not on disk are still
     * returned so the UI has a row to render — the {@code exists} flag
     * tells the frontend whether to show a "missing" badge.
     */
    public List<FileMetadata> list() {
        List<FileMetadata> out = new ArrayList<>(WHITELIST.size());
        for (WhitelistEntry e : WHITELIST) {
            Path p = Paths.get(confDir, e.name).toAbsolutePath().normalize();
            long size = 0;
            long lastModified = 0;
            boolean exists = Files.exists(p);
            if (exists) {
                try {
                    size = Files.size(p);
                    lastModified = Files.getLastModifiedTime(p).toMillis();
                } catch (IOException ex) {
                    log.warn("Failed to stat config file {}: {}", p, ex.getMessage());
                }
            } else {
                log.debug("Whitelisted config file not on disk: {}", p);
            }
            out.add(new FileMetadata(e.name, p.toString(), e.section, size, lastModified, exists));
        }
        return out;
    }

    /** Diagnostic snapshot — useful for ops troubleshooting. */
    public Map<String, Object> diagnostic() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("confDir", confDir);
        out.put("absoluteConfDir", Paths.get(confDir).toAbsolutePath().normalize().toString());
        out.put("whitelist", WHITELIST.stream().map(e -> e.name).toList());
        out.put("files", list());
        return out;
    }

    // --- data ---------------------------------------------------------------

    private record WhitelistEntry(String name, String section) {}

    public record FileMetadata(
            String name,
            String absolutePath,
            String section,
            long size,
            long lastModified,
            boolean exists
    ) {}
}
