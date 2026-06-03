package com.socket.edge.tester.cli;

import com.socket.edge.tester.validate.ValidationIssue;
import com.socket.edge.tester.validate.ValidationResult;
import com.socket.edge.tester.validate.YamlValidator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name        = "validate",
    description = "Validate YAML files (test case, suite, collection, keyword) without executing them.",
    mixinStandardHelpOptions = true
)
public class ValidateCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "YAML file or directory to validate")
    private Path target;

    @Option(names = {"--strict"}, description = "Treat warnings as errors")
    private boolean strict;

    @Option(names = {"-q", "--quiet"}, description = "Only show errors and warnings, suppress INFO")
    private boolean quiet;

    @Override
    public Integer call() throws Exception {
        List<Path> files = collectYamlFiles(target);

        if (files.isEmpty()) {
            System.out.println("No YAML files found at: " + target);
            return 1;
        }

        YamlValidator validator = new YamlValidator();
        List<ValidationResult> results = new ArrayList<>();

        for (Path file : files) {
            ValidationResult result = validator.validate(file);
            results.add(result);
            printResult(result);
        }

        printSummary(results);

        boolean anyFailed = results.stream().anyMatch(r ->
                !r.isValid() || (strict && r.hasWarnings()));
        return anyFailed ? 1 : 0;
    }

    // =========================================================================

    private void printResult(ValidationResult r) {
        String typeTag = switch (r.getFileType()) {
            case TEST_CASE   -> "[TC]       ";
            case SUITE       -> "[SUITE]    ";
            case COLLECTION  -> "[COLLECT]  ";
            case KEYWORD     -> "[KEYWORD]  ";
            default          -> "[UNKNOWN]  ";
        };

        String statusTag = r.isValid() ? "[OK]" : "[FAIL]";
        if (strict && r.hasWarnings() && r.isValid()) statusTag = "[WARN]";

        System.out.printf("%s %s %s%n", statusTag, typeTag, r.getFilePath());

        for (ValidationIssue issue : r.getIssues()) {
            if (quiet && issue.getLevel() == ValidationIssue.Level.INFO) continue;
            String icon = switch (issue.getLevel()) {
                case ERROR -> "  [ERROR] ";
                case WARN  -> "  [WARN]  ";
                case INFO  -> "  [INFO]  ";
            };
            System.out.println(icon + issue.getMessage());
        }

        if (!r.getIssues().isEmpty()) System.out.println();
    }

    private void printSummary(List<ValidationResult> results) {
        long total   = results.size();
        long valid   = results.stream().filter(ValidationResult::isValid).count();
        long invalid = total - valid;
        long warns   = results.stream().filter(ValidationResult::hasWarnings).count();
        long errors  = results.stream().mapToLong(ValidationResult::errorCount).sum();
        long warnings= results.stream().mapToLong(ValidationResult::warnCount).sum();

        String line = "-".repeat(70);
        System.out.println(line);
        System.out.printf("Files   : %d total | %d valid | %d invalid%n", total, valid, invalid);
        System.out.printf("Issues  : %d error(s) | %d warning(s)%n", errors, warnings);
        if (strict && warns > 0)
            System.out.println("          (--strict: warnings treated as errors)");

        boolean anyFailed = invalid > 0 || (strict && warns > 0);
        System.out.println(anyFailed ? "Result  : FAILED" : "Result  : OK");
    }

    // =========================================================================

    private List<Path> collectYamlFiles(Path path) throws IOException {
        List<Path> files = new ArrayList<>();
        if (!Files.exists(path)) {
            System.err.println("Path not found: " + path);
            return files;
        }
        if (Files.isRegularFile(path)) {
            files.add(path);
            return files;
        }
        // Walk directory, collect .yaml files, skip hidden dirs and target/
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName().toString();
                if (name.startsWith(".") || name.equals("target")) return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString().toLowerCase();
                if (name.endsWith(".yaml") || name.endsWith(".yml")) files.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Path::compareTo);
        return files;
    }
}
