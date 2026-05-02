package id.co.jalin.seconsole.controller;

import id.co.jalin.seconsole.service.AuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Consistent error envelope per Foundation 8.1: { error, code, details?, timestamp }.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, Object>> handleAuth(AuthException e) {
        HttpStatus status = switch (e) {
            case AuthException.AccountDisabled ad -> HttpStatus.FORBIDDEN;
            case AuthException.AccountLocked al -> HttpStatus.FORBIDDEN;
            case AuthException.BadCredentials bc -> HttpStatus.UNAUTHORIZED;
            case AuthException.SamePassword sp -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.UNAUTHORIZED;  // defensive; sealed hierarchy should cover all
        };
        return ResponseEntity.status(status).body(envelope(e.getMessage(), e.getCode(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = e.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                f -> f.getField(),
                f -> f.getDefaultMessage() == null ? "invalid" : f.getDefaultMessage(),
                (a, b) -> a
            ));
        return ResponseEntity.badRequest().body(envelope("Validation failed", "VALIDATION_ERROR", fieldErrors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(envelope(e.getMessage(), "BAD_REQUEST", null));
    }

    /**
     * Browser-initiated requests to well-known paths like
     * /.well-known/appspecific/com.chrome.devtools.json are returned as 404 silently
     * without logging, since they're not bugs and would otherwise spam the log
     * every time someone opens DevTools.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(envelope("Not found", "NOT_FOUND", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAny(Exception e) {
        log.error("Unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(envelope("Internal server error", "INTERNAL_ERROR", null));
    }

    private static Map<String, Object> envelope(String message, String code, Object details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        body.put("code", code);
        if (details != null) body.put("details", details);
        body.put("timestamp", Instant.now().toString());
        return body;
    }
}
