package id.co.jalin.seconsole.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and verifies JWT access + refresh tokens using HS256.
 *
 * Foundation Guide 9.4 specifies RS256 as the target; Phase 1 uses HS256 with a
 * shared secret for simpler bootstrap. Migration to RS256 is an isolated change
 * inside this class (key loading + algorithm) — API and callers don't move.
 *
 * Refresh tokens are scaffolded but NOT wired into AuthController yet
 * (foundation 9.4: "Not implemented — user re-logs in after expiry").
 * Keeping the method avoids a future refactor.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private static final String ISSUER = "se-console";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final String secret;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    private SecretKey signingKey;
    private JwtParser parser;

    public JwtTokenProvider(
            @Value("${seconsole.jwt.secret}") String secret,
            @Value("${seconsole.jwt.access-ttl:PT8H}") Duration accessTtl,
            @Value("${seconsole.jwt.refresh-ttl:P7D}") Duration refreshTtl
    ) {
        this.secret = secret;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    @PostConstruct
    void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "seconsole.jwt.secret must be at least 32 bytes (256 bits); got " + keyBytes.length);
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(ISSUER)
                .build();
        log.info("JwtTokenProvider initialized: accessTtl={}, refreshTtl={}", accessTtl, refreshTtl);
    }

    // --- Issue ------------------------------------------------------------

    public record IssuedToken(String token, String jti, Instant issuedAt, Instant expiresAt) {}

    public IssuedToken issueAccessToken(String username, String role) {
        return issue(username, role, TYPE_ACCESS, accessTtl);
    }

    public IssuedToken issueRefreshToken(String username, String role) {
        return issue(username, role, TYPE_REFRESH, refreshTtl);
    }

    private IssuedToken issue(String subject, String role, String type, Duration ttl) {
        Instant now = Instant.now();
        Instant expiry = now.plus(ttl);
        String jti = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject(subject)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TOKEN_TYPE, type)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new IssuedToken(token, jti, now, expiry);
    }

    // --- Verify -----------------------------------------------------------

    public record ParsedToken(String username, String role, String jti,
                              Instant issuedAt, Instant expiresAt, String type) {
        public boolean isAccessToken() { return TYPE_ACCESS.equals(type); }
        public boolean isRefreshToken() { return TYPE_REFRESH.equals(type); }
    }

    /**
     * Parse + verify signature, issuer, and expiry. Throws JwtException on any
     * failure. Does NOT check session revocation — that's the filter's job.
     */
    public ParsedToken parseAndVerify(String token) throws JwtException {
        Jws<Claims> jws = parser.parseSignedClaims(token);
        Claims c = jws.getPayload();
        return new ParsedToken(
                c.getSubject(),
                c.get(CLAIM_ROLE, String.class),
                c.getId(),
                c.getIssuedAt().toInstant(),
                c.getExpiration().toInstant(),
                c.get(CLAIM_TOKEN_TYPE, String.class)
        );
    }

    public Duration getAccessTtl() { return accessTtl; }
    public Duration getRefreshTtl() { return refreshTtl; }
}
