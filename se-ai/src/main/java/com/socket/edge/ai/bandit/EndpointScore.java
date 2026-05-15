package com.socket.edge.ai.bandit;

/**
 * LinUCB score for one endpoint within a channel.
 *
 * @param bindingId  matches SocketSnapshot.binding_id — stable cross-restarts
 * @param score      raw LinUCB score (exploit + explore); higher is better
 */
public record EndpointScore(String bindingId, double score) {}
