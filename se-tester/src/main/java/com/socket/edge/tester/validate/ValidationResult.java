package com.socket.edge.tester.validate;

import java.util.ArrayList;
import java.util.List;

public class ValidationResult {

    public enum FileType { TEST_CASE, SUITE, COLLECTION, KEYWORD, UNKNOWN }

    private final String filePath;
    private FileType fileType = FileType.UNKNOWN;
    private final List<ValidationIssue> issues = new ArrayList<>();

    public ValidationResult(String filePath) {
        this.filePath = filePath;
    }

    public void add(ValidationIssue issue)                 { issues.add(issue); }
    public void error(String msg)                          { issues.add(ValidationIssue.error(msg)); }
    public void warn(String msg)                           { issues.add(ValidationIssue.warn(msg)); }
    public void info(String msg)                           { issues.add(ValidationIssue.info(msg)); }

    public boolean isValid()       { return issues.stream().noneMatch(ValidationIssue::isError); }
    public boolean hasWarnings()   { return issues.stream().anyMatch(ValidationIssue::isWarn); }
    public long    errorCount()    { return issues.stream().filter(ValidationIssue::isError).count(); }
    public long    warnCount()     { return issues.stream().filter(ValidationIssue::isWarn).count(); }

    public String              getFilePath() { return filePath; }
    public FileType            getFileType() { return fileType; }
    public void             setFileType(FileType fileType) { this.fileType = fileType; }
    public List<ValidationIssue> getIssues() { return issues; }
}
