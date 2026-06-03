package com.socket.edge.tester.core.client;

import com.socket.edge.tester.core.iso.IsoFramer;
import com.socket.edge.tester.core.iso.IsoMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Netty-based ISO 8583 TCP client.
 * Correlation: matches response to request via DE11(STAN) + DE37(RRN).
 */
public class IsoClient {

    private static final Logger log = LoggerFactory.getLogger(IsoClient.class);

    private final CorrelationStore store = new CorrelationStore();
    private EventLoopGroup group;
    private Channel channel;

    public void connect(String host, int port, int timeoutMs) throws Exception {
        group = new NioEventLoopGroup();
        CorrelationStore cs = store;

        Bootstrap b = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMs)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                          .addLast(new IsoFramer.Decoder())
                          .addLast(new IsoFramer.Encoder())
                          .addLast(new SimpleChannelInboundHandler<byte[]>() {

                              @Override
                              protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) {
                                  try {
                                      IsoMessage response = IsoMessage.decode(msg);
                                      String key = CorrelationStore.keyOf(response);
                                      if (!cs.complete(key, response)) {
                                          log.warn("Unmatched response key={}", key);
                                      }
                                  } catch (Exception e) {
                                      log.error("Failed to decode response", e);
                                  }
                              }

                              @Override
                              public void channelInactive(ChannelHandlerContext ctx) {
                                  cs.failAll(new Exception("Connection closed by remote"));
                              }

                              @Override
                              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                  log.error("Channel error: {}", cause.getMessage());
                                  cs.failAll(cause);
                                  ctx.close();
                              }
                          });
                    }
                });

        ChannelFuture cf = b.connect(host, port).sync();
        channel = cf.channel();
        log.info("Connected to {}:{}", host, port);
    }

    /**
     * Blocking send — waits for correlated response or throws on timeout.
     */
    public IsoMessage send(IsoMessage request, long timeoutMs) throws Exception {
        ensureConnected();
        String key = CorrelationStore.keyOf(request);
        CompletableFuture<IsoMessage> future = store.register(key);

        channel.writeAndFlush(request.encode()).sync();

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            store.complete(key, null);
            throw new TimeoutException("No response for key=" + key + " within " + timeoutMs + "ms");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw new Exception("Error receiving response: " + cause.getMessage(), cause);
        }
    }

    /**
     * Non-blocking send — returns future that resolves when correlated response arrives.
     */
    public CompletableFuture<IsoMessage> sendAsync(IsoMessage request, long timeoutMs) {
        if (!isConnected()) return CompletableFuture.failedFuture(new IllegalStateException("Not connected"));
        String key = CorrelationStore.keyOf(request);
        CompletableFuture<IsoMessage> future = store.register(key);
        channel.writeAndFlush(request.encode());
        return future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public void disconnect() {
        if (channel != null) channel.close().awaitUninterruptibly();
        if (group != null)   group.shutdownGracefully().awaitUninterruptibly();
        channel = null;
        group   = null;
        log.info("Disconnected");
    }

    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    private void ensureConnected() {
        if (!isConnected()) throw new IllegalStateException("IsoClient is not connected");
    }
}