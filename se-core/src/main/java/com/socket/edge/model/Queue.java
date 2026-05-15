package com.socket.edge.model;

public record Queue(String bindingId, String socketId, String name, String type,
                    long msgIn, long msgOut,
                    long queue, long errCnt, long lastErr, long lastMsg){


}
