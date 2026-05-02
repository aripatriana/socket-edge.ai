package id.co.jalin.seconsole.config;

import id.co.jalin.seconsole.domain.ConfigVersion;
import id.co.jalin.seconsole.repository.ConfigVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * Captures the <em>baseline</em> content of every whitelisted config file on
 * the first boot that sees it.
 *
 * <p>Motivation: before this seeder existed, the first row in
 * {@code config_versions} for any file was {@code v1} — the user's first
 * Apply. That meant the file's on-disk state <em>before</em> the console ever
 * touched it was never recorded, and there was no way to "rollback to the
 * pre-console state" from the UI. Operators reasonably expected the first
 * entry in history to represent "what was there to begin with", not "my first
 * edit".
 *
 * <p>Behaviour:
 *
 * <ul>
 *   <li>For each file in {@link ConfigFileRegistry}'s whitelist that exists on
 *       disk AND has no existing version rows in the DB, insert a row with
 *       {@code version=0}, {@code author="system"}, {@code applyResult="imported"},
 *       and {@code milestone=true}.</li>
 *   <li>Idempotent — if any rows already exist for a file (even {@code v0}
 *       from a previous boot, or {@code v1..N} from a pre-seeder install),
 *       skip that file. We never rewrite history.</li>
 *   <li>Files not on disk at boot are skipped silently — they'll get a
 *       regular {@code v1} the first time a user saves content for them,
 *       which matches the pre-seeder behaviour and is the best we can do
 *       without a file to baseline from.</li>
 * </ul>
 *
 * <p>Interaction with {@link ConfigService#apply}: no code change needed in
 * apply. Apply computes {@code newVersion = findMaxVersion().orElse(0) + 1},
 * so with a baseline present the first user Apply becomes {@code v1}, with
 * no baseline it's still {@code v1}. Backward compatible on both sides.
 *
 * <p>Ordering: {@code @Order(100)} so this runs after Flyway (which Spring
 * Boot auto-runs before any {@link ApplicationRunner}) but well ahead of
 * anything that reads version history. Scheduled services in this app use
 * {@code @Scheduled} with {@code initialDelay=0}, not runners, so there's
 * no race to worry about.
 */
@Component
@Order(100)
public class ConfigBaselineSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfigBaselineSeeder.class);

    private static final String APPLY_RESULT_IMPORTED = "imported";
    private static final String BASELINE_AUTHOR = "system";
    private static final String BASELINE_DESCRIPTION = "Baseline imported from disk";

    private final ConfigFileRegistry registry;
    private final ConfigVersionRepository versionRepository;

    public ConfigBaselineSeeder(ConfigFileRegistry registry,
                                ConfigVersionRepository versionRepository) {
        this.registry = registry;
        this.versionRepository = versionRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (ConfigFileRegistry.FileMetadata meta : registry.list()) {
            try {
                seedOne(meta);
            } catch (Exception ex) {
                // Per-file isolation: one unreadable file must not stop the
                // others. Log and continue — the UI will simply show no
                // baseline for that file, and the first user Apply will
                // become v1 as before.
                log.warn("Baseline seed failed for {}: {}", meta.name(), ex.toString());
            }
        }
    }

    private void seedOne(ConfigFileRegistry.FileMetadata meta) throws java.io.IOException {
        String fileName = meta.name();

        if (!meta.exists()) {
            log.debug("Skipping baseline for {} — file not on disk at {}", fileName, meta.absolutePath());
            return;
        }

        // Idempotency: any version row at all means we've seen this file
        // before (either from a prior boot's seeder run, or from pre-seeder
        // user applies). Either way, do not rewrite history.
        if (versionRepository.findMaxVersion(fileName).isPresent()) {
            log.debug("Skipping baseline for {} — history already present", fileName);
            return;
        }

        Path path = Paths.get(meta.absolutePath());
        String content = Files.readString(path, StandardCharsets.UTF_8);
        String sha = sha256(content);

        ConfigVersion baseline = new ConfigVersion(
                fileName,
                0,                          // version
                content,
                sha,
                null,                       // authorUserId — system-initiated, no user
                BASELINE_AUTHOR,            // authorUsername
                BASELINE_DESCRIPTION,
                null,                       // reason
                Instant.now(),              // appliedAt — boot time, not file mtime; see note below
                0,                          // applyDurationMs — not an "apply" per se
                APPLY_RESULT_IMPORTED,
                null,                       // reloadImpactJson
                null,                       // rolledBackFromVersion
                true                        // milestone — visually distinct in timeline
        );
        versionRepository.save(baseline);

        log.info("Baseline seeded for {} (v0, {} bytes, sha={}...)", fileName, content.length(),
                sha.substring(0, Math.min(12, sha.length())));

        // Note on appliedAt: we deliberately use Instant.now() rather than
        // Files.getLastModifiedTime(). The baseline row means "first time
        // the console observed this file", and that's a boot event. The
        // file's mtime may be arbitrarily old (or, on some filesystems,
        // reset by unrelated ops like a restore), and reporting that as
        // the baseline's "applied at" would be misleading.
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
            // SHA-256 is a standard algorithm — this branch is effectively
            // unreachable on any JVM we'd run on, but we fail soft rather
            // than block boot over it.
            return "sha-unavailable";
        }
    }
}
