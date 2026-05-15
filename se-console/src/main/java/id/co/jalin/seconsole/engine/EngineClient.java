package id.co.jalin.seconsole.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.co.jalin.seconsole.engine.model.ChannelCfg;
import id.co.jalin.seconsole.engine.model.ChannelSnapshot;
import id.co.jalin.seconsole.engine.model.EngineHealth;
import id.co.jalin.seconsole.engine.model.JvmSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * Thin HTTP wrapper over the engine's {@code NettyHttpServer} REST API.
 *
 * <p>Chat 3c-1d: adds {@link #postSocketAction(String, String, String)} to
 * forward control actions (start/stop/restart). Monitoring methods from
 * Chat 3c-1a remain unchanged.
 */
@Component
@EnableConfigurationProperties(EngineProperties.class)
public class EngineClient {

    private static final Logger log = LoggerFactory.getLogger(EngineClient.class);

    private final EngineProperties props;
    private final ObjectMapper mapper;
    private final RestClient restClient;

    public EngineClient(EngineProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.restClient = buildRestClient(props);
        log.info("EngineClient configured: baseUrl={} authEnabled={} connectMs={} readMs={}",
                props.getBaseUrl(), props.getAuth().isEnabled(),
                props.getConnectTimeoutMs(), props.getReadTimeoutMs());
    }

    private static RestClient buildRestClient(EngineProperties props) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()));
        rf.setReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()));
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(rf)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // =========================================================================
    // Monitoring
    // =========================================================================

    public EngineHealth getHealth() {
        return getJsonUnwrapped("/healthcheck", new TypeReference<EngineResponse<EngineHealth>>() {});
    }

    /**
     * Fetch the engine's channel snapshot — the single source for runtime,
     * queue, and metrics data across all sockets. Replaces the pre-rewrite
     * trio of {@code /socket/status}, {@code /socket/metrics},
     * {@code /socket/queues}. Response is NOT enveloped (spec-channel-snapshot.md).
     */
    public ChannelSnapshot getChannelSnapshot() {
        return getJsonRaw("/socket/snapshot/channels", ChannelSnapshot.class);
    }

    public List<ChannelCfg> getChannels() {
        List<ChannelCfg> out = getJsonUnwrapped(
                "/config/channels",
                new TypeReference<EngineResponse<List<ChannelCfg>>>() {});
        return out == null ? Collections.emptyList() : out;
    }

    /**
     * Fetch the engine's JVM snapshot. Unlike the other monitoring endpoints,
     * this one does NOT use the {@code {status,result,message}} envelope —
     * the response is the snapshot object directly (see {@code spec-jvm-snapshot.md}).
     *
     * @param details when true, asks for the per-thread {@code threadDetails}
     *                array. Caller should set this to true only for on-demand
     *                "Threads" tab loads; default polling uses false.
     */
    public JvmSnapshot getJvmSnapshot(boolean details) {
        String path = details ? "/socket/snapshot/jvm?details=true" : "/socket/snapshot/jvm";
        return getJsonRaw(path, JvmSnapshot.class);
    }


    // =========================================================================
    // Control actions — Chat 3c-1d
    // =========================================================================

    /**
     * Invoke a control action on the engine.
     *
     * @param action    "start", "stop", or "restart"
     * @param paramName "id" for per-socket, "name" for channel-level
     * @param paramValue bindingId or channel name
     * @return engine's {@code result} message (may be null or empty)
     * @throws EngineClientException on transport or envelope failure
     */
    public String postSocketAction(String action, String paramName, String paramValue) {
        if (!"start".equals(action) && !"stop".equals(action) && !"restart".equals(action)) {
            throw new IllegalArgumentException("Unsupported action: " + action);
        }
        if (!"id".equals(paramName) && !"name".equals(paramName)) {
            throw new IllegalArgumentException("Unsupported param: " + paramName);
        }
        String path = "/socket/" + action + "?" + paramName + "=" + encode(paramValue);
        String body;
        try {
            RestClient.RequestBodySpec spec = restClient.post().uri(path);
            if (props.getAuth().isEnabled()) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, basicAuthHeader());
            }
            body = spec.retrieve().body(String.class);
        } catch (RestClientException ex) {
            throw new EngineClientException("Transport error calling POST " + path + ": " + ex.getMessage(), ex);
        }

        if (body == null || body.isEmpty()) {
            throw new EngineClientException("Empty response from POST " + path);
        }

        EngineResponse<Object> envelope;
        try {
            envelope = mapper.readValue(body, new TypeReference<EngineResponse<Object>>() {});
        } catch (Exception ex) {
            throw new EngineClientException("Failed to parse response from POST " + path + ": " + ex.getMessage(), ex);
        }

        if (envelope == null) {
            throw new EngineClientException("Null envelope from POST " + path);
        }
        if (!envelope.ok()) {
            throw new EngineClientException(
                    "Engine returned FAILED for POST " + path + ": "
                            + envelope.messageOrDefault("no message"));
        }
        return envelope.message();
    }

    // =========================================================================
    // Config reload — Chunk B
    // =========================================================================

    /**
     * Tell the engine to re-read its configuration. Returns the engine's
     * {@code result} message (empty string when the engine doesn't include one).
     *
     * <p>Engine endpoint: {@code POST /config/reload}. No body, no path params.
     * Engine re-reads all config files and returns a summary message.
     *
     * @throws EngineClientException on transport failure or non-ok envelope.
     */
    public String reloadConfig() {
        String path = "/config/reload";
        String body;
        try {
            RestClient.RequestBodySpec spec = restClient.post().uri(path);
            if (props.getAuth().isEnabled()) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, basicAuthHeader());
            }
            body = spec.retrieve().body(String.class);
        } catch (RestClientException ex) {
            throw new EngineClientException("Transport error calling POST " + path + ": " + ex.getMessage(), ex);
        }

        if (body == null || body.isEmpty()) {
            // Some reload endpoints respond with 200 + empty body. Treat as success.
            return "";
        }

        EngineResponse<Object> envelope;
        try {
            envelope = mapper.readValue(body, new TypeReference<EngineResponse<Object>>() {});
        } catch (Exception ex) {
            throw new EngineClientException("Failed to parse response from POST " + path + ": " + ex.getMessage(), ex);
        }

        if (envelope == null) {
            throw new EngineClientException("Null envelope from POST " + path);
        }
        if (!envelope.ok()) {
            throw new EngineClientException(
                    "Engine returned FAILED for POST " + path + ": "
                            + envelope.messageOrDefault("no message"));
        }
        return envelope.message() == null ? "" : envelope.message();
    }

    // =========================================================================
    // Internal
    // =========================================================================

    /**
     * Fetch a raw JSON body (not wrapped in {@link EngineResponse}).
     * Used for endpoints like {@code /socket/snapshot/jvm} whose response
     * is the payload object directly.
     */
    private <T> T getJsonRaw(String path, Class<T> type) {
        String body;
        try {
            RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(path);
            if (props.getAuth().isEnabled()) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, basicAuthHeader());
            }
            body = spec.retrieve().body(String.class);
        } catch (RestClientException ex) {
            throw new EngineClientException("Transport error calling " + path + ": " + ex.getMessage(), ex);
        }

        if (body == null || body.isEmpty()) {
            throw new EngineClientException("Empty response from " + path);
        }

        try {
            return mapper.readValue(body, type);
        } catch (Exception ex) {
            throw new EngineClientException("Failed to parse response from " + path + ": " + ex.getMessage(), ex);
        }
    }

    private <T> T getJsonUnwrapped(String path, TypeReference<EngineResponse<T>> typeRef) {
        String body;
        try {
            RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(path);
            if (props.getAuth().isEnabled()) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, basicAuthHeader());
            }
            body = spec.retrieve().body(String.class);
        } catch (RestClientException ex) {
            throw new EngineClientException("Transport error calling " + path + ": " + ex.getMessage(), ex);
        }

        if (body == null || body.isEmpty()) {
            throw new EngineClientException("Empty response from " + path);
        }

        EngineResponse<T> envelope;
        try {
            envelope = mapper.readValue(body, typeRef);
        } catch (Exception ex) {
            throw new EngineClientException("Failed to parse response from " + path + ": " + ex.getMessage(), ex);
        }

        if (envelope == null) {
            throw new EngineClientException("Null envelope from " + path);
        }
        if (!envelope.ok()) {
            throw new EngineClientException(
                    "Engine returned FAILED for " + path + ": "
                            + envelope.messageOrDefault("no message"));
        }
        return envelope.result();
    }

    private String basicAuthHeader() {
        String raw = props.getAuth().getUsername() + ":" + props.getAuth().getPassword();
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static class EngineClientException extends RuntimeException {
        public EngineClientException(String msg) { super(msg); }
        public EngineClientException(String msg, Throwable cause) { super(msg, cause); }
    }
}
