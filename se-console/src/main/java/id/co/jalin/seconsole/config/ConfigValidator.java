package id.co.jalin.seconsole.config;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * No-op validator. Per product decision, syntax validation is deferred to
 * the engine — which parses the file on reload and reports errors via its
 * own logs. This class is kept as a dedicated bean so that:
 *
 * <ol>
 *   <li>Controllers and services have a stable injection point — when real
 *       validation arrives, we swap the implementation without touching any
 *       caller.</li>
 *   <li>The {@code /api/config/validate} endpoint has consistent shape
 *       with or without real validation enabled.</li>
 * </ol>
 *
 * <p>When ready to switch on real validation (Typesafe Config / HOCON
 * parser), replace {@link #validate(String, String)} implementation. The
 * {@link Result} / {@link Problem} shape is already rich enough to carry
 * positional errors + warnings.
 */
@Component
public class ConfigValidator {

    /** One problem found during validation. */
    public record Problem(int line, int column, String message, String severity, String rule) {
        public static Problem error(int line, int col, String msg, String rule) {
            return new Problem(line, col, msg, "error", rule);
        }
        public static Problem warning(int line, int col, String msg, String rule) {
            return new Problem(line, col, msg, "warning", rule);
        }
    }

    public record Result(boolean valid, List<Problem> errors, List<Problem> warnings) {
        public static Result ok() {
            return new Result(true, List.of(), List.of());
        }
    }

    /**
     * Always returns {@link Result#ok()}. The engine is the source of
     * truth for config correctness — let it complain on reload.
     */
    public Result validate(String fileName, String content) {
        return Result.ok();
    }
}
