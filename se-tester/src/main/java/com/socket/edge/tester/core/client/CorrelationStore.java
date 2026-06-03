package com.socket.edge.tester.core.client;

import com.socket.edge.tester.core.iso.IsoMessage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps DE11(STAN):DE37(RRN) key to a pending CompletableFuture waiting for the response.
 */
public class CorrelationStore {

    private final ConcurrentHashMap<String, CompletableFuture<IsoMessage>> pending = new ConcurrentHashMap<>();

    public static String keyOf(IsoMessage msg) {
        String stan = msg.getField(11);
        String rrn  = msg.getField(37);
        return (stan != null ? stan.trim() : "") + ":" + (rrn != null ? rrn.trim() : "");
    }

    public CompletableFuture<IsoMessage> register(String key) {
        CompletableFuture<IsoMessage> future = new CompletableFuture<>();
        pending.put(key, future);
        return future;
    }

    /** Returns true if a waiting future was found and completed. */
    public boolean complete(String key, IsoMessage response) {
        CompletableFuture<IsoMessage> f = pending.remove(key);
        if (f != null) { f.complete(response); return true; }
        return false;
    }

    public void failAll(Throwable cause) {
        pending.values().forEach(f -> f.completeExceptionally(cause));
        pending.clear();
    }

    public int size() { return pending.size(); }
}