package id.co.jalin.seconsole.engine;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the SE-Console → engine connection.
 *
 * <p>Binds {@code seconsole.engine.*} from {@code application.yml}. Defaults
 * target the engine's {@code NettyHttpServer} on the loopback interface with
 * authentication disabled (matching the engine's {@code auth.mode=none}
 * out-of-the-box default).
 */
@Validated
@ConfigurationProperties(prefix = "seconsole.engine")
public class EngineProperties {

    /** Feature flag — when false, all engine pollers are short-circuited and the controller returns empty data. */
    private boolean enabled = true;

    /** Engine NettyHttpServer base URL. Matches {@code system.server.http.port=9001} default. */
    @NotBlank
    private String baseUrl = "http://127.0.0.1:9001";

    @Min(500)
    private int connectTimeoutMs = 2000;

    @Min(1000)
    private int readTimeoutMs = 5000;

    private Auth auth = new Auth();
    private Health health = new Health();
    private Metrics metrics = new Metrics();
    private Config config = new Config();

    public static class Auth {
        /** Toggle Basic Auth on outgoing requests. Leave false for {@code auth.mode=none}. */
        private boolean enabled = false;
        private String username = "admin";
        private String password = "changeit";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class Health {
        /** {@code /healthcheck} poll cadence. Drives the topbar engine badge. */
        @Min(1000)
        private int pollIntervalMs = 5000;

        public int getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(int pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
    }

    public static class Metrics {
        /** Socket status/metrics/queues poll cadence — the main refresh rate of the channels page. */
        @Min(500)
        private int pollIntervalMs = 2000;

        public int getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(int pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
    }

    public static class Config {
        /** {@code /config/channels} poll cadence. Slow — channel config changes rarely. */
        @Min(5000)
        private int pollIntervalMs = 30_000;

        public int getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(int pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    public Auth getAuth() { return auth; }
    public void setAuth(Auth auth) { this.auth = auth; }
    public Health getHealth() { return health; }
    public void setHealth(Health health) { this.health = health; }
    public Metrics getMetrics() { return metrics; }
    public void setMetrics(Metrics metrics) { this.metrics = metrics; }
    public Config getConfig() { return config; }
    public void setConfig(Config config) { this.config = config; }
}
