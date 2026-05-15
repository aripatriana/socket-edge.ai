package com.socket.edge.ai.bandit;

/**
 * LinUCB score for one endpoint within a channel.
 *
 * @param hashId  matches SocketSnapshot.hash_id — stable cross-restarts
 * @param score   raw LinUCB score (exploit + explore); higher is better
 */
public record EndpointScore(String hashId, double score) {}
