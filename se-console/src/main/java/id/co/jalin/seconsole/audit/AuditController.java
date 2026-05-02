package id.co.jalin.seconsole.audit;

import id.co.jalin.seconsole.domain.AuditEntry;
import id.co.jalin.seconsole.repository.AuditEntryRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Audit Trail read API. ADMIN-only — audit content includes failed login
 * usernames and target IDs, which is information the other roles don't
 * need to see.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/audit} — paginated JSON listing with filters</li>
 *   <li>{@code GET /api/audit/export} — CSV stream of the same query</li>
 *   <li>{@code GET /api/audit/facets} — distinct action + target_type values
 *       for filter dropdown population</li>
 * </ul>
 *
 * <p>Filters are all optional. Every filter omitted = "all entries".
 * Date range uses ISO-8601 instants ({@code 2026-04-22T00:00:00Z}).
 *
 * <p><b>Why include an export endpoint at all?</b> Foundation Guide 8.2
 * lists it as a requirement, and in practice auditors frequently want to
 * ingest the log into their own tooling (Excel, Splunk, etc.) rather
 * than browse our UI. CSV is a reasonable lingua franca.
 */
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private static final Logger log = LoggerFactory.getLogger(AuditController.class);

    /** Safety cap on export — a CSV of more than this many rows is not useful and risks OOM. */
    private static final int EXPORT_MAX_ROWS = 50_000;

    /** Default page size for list endpoint. */
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private final AuditEntryRepository repository;

    public AuditController(AuditEntryRepository repository) {
        this.repository = repository;
    }

    // -----------------------------------------------------------------------
    // GET /api/audit — paginated list
    // -----------------------------------------------------------------------

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Instant fromTs = parseInstantOrNull(from);
        Instant toTs = parseInstantOrNull(to);
        String normalizedAction = emptyToNull(action);
        String normalizedResult = emptyToNull(result);
        String normalizedTargetType = emptyToNull(targetType);
        String normalizedUsername = emptyToNull(username);

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<AuditEntry> pageResult = repository.findFiltered(
                fromTs, toTs, normalizedAction, normalizedResult,
                normalizedTargetType, normalizedUsername, pageable);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("page", pageResult.getNumber());
        body.put("size", pageResult.getSize());
        body.put("totalElements", pageResult.getTotalElements());
        body.put("totalPages", pageResult.getTotalPages());
        body.put("entries", pageResult.getContent().stream().map(AuditController::toDto).toList());
        return ResponseEntity.ok(body);
    }

    // -----------------------------------------------------------------------
    // GET /api/audit/facets — dropdown population
    // -----------------------------------------------------------------------

    @GetMapping("/facets")
    public ResponseEntity<?> facets() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("actions", repository.findDistinctActions());
        body.put("targetTypes", repository.findDistinctTargetTypes());
        body.put("results", List.of("success", "failed"));
        return ResponseEntity.ok(body);
    }

    // -----------------------------------------------------------------------
    // GET /api/audit/export — CSV stream
    // -----------------------------------------------------------------------

    @GetMapping(value = "/export", produces = "text/csv")
    public void export(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String username,
            HttpServletResponse response
    ) throws IOException {
        Instant fromTs = parseInstantOrNull(from);
        Instant toTs = parseInstantOrNull(to);

        // Cap rows to prevent server OOM on an unbounded filter.
        Pageable cap = PageRequest.of(0, EXPORT_MAX_ROWS);
        List<AuditEntry> rows = repository.findFilteredForExport(
                fromTs, toTs, emptyToNull(action), emptyToNull(result),
                emptyToNull(targetType), emptyToNull(username), cap);

        String filename = "audit-" + Instant.now().toString().replace(':', '-') + ".csv";
        response.setContentType("text/csv; charset=utf-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        // UTF-8 BOM so Excel opens the file with the right encoding by default.
        // Without this, non-ASCII usernames/details display as mojibake in Excel.
        response.getOutputStream().write(0xEF);
        response.getOutputStream().write(0xBB);
        response.getOutputStream().write(0xBF);

        try (PrintWriter out = new PrintWriter(response.getOutputStream(), false, StandardCharsets.UTF_8)) {
            writeCsvHeader(out);
            for (AuditEntry e : rows) {
                writeCsvRow(out, e);
            }
            out.flush();
        }

        log.info("Audit CSV export: {} rows for filter from={} to={} action={} result={} username={}",
                rows.size(), fromTs, toTs, action, result, username);
    }

    // -----------------------------------------------------------------------
    // DTO + CSV helpers
    // -----------------------------------------------------------------------

    private static Map<String, Object> toDto(AuditEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("eventTime", e.getEventTime() == null ? null : e.getEventTime().toEpochMilli());
        m.put("userId", e.getUserId());
        m.put("username", e.getUsername());
        m.put("action", e.getAction());
        m.put("targetType", e.getTargetType());
        m.put("targetId", e.getTargetId());
        m.put("result", e.getResult());
        m.put("detailsJson", e.getDetailsJson());
        m.put("sourceIp", e.getSourceIp());
        m.put("userAgent", e.getUserAgent());
        return m;
    }

    private static void writeCsvHeader(PrintWriter out) {
        out.println("id,event_time,username,user_id,action,target_type,target_id,result,source_ip,user_agent,details_json");
    }

    private static final DateTimeFormatter CSV_TS_FMT =
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private static void writeCsvRow(PrintWriter out, AuditEntry e) {
        out.print(e.getId());
        out.print(',');
        out.print(e.getEventTime() == null ? "" : csvField(CSV_TS_FMT.format(e.getEventTime())));
        out.print(',');
        out.print(csvField(e.getUsername()));
        out.print(',');
        out.print(e.getUserId() == null ? "" : e.getUserId());
        out.print(',');
        out.print(csvField(e.getAction()));
        out.print(',');
        out.print(csvField(e.getTargetType()));
        out.print(',');
        out.print(csvField(e.getTargetId()));
        out.print(',');
        out.print(csvField(e.getResult()));
        out.print(',');
        out.print(csvField(e.getSourceIp()));
        out.print(',');
        out.print(csvField(e.getUserAgent()));
        out.print(',');
        out.print(csvField(e.getDetailsJson()));
        out.println();
    }

    /**
     * CSV field escaping per RFC 4180: wrap in double-quotes if the value
     * contains comma, double-quote, CR, or LF; double up internal quotes.
     * Null is written as an empty field (no quotes).
     */
    private static String csvField(String s) {
        if (s == null) return "";
        boolean needsQuoting = s.indexOf(',') >= 0
                || s.indexOf('"') >= 0
                || s.indexOf('\n') >= 0
                || s.indexOf('\r') >= 0;
        if (!needsQuoting) return s;
        StringBuilder sb = new StringBuilder(s.length() + 4);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') sb.append('"').append('"');
            else sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Parameter parsing
    // -----------------------------------------------------------------------

    /**
     * Accepts ISO-8601 instants ({@code 2026-04-22T00:00:00Z}) and also
     * bare dates ({@code 2026-04-22}), which we treat as start-of-day UTC
     * — the UI's date picker emits bare dates and we don't want to force
     * the FE to bolt on {@code T00:00:00Z} before every request.
     */
    private static Instant parseInstantOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim();
        try {
            return Instant.parse(v);
        } catch (Exception ignoreAndTryDate) {
            // fall through
        }
        try {
            return java.time.LocalDate.parse(v).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception ex) {
            // Bad input — treat as absent rather than 400ing. The audit UI
            // drives this endpoint; a typo shouldn't become a page-level error.
            log.debug("Unparseable timestamp '{}' in audit filter, treating as null", raw);
            return null;
        }
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
