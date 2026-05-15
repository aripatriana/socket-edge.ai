package com.socket.edge.ai.engine;

import com.socket.edge.ai.bandit.ChannelBanditModel;
import com.socket.edge.ai.bandit.EndpointScore;
import com.socket.edge.ai.bandit.SoftmaxWeightSelector;
import com.socket.edge.ai.feature.FeatureBuilder;
import com.socket.edge.ai.reward.ChannelState;
import com.socket.edge.ai.reward.RewardCalculator;
import com.socket.edge.ai.safety.SafetyConstraint;
import com.socket.edge.grpc.MetricsBundle;
import com.socket.edge.grpc.SocketSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Main LinUCB processing loop.
 *
 * Called once per MetricsBundle (each gRPC push from se-core).
 *
 * Per-channel flow:
 *   1. Extract and sort CLIENT SocketSnapshots by hash_id.
 *   2. Skip channels with ≤ 1 endpoint (no load balancing needed).
 *   3. Build normalized feature vector x (N × 15).
 *   4. If previous state exists: compute reward, update bandit model.
 *   5. Compute LinUCB score per endpoint → softmax weights.
 *   6. Apply safety constraints (DOWN endpoints, min/max bounds).
 *   7. Publish weights to se-core via gRPC.
 */
public class BanditEngine {

    private static final Logger log = LoggerFactory.getLogger(BanditEngine.class);

    private final FeatureBuilder       featureBuilder;
    private final SoftmaxWeightSelector softmax;
    private final RewardCalculator     rewardCalc;
    private final SafetyConstraint     safety;
    private final Publisher            publisher;
    private final double               alpha;

    // per-channel state
    private final Map<String, ChannelBanditModel> models    = new ConcurrentHashMap<>();
    private final Map<String, ChannelState>       prevState = new ConcurrentHashMap<>();

    public BanditEngine(FeatureBuilder featureBuilder,
                        SoftmaxWeightSelector softmax,
                        RewardCalculator rewardCalc,
                        SafetyConstraint safety,
                        Publisher publisher,
                        double alpha) {
        this.featureBuilder = featureBuilder;
        this.softmax        = softmax;
        this.rewardCalc     = rewardCalc;
        this.safety         = safety;
        this.publisher      = publisher;
        this.alpha          = alpha;
    }

    /** Entry point called by MetricSubscriber for each arriving MetricsBundle. */
    public void process(MetricsBundle bundle) {
        if (!bundle.hasChannel()) return;

        // group all CLIENT sockets by channel name
        Map<String, List<SocketSnapshot>> byChannel = groupClients(bundle);

        for (Map.Entry<String, List<SocketSnapshot>> entry : byChannel.entrySet()) {
            String channel = entry.getKey();
            List<SocketSnapshot> endpoints = sortedByHashId(entry.getValue());

            if (endpoints.size() <= 1) continue; // no balancing needed

            processChannel(channel, endpoints);
        }
    }

    private void processChannel(String channel, List<SocketSnapshot> endpoints) {
        int n = endpoints.size();

        // reset model if topology changed
        ChannelBanditModel model = models.compute(channel, (k, existing) -> {
            if (existing == null || existing.endpointCount() != n) {
                log.info("Bandit model reset for channel={} endpoints={}", channel, n);
                prevState.remove(k);
                return new ChannelBanditModel(n, alpha);
            }
            return existing;
        });

        // build normalized feature vector
        double[] x = featureBuilder.build(channel, endpoints);

        // compute reward and update model if we have a previous state
        ChannelState curr = ChannelState.from(channel, endpoints);
        double reward = 0.0;
        ChannelState prev = prevState.get(channel);
        if (prev != null) {
            reward = rewardCalc.compute(prev, curr);
            model.update(x, reward);
            log.debug("channel={} reward={}", channel, String.format("%.4f", reward));
        }
        prevState.put(channel, curr);

        // score each endpoint
        List<EndpointScore> scores = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double s = model.score(i, x);
            scores.add(new EndpointScore(endpoints.get(i).getHashId(), s));
        }

        // softmax scores → integer weights
        Map<String, Integer> weights = softmax.select(scores);

        // apply safety boundaries
        Set<String> downIds = downSockets(endpoints);
        weights = safety.apply(weights, downIds);

        // publish to se-core
        double confidence = scores.stream().mapToDouble(EndpointScore::score).max().orElse(0);
        publisher.publish(channel, weights, reward, confidence);

        log.info("channel={} weights={} reward={} down={}", channel, weights,
                String.format("%.4f", reward), downIds);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, List<SocketSnapshot>> groupClients(MetricsBundle bundle) {
        Map<String, List<SocketSnapshot>> result = new LinkedHashMap<>();
        for (SocketSnapshot s : bundle.getChannel().getSocketsList()) {
            if (!"CLIENT".equalsIgnoreCase(s.getType())) continue;
            result.computeIfAbsent(s.getName(), k -> new ArrayList<>()).add(s);
        }
        return result;
    }

    private List<SocketSnapshot> sortedByHashId(List<SocketSnapshot> list) {
        return list.stream()
                .sorted(Comparator.comparing(SocketSnapshot::getHashId))
                .collect(Collectors.toList());
    }

    private Set<String> downSockets(List<SocketSnapshot> endpoints) {
        Set<String> down = new HashSet<>();
        for (SocketSnapshot s : endpoints) {
            String state = s.getRuntime().getState();
            if ("DOWN".equalsIgnoreCase(state) || "ERROR".equalsIgnoreCase(state)) {
                down.add(s.getHashId());
            }
        }
        return down;
    }
}
