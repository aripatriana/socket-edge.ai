package com.socket.edge.tester.core.iso;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {{expr}} template placeholders in field values.
 *
 * Supported expressions:
 *   stan()                         — 6-digit sequential STAN
 *   rrn()                          — 12-digit: yy(2)+doy(3)+hh(2)+mm(2)+sec(2)+seq(1)
 *   datetime()                     — MMddHHmmss (10 chars)
 *   time()                         — HHmmss (6 chars)
 *   date()                         — MMdd (4 chars)
 *   pan(prefix=4111)               — 16-digit Luhn-valid PAN
 *   amount(min=1000,max=99999)     — 12-digit zero-padded amount
 *   env.KEY                        — environment variable
 *   vars.KEY                       — scenario variable
 *   steps.STEP_ID.request.DE11    — field from a previous step's request
 *   steps.STEP_ID.response.DE39   — field from a previous step's response
 */
public final class TemplateFunctions {

    private static final AtomicInteger STAN_SEQ = new AtomicInteger(0);
    private static final Pattern EXPR = Pattern.compile("\\{\\{([^}]+?)\\}\\}");
    private static final Random RNG = new Random();

    private TemplateFunctions() {}

    public static String resolve(String template,
                                  Map<String, String> vars,
                                  Map<String, Map<String, String>> stepContext) {
        if (template == null || !template.contains("{{")) return template;

        Matcher m = EXPR.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String replacement = resolveExpr(m.group(1).trim(), vars, stepContext);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String resolveExpr(String expr,
                                       Map<String, String> vars,
                                       Map<String, Map<String, String>> stepContext) {
        if (expr.startsWith("stan()"))      return stan();
        if (expr.startsWith("rrn()"))       return rrn();
        if (expr.startsWith("datetime()"))  return datetime();
        if (expr.startsWith("time()"))      return time();
        if (expr.startsWith("date()"))      return date();
        if (expr.startsWith("pan("))        return pan(args(expr));
        if (expr.startsWith("amount("))     return amount(args(expr));
        if (expr.startsWith("env."))        return env(expr.substring(4));
        if (expr.startsWith("vars."))       return var(expr.substring(5), vars);
        if (expr.startsWith("steps."))      return step(expr.substring(6), stepContext);
        return "{{" + expr + "}}";
    }

    // -------------------------------------------------------------------------
    // Functions
    // -------------------------------------------------------------------------

    public static String stan() {
        return String.format("%06d", STAN_SEQ.incrementAndGet() % 1_000_000);
    }

    public static String rrn() {
        LocalDateTime now = LocalDateTime.now();
        int seq = STAN_SEQ.get() % 10;
        return String.format("%02d%03d%02d%02d%02d%01d",
                now.getYear() % 100,
                now.getDayOfYear(),
                now.getHour(),
                now.getMinute(),
                now.getSecond(),
                seq);
    }

    public static String datetime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss"));
    }

    public static String time() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
    }

    public static String date() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd"));
    }

    public static String pan(String argsStr) {
        String prefix = extractParam(argsStr, "prefix", "4111");
        int prefixLen = prefix.length();
        int fillLen   = 15 - prefixLen; // 15 digits + 1 Luhn check = 16
        if (fillLen < 0) fillLen = 0;

        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < fillLen; i++) sb.append(RNG.nextInt(10));
        sb.append(luhn(sb.toString()));
        return sb.toString();
    }

    public static String amount(String argsStr) {
        long min = 1000, max = 99999;
        String minStr = extractParam(argsStr, "min", null);
        String maxStr = extractParam(argsStr, "max", null);
        if (minStr != null) min = Long.parseLong(minStr);
        if (maxStr != null) max = Long.parseLong(maxStr);
        if (minStr == null && maxStr == null && argsStr != null && !argsStr.isBlank()) {
            try { min = max = Long.parseLong(argsStr.trim()); } catch (NumberFormatException ignored) {}
        }
        long value = min + (long)(RNG.nextDouble() * (max - min + 1));
        return String.format("%012d", value);
    }

    // -------------------------------------------------------------------------
    // Context lookups
    // -------------------------------------------------------------------------

    private static String env(String key) {
        String v = System.getenv(key);
        return v != null ? v : "";
    }

    private static String var(String key, Map<String, String> vars) {
        return vars != null ? vars.getOrDefault(key, "") : "";
    }

    private static String step(String expr, Map<String, Map<String, String>> ctx) {
        // expr = "STEP_ID.request.DE11"  or  "STEP_ID.response.DE39"
        int first = expr.indexOf('.');
        int second = first >= 0 ? expr.indexOf('.', first + 1) : -1;
        if (first < 0 || second < 0 || ctx == null) return "";

        String stepId  = expr.substring(0, first);
        String dir     = expr.substring(first + 1, second); // "request" or "response"
        String field   = expr.substring(second + 1);        // "DE11", "mti", etc.

        Map<String, String> map = ctx.get(stepId + "." + dir);
        return map != null ? map.getOrDefault(field, "") : "";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String args(String expr) {
        int s = expr.indexOf('('), e = expr.lastIndexOf(')');
        return (s >= 0 && e > s) ? expr.substring(s + 1, e) : "";
    }

    private static String extractParam(String argsStr, String key, String defaultVal) {
        if (argsStr == null || argsStr.isBlank()) return defaultVal;
        for (String part : argsStr.split(",")) {
            String p = part.trim();
            if (p.startsWith(key + "=")) return p.substring(key.length() + 1);
        }
        return defaultVal;
    }

    private static int luhn(String digits) {
        int sum = 0;
        boolean alt = true;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (alt) { d *= 2; if (d > 9) d -= 9; }
            sum += d;
            alt = !alt;
        }
        return (10 - sum % 10) % 10;
    }

    public static void resetStanCounter() {
        STAN_SEQ.set(0);
    }
}