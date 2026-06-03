package com.socket.edge.tester.validate;

public class ValidationIssue {

    public enum Level { ERROR, WARN, INFO }

    private final Level level;
    private final String message;

    public ValidationIssue(Level level, String message) {
        this.level   = level;
        this.message = message;
    }

    public static ValidationIssue error(String msg) { return new ValidationIssue(Level.ERROR, msg); }
    public static ValidationIssue warn(String msg)  { return new ValidationIssue(Level.WARN,  msg); }
    public static ValidationIssue info(String msg)  { return new ValidationIssue(Level.INFO,  msg); }

    public Level  getLevel()   { return level;   }
    public String getMessage() { return message; }

    public boolean isError() { return level == Level.ERROR; }
    public boolean isWarn()  { return level == Level.WARN;  }
}
