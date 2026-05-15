package com.socket.edge.core;

import com.socket.edge.core.socket.AbstractSocket;
import com.socket.edge.model.Metrics;
import com.socket.edge.model.Queue;
import com.socket.edge.model.RuntimeState;
import com.socket.edge.model.SocketEndpoint;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central store for all active {@link SocketTelemetry} instances.
 *
 * <p>Keyed by {@code bindingId} — the stable CRC32 composite of
 * {@code socketId|host:port} — so each (socket, endpoint) pair maps to
 * exactly one telemetry object regardless of how many TCP connections it holds.
 *
 * <p>A secondary index ({@code name → Set<bindingId>}) supports bulk
 * lookup by channel name without scanning the full map.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #register} is called when a socket is bound to an endpoint.
 *       Calling it again for the same pair is idempotent — the existing
 *       {@link SocketTelemetry} is returned.</li>
 *   <li>{@link #unregister(AbstractSocket, SocketEndpoint)} removes one
 *       (socket, endpoint) pair and disposes its meters.</li>
 *   <li>{@link #unregister(AbstractSocket)} removes all endpoints for
 *       a socket at once — used on full channel shutdown.</li>
 * </ol>
 *
 * <p>Thread-safe: both maps are {@link ConcurrentHashMap}; the name-index
 * set is created with {@link ConcurrentHashMap#newKeySet()}.
 *
 *  imp@author Ari Patriana
 *  @since 1.0.0
 */
public class TelemetryRegistry {

    private final MeterRegistry registry;

    /** Primary index: bindingId → telemetry. */
    private final Map<String, SocketTelemetry> byId = new ConcurrentHashMap<>();

    /** Secondary index: channel name → set of bindingIds belonging to that channel. */
    private final Map<String, Set<String>> nameToIds = new ConcurrentHashMap<>();

    public TelemetryRegistry(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Registers a (socket, endpoint) pair and returns its {@link SocketTelemetry}.
     * Idempotent: if the pair was already registered the existing instance is returned.
     *
     * @param socket the socket being registered
     * @param se     the endpoint the socket is bound to
     * @return the telemetry instance for this binding
     */
    public SocketTelemetry register(AbstractSocket socket, SocketEndpoint se) {
        String bindingId = se.bindingId(socket.getId());
        SocketTelemetry telemetry = byId.computeIfAbsent(
                bindingId,
                k -> new SocketTelemetry(bindingId, registry, socket, se)
        );

        nameToIds
                .computeIfAbsent(socket.getName(), k -> ConcurrentHashMap.newKeySet())
                .add(bindingId);

        return telemetry;
    }

    /**
     * Removes and disposes all telemetry entries for every endpoint of the
     * given socket. Used on full channel shutdown where all bindings are torn
     * down at once.
     *
     * @param socket the socket whose entire telemetry should be removed
     */
    public void unregister(AbstractSocket socket) {
        Set<String> ids = nameToIds.remove(socket.getName());
        if (ids != null) {
            ids.forEach(id -> {
                SocketTelemetry telemetry = byId.remove(id);
                if (telemetry != null) {
                    telemetry.dispose();
                }
            });
        }
    }

    /**
     * Removes and disposes the telemetry entry for a single (socket, endpoint)
     * pair. If this was the last endpoint for the socket's channel name, the
     * name index entry is also removed.
     *
     * @param socket the socket being unregistered
     * @param se     the specific endpoint binding to remove
     * @return the disposed {@link SocketTelemetry}, or {@code null} if not found
     */
    public SocketTelemetry unregister(AbstractSocket socket, SocketEndpoint se) {
        String bindingId = se.bindingId(socket.getId());
        Set<String> ids = nameToIds.get(socket.getName());
        if (ids != null) {
            ids.remove(bindingId);
            if (ids.isEmpty()) {
                nameToIds.remove(socket.getName());
            }
        }

        SocketTelemetry telemetry = byId.remove(bindingId);
        if (telemetry != null) {
            telemetry.dispose();
        }
        return telemetry;
    }

    /**
     * Returns the telemetry for the given {@code bindingId}, or {@code null}
     * if no binding with that id is registered.
     */
    public SocketTelemetry getById(String id) {
        return byId.get(id);
    }

    /**
     * Returns all telemetry instances for the given channel name.
     * Returns an empty list if the name is not registered.
     */
    public List<SocketTelemetry> getByName(String name) {
        Set<String> ids = nameToIds.get(name);
        if (ids == null) return List.of();

        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /** Returns the {@link Metrics} snapshot for the given bindingId, or {@code null}. */
    public Metrics getMetricsById(String id) {
        SocketTelemetry socketTelemetry = getById(id);
        if (socketTelemetry != null) {
            return socketTelemetry.getMetrics();
        }
        return null;
    }

    /** Returns the {@link RuntimeState} snapshot for the given bindingId, or {@code null}. */
    public RuntimeState getRuntimeStateById(String id) {
        SocketTelemetry socketTelemetry = getById(id);
        if (socketTelemetry != null) {
            return socketTelemetry.getRuntimeState();
        }
        return null;
    }

    /** Returns the {@link Queue} snapshot for the given bindingId, or {@code null}. */
    public Queue getQueueById(String id) {
        SocketTelemetry socketTelemetry = getById(id);
        if (socketTelemetry != null) {
            return socketTelemetry.getQueue();
        }
        return null;
    }

    /** Returns metrics for all bindings under the given channel name, sorted by socketId. */
    public List<Metrics> getMetricsByName(String name) {
        return getByName(name).stream()
                .map(SocketTelemetry::getMetrics)
                .sorted(Comparator.comparing(Metrics::socketId))
                .toList();
    }

    /** Returns runtime states for all bindings under the given channel name, sorted by socketId. */
    public List<RuntimeState> getRuntimeStateByName(String name) {
        return getByName(name).stream()
                .map(SocketTelemetry::getRuntimeState)
                .sorted(Comparator.comparing(RuntimeState::socketId))
                .toList();
    }

    /** Returns queue snapshots for all bindings under the given channel name, sorted by socketId. */
    public List<Queue> getQueueByName(String name) {
        return getByName(name).stream()
                .map(SocketTelemetry::getQueue)
                .sorted(Comparator.comparing(Queue::socketId))
                .toList();
    }

    /** Returns all registered telemetry instances (unordered). */
    public Collection<SocketTelemetry> getAllTelemetry() {
        return byId.values();
    }

    /** Returns metrics for every registered binding, sorted by socketId. */
    public List<Metrics> getAllMetrics() {
        return byId.values()
                .stream()
                .map(SocketTelemetry::getMetrics)
                .sorted(Comparator.comparing(Metrics::socketId))
                .toList();
    }

    /** Returns runtime states for every registered binding, sorted by socketId. */
    public List<RuntimeState> getAllRuntimeState() {
        return byId.values()
                .stream()
                .map(SocketTelemetry::getRuntimeState)
                .sorted(Comparator.comparing(RuntimeState::socketId))
                .toList();
    }

    /** Returns queue snapshots for every registered binding, sorted by socketId. */
    public List<Queue> getAllQueue() {
        return byId.values()
                .stream()
                .map(SocketTelemetry::getQueue)
                .sorted(Comparator.comparing(Queue::socketId))
                .toList();
    }
}
