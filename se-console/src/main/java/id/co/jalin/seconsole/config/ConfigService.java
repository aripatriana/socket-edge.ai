package id.co.jalin.seconsole.config;

import id.co.jalin.seconsole.domain.ConfigVersion;
import id.co.jalin.seconsole.repository.ConfigVersionRepository;
import id.co.jalin.seconsole.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;

/**
 * Read + write orchestration for config files.
 *
 * <p>Read is a straight filesystem read. Write is transactional:
 *
 * <ol>
 *   <li>Validate via {@link ConfigValidator} (defensive — frontend already
 *       validated, but never trust the client).</li>
 *   <li>Optimistic-concurrency check: {@code expectedVersion} must equal
 *       the latest version in the DB. Off by one → 409.</li>
 *   <li>Append a new row to {@code config_versions} with the NEW content,
 *       SHA, author, apply result.</li>
 *   <li>Atomic write: write new content to a temp file in the same
 *       directory, then {@code ATOMIC_MOVE} onto the target. This way a
 *       crash mid-write never leaves a half-file on disk.</li>
 *   <li>Record audit entry.</li>
 * </ol>
 *
 * <p>Engine reload is <strong>not</strong> triggered here — the engine
 * doesn't expose a reload HTTP endpoint yet (per engine doc 1.0.1, reload
 * is CLI-only via {@code jsocket.sh reload}). The controller's response
 * reports {@code engineReloadMs=0} to make this explicit to the UI.
 *
 * <p>The whole method is {@code @Transactional}. If disk write fails, the
 * config_versions INSERT rolls back, so the DB never disagrees with disk.
 */
@Service
public class ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);

    private final ConfigFileRegistry registry;
    private final ConfigValidator validator;
    private final ConfigVersionRepository versionRepository;
    private final AuditService auditService;

    public ConfigService(ConfigFileRegistry registry,
                         ConfigValidator validator,
                         ConfigVersionRepository versionRepository,
                         AuditService auditService) {
        this.registry = registry;
        this.validator = validator;
        this.versionRepository = versionRepository;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------------

    public ReadResult read(String fileName) throws IOException {
        Path path = registry.resolve(fileName).orElseThrow(
                () -> new IllegalArgumentException("File not in whitelist: " + fileName));

        if (!Files.exists(path)) {
            return new ReadResult(fileName, path.toString(), null, null, null, null, 0, 0);
        }

        String content = Files.readString(path, StandardCharsets.UTF_8);
        long size = Files.size(path);
        long lastModifiedFs = Files.getLastModifiedTime(path).toMillis();

        // Pick the most recent version row for metadata (version number, author).
        Optional<ConfigVersion> latest = versionRepository.findByFileNameOrderByVersionDesc(fileName)
                .stream().findFirst();

        Integer currentVersion = latest.map(ConfigVersion::getVersion).orElse(null);
        String lastModifiedBy = latest.map(ConfigVersion::getAuthorUsername).orElse(null);
        Long lastModifiedDb = latest.map(v -> v.getAppliedAt() == null ? null : v.getAppliedAt().toEpochMilli()).orElse(null);

        return new ReadResult(
                fileName,
                path.toString(),
                content,
                currentVersion,
                lastModifiedBy,
                lastModifiedDb != null ? lastModifiedDb : lastModifiedFs,
                size,
                lastModifiedFs
        );
    }

    // ------------------------------------------------------------------------
    // Apply
    // ------------------------------------------------------------------------

    @Transactional
    public ApplyResult apply(String fileName, String content, Integer expectedVersion) throws IOException {
        long started = System.currentTimeMillis();

        Path path = registry.resolve(fileName).orElseThrow(
                () -> new IllegalArgumentException("File not in whitelist: " + fileName));

        // 1. Validate defensively.
        ConfigValidator.Result validation = validator.validate(fileName, content);
        if (!validation.valid()) {
            return ApplyResult.validationFailed(validation);
        }

        // 2. Optimistic concurrency check.
        int currentVersion = versionRepository.findMaxVersion(fileName).orElse(0);
        if (expectedVersion != null && currentVersion != 0 && expectedVersion != currentVersion) {
            return ApplyResult.versionConflict(currentVersion, expectedVersion);
        }
        int newVersion = currentVersion + 1;

        // 3. Prepare version row (not saved yet — we save after disk write succeeds).
        String author = currentUsername();
        String sha = sha256(content);

        // 4. Atomic disk write.
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp-" + started);
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFailed) {
                // Fall back to non-atomic replace on filesystems that don't support ATOMIC_MOVE.
                log.warn("ATOMIC_MOVE not supported, falling back to REPLACE_EXISTING: {}", atomicFailed.getMessage());
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ioe) {
            // Best-effort cleanup of the tmp file.
            try { Files.deleteIfExists(tmp); } catch (IOException ignore) { /* swallow */ }
            throw ioe;
        }

        // 5. Persist version row.
        ConfigVersion row = new ConfigVersion(
                fileName,
                newVersion,
                content,
                sha,
                null,
                author,
                null,
                null,
                Instant.now(),
                (int) (System.currentTimeMillis() - started),
                "success",
                null,
                null,
                false
        );
        versionRepository.save(row);

        long totalMs = System.currentTimeMillis() - started;

        // 6. Audit.
        try {
            String details = "{\"newVersion\":" + newVersion
                    + ",\"sha\":\"" + sha + "\""
                    + ",\"totalMs\":" + totalMs + "}";
            auditService.logSuccess(null, author, "APPLY_CONFIG",
                    "config_version", fileName + "/v" + newVersion,
                    details, null, null);
        } catch (Exception auditEx) {
            log.warn("Audit write failed for apply {}: {}", fileName, auditEx.getMessage());
        }

        return ApplyResult.ok(newVersion, totalMs);
    }

    // ------------------------------------------------------------------------
    // Result records
    // ------------------------------------------------------------------------

    public record ReadResult(
            String fileName,
            String absolutePath,
            String content,
            Integer currentVersion,
            String lastModifiedBy,
            Long lastModified,
            long size,
            long lastModifiedFs
    ) {}

    public record ApplyResult(
            boolean success,
            Integer newVersion,
            Long totalMs,
            ConfigValidator.Result validationFailure,   // non-null when validation rejected
            Integer currentVersionOnConflict,            // non-null on 409
            Integer expectedVersionOnConflict            // non-null on 409
    ) {
        public static ApplyResult ok(int newVersion, long totalMs) {
            return new ApplyResult(true, newVersion, totalMs, null, null, null);
        }

        public static ApplyResult validationFailed(ConfigValidator.Result r) {
            return new ApplyResult(false, null, null, r, null, null);
        }

        public static ApplyResult versionConflict(int current, int expected) {
            return new ApplyResult(false, null, null, null, current, expected);
        }
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return "unknown";
        return auth.getName();
    }

    private static String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "sha-unavailable";
        }
    }
}
