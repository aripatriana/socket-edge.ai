package com.socket.edge.tester.validate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.socket.edge.tester.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Validates SE Tester YAML files without executing them.
 *
 * Auto-detects file type from YAML keys:
 *   testCases  → TestSuite
 *   suites     → SuiteCollection
 *   steps + setup → TestCase
 *   steps (no setup) → Keyword
 */
public class YamlValidator {

    private static final Pattern DE_PATTERN = Pattern.compile("^DE(\\d{1,3})$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> VALID_ACTIONS = Set.of("SEND", "WAIT", "PAUSE", "LOG", "CALL");

    private final ObjectMapper mapper;

    public YamlValidator() {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.READ_ENUMS_USING_TO_STRING, false);
    }

    // =========================================================================
    // Entry point
    // =========================================================================

    public ValidationResult validate(Path yamlPath) {
        ValidationResult result = new ValidationResult(yamlPath.toString());

        if (!Files.exists(yamlPath)) {
            result.error("File not found: " + yamlPath);
            return result;
        }

        Map<String, Object> raw;
        try {
            raw = loadRaw(yamlPath);
        } catch (Exception e) {
            result.error("YAML parse error: " + e.getMessage());
            return result;
        }

        ValidationResult.FileType type = detectType(raw);
        result.setFileType(type);

        try {
            switch (type) {
                case TEST_CASE   -> validateTestCase(result, yamlPath);
                case SUITE       -> validateSuite(result, yamlPath);
                case COLLECTION  -> validateCollection(result, yamlPath);
                case KEYWORD     -> validateKeyword(result, yamlPath);
                default          -> result.warn("Cannot determine file type (missing: steps/testCases/suites)");
            }
        } catch (Exception e) {
            result.error("Validation error: " + e.getMessage());
        }

        return result;
    }

    // =========================================================================
    // TestCase
    // =========================================================================

    private void validateTestCase(ValidationResult r, Path path) throws IOException {
        TestCase tc = mapper.readValue(path.toFile(), TestCase.class);
        Path baseDir = path.toAbsolutePath().getParent();

        // Required fields
        if (blank(tc.getName()))
            r.error("Missing required field: name");
        else
            r.info("name: " + tc.getName());

        // Setup — checked before steps so port errors show even on incomplete TCs
        if (tc.getSetup() != null) {
            TestCase.ConnectConfig cc = tc.getSetup().getConnect();
            if (cc != null) {
                if (cc.getPort() <= 0 || cc.getPort() > 65535)
                    r.error("setup.connect.port out of range: " + cc.getPort());
                if (blank(cc.getHost()))
                    r.warn("setup.connect.host is blank");
            }
            TestCase.ServerConfig sc = tc.getSetup().getServer();
            if (sc != null && (sc.getPort() <= 0 || sc.getPort() > 65535))
                r.error("setup.server.port out of range: " + sc.getPort());
        }

        if (tc.getSteps() == null || tc.getSteps().isEmpty()) {
            r.error("Missing required field: steps (must have at least one step)");
            return;
        }
        r.info(tc.getSteps().size() + " step(s)");

        // dataFile
        if (tc.getDataFile() != null) {
            Path csv = baseDir.resolve(tc.getDataFile()).normalize();
            if (!Files.exists(csv))
                r.error("dataFile not found: " + tc.getDataFile() + "  (resolved: " + csv + ")");
            else
                r.info("dataFile: " + tc.getDataFile() + " [exists]");
        }

        // Steps
        Set<String> stepIds = new LinkedHashSet<>();
        for (int i = 0; i < tc.getSteps().size(); i++) {
            TestStep step = tc.getSteps().get(i);
            String prefix = "step[" + (i + 1) + "]";

            if (blank(step.getId())) {
                r.error(prefix + " missing 'id'");
            } else {
                prefix = "step '" + step.getId() + "'";
                if (!stepIds.add(step.getId()))
                    r.error("Duplicate step id: '" + step.getId() + "'");
            }

            if (step.getAction() == null) {
                r.error(prefix + " missing 'action'");
                continue;
            }

            switch (step.getAction()) {
                case SEND -> validateSendStep(r, step, prefix, baseDir);
                case CALL -> validateCallStep(r, step, prefix, baseDir);
                case WAIT, PAUSE -> {
                    Long ms = step.getAction() == TestStep.Action.WAIT ? step.getWaitMs() : step.getPauseMs();
                    if (ms == null) r.warn(prefix + " " + step.getAction() + " missing wait/pauseMs (defaults to 0)");
                }
                case LOG -> {
                    if (blank(step.getLogMessage()))
                        r.warn(prefix + " LOG missing 'logMessage'");
                }
            }
        }
    }

    private void validateSendStep(ValidationResult r, TestStep step, String prefix, Path baseDir) {
        if (step.getMessage() == null) {
            r.error(prefix + " SEND missing 'message'");
            return;
        }
        if (blank(step.getMessage().getMti()))
            r.error(prefix + " SEND missing 'message.mti'");
        else if (!step.getMessage().getMti().matches("\\d{4}|\\{\\{.*\\}\\}"))
            r.warn(prefix + " MTI '" + step.getMessage().getMti() + "' looks unusual (expected 4 digits)");

        if (step.getMessage().getFields() == null || step.getMessage().getFields().isEmpty())
            r.warn(prefix + " SEND has no fields defined");
        else {
            for (String key : step.getMessage().getFields().keySet()) {
                if (!DE_PATTERN.matcher(key).matches())
                    r.warn(prefix + " field key '" + key + "' — expected format DEnn (e.g. DE2, DE39)");
            }
        }

        // Latency SLA sanity
        if (step.getLatencySla() != null) {
            LatencySla sla = step.getLatencySla();
            if (sla.getWarn() > 0 && sla.getFail() > 0 && sla.getWarn() >= sla.getFail())
                r.warn(prefix + " latencySla.warn (" + sla.getWarn() + ") should be < fail (" + sla.getFail() + ")");
        }
    }

    private void validateCallStep(ValidationResult r, TestStep step, String prefix, Path baseDir) {
        if (blank(step.getKeyword())) {
            r.error(prefix + " CALL missing 'keyword'");
            return;
        }
        // Resolve keyword path
        String kw = step.getKeyword();
        Path kwPath = kw.endsWith(".yaml") || kw.endsWith(".yml")
                ? baseDir.resolve(kw).normalize()
                : baseDir.resolve("keywords").resolve(kw + ".yaml").normalize();

        if (!Files.exists(kwPath))
            r.error(prefix + " CALL keyword not found: '" + kw + "'  (resolved: " + kwPath + ")");
        else
            r.info(prefix + " CALL keyword '" + kw + "' [exists]");
    }

    // =========================================================================
    // TestSuite
    // =========================================================================

    private void validateSuite(ValidationResult r, Path path) throws IOException {
        TestSuite suite = mapper.readValue(path.toFile(), TestSuite.class);
        Path baseDir = path.toAbsolutePath().getParent();

        if (blank(suite.getName()))
            r.error("Missing required field: name");
        else
            r.info("name: " + suite.getName());

        if (suite.getTestCases() == null || suite.getTestCases().isEmpty()) {
            r.error("Missing required field: testCases");
            return;
        }
        r.info(suite.getTestCases().size() + " test case(s)");

        Set<String> seen = new LinkedHashSet<>();
        for (String tcPath : suite.getTestCases()) {
            if (!seen.add(tcPath))
                r.warn("Duplicate testCase entry: " + tcPath);
            Path resolved = baseDir.resolve(tcPath).normalize();
            if (!Files.exists(resolved))
                r.error("testCase file not found: " + tcPath + "  (resolved: " + resolved + ")");
            else
                r.info("  " + tcPath + " [exists]");
        }
    }

    // =========================================================================
    // SuiteCollection
    // =========================================================================

    private void validateCollection(ValidationResult r, Path path) throws IOException {
        SuiteCollection col = mapper.readValue(path.toFile(), SuiteCollection.class);
        Path baseDir = path.toAbsolutePath().getParent();

        if (blank(col.getName()))
            r.error("Missing required field: name");
        else
            r.info("name: " + col.getName());

        if (col.getSuites() == null || col.getSuites().isEmpty()) {
            r.error("Missing required field: suites");
            return;
        }
        r.info(col.getSuites().size() + " suite(s)");

        Set<String> seen = new LinkedHashSet<>();
        for (String suitePath : col.getSuites()) {
            if (!seen.add(suitePath))
                r.warn("Duplicate suite entry: " + suitePath);
            Path resolved = baseDir.resolve(suitePath).normalize();
            if (!Files.exists(resolved))
                r.error("Suite file not found: " + suitePath + "  (resolved: " + resolved + ")");
            else
                r.info("  " + suitePath + " [exists]");
        }
    }

    // =========================================================================
    // Keyword
    // =========================================================================

    private void validateKeyword(ValidationResult r, Path path) throws IOException {
        Keyword kw = mapper.readValue(path.toFile(), Keyword.class);

        if (blank(kw.getName()))
            r.error("Missing required field: name");
        else
            r.info("name: " + kw.getName());

        if (kw.getSteps() == null || kw.getSteps().isEmpty()) {
            r.error("Missing required field: steps");
            return;
        }
        r.info(kw.getSteps().size() + " step(s)");

        if (kw.getParameters() != null && !kw.getParameters().isEmpty())
            r.info("parameters: " + String.join(", ", kw.getParameters().keySet()));

        Set<String> stepIds = new LinkedHashSet<>();
        for (int i = 0; i < kw.getSteps().size(); i++) {
            TestStep step = kw.getSteps().get(i);
            String prefix = "step[" + (i + 1) + "]";
            if (blank(step.getId())) {
                r.error(prefix + " missing 'id'");
            } else {
                prefix = "step '" + step.getId() + "'";
                if (!stepIds.add(step.getId()))
                    r.error("Duplicate step id: '" + step.getId() + "'");
            }
            if (step.getAction() == TestStep.Action.SEND)
                validateSendStep(r, step, prefix, path.getParent());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadRaw(Path path) throws IOException {
        return mapper.readValue(path.toFile(), Map.class);
    }

    private ValidationResult.FileType detectType(Map<String, Object> raw) {
        if (raw.containsKey("testCases"))                           return ValidationResult.FileType.SUITE;
        if (raw.containsKey("suites"))                              return ValidationResult.FileType.COLLECTION;
        if (raw.containsKey("steps") && raw.containsKey("setup"))  return ValidationResult.FileType.TEST_CASE;
        if (raw.containsKey("setup"))                               return ValidationResult.FileType.TEST_CASE;
        if (raw.containsKey("steps"))                               return ValidationResult.FileType.KEYWORD;
        return ValidationResult.FileType.UNKNOWN;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
