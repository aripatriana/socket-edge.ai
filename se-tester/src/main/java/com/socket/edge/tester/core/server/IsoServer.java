package com.socket.edge.tester.core.server;

import com.socket.edge.tester.core.iso.IsoFramer;
import com.socket.edge.tester.core.iso.IsoMessage;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Netty-based mock issuer TCP server.
 * Decodes incoming ISO 8583 frames, delegates to AutoResponder, sends reply.
 */
public class IsoServer {

    private static final Logger log = LoggerFactory.getLogger(IsoServer.class);

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    private final AtomicInteger receivedCount = new AtomicInteger(0);
    private AutoResponder responder;
    private volatile boolean running = false;

    /** Backward-compat: default 4-byte header. */
    public void start(int port, boolean autoRespond, int delayMs, String responseCode) throws Exception {
        start(port, autoRespond, delayMs, responseCode, 4);
    }

    public void start(int port, boolean autoRespond, int delayMs, String responseCode, int headerBytes) throws Exception {
        bossGroup  = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        responder   = new AutoResponder(autoRespond, delayMs, responseCode);

        AtomicInteger count = receivedCount;
        AutoResponder resp  = responder;

        ServerBootstrap b = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                          .addLast(new IsoFramer.Decoder(headerBytes))
                          .addLast(new IsoFramer.Encoder(headerBytes))
                          .addLast(new SimpleChannelInboundHandler<byte[]>() {

                              @Override
                              protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) {
                                  count.incrementAndGet();
                                  try {
                                      IsoMessage request  = IsoMessage.decode(msg);
                                      IsoMessage response = resp.respond(request);
                                      if (response != null) {
                                          ctx.writeAndFlush(response.encode());
                                      }
                                  } catch (Exception e) {
                                      log.error("Mock server error processing message", e);
                                  }
                              }

                              @Override
                              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                  log.warn("Mock server channel exception: {}", cause.getMessage());
                                  ctx.close();
                              }
                          });
                    }
                });

        serverChannel = b.bind(port).sync().channel();
        running = true;
        log.info("Mock issuer started on port {}", port);
    }

    public void stop() {
        running = false;
        if (serverChannel != null) serverChannel.close().awaitUninterruptibly();
        if (bossGroup   != null)   bossGroup.shutdownGracefully().awaitUninterruptibly();
        if (workerGroup != null)   workerGroup.shutdownGracefully().awaitUninterruptibly();
        log.info("Mock issuer stopped (received {} messages)", receivedCount.get());
    }

    public int getReceivedCount() { return receivedCount.get(); }
    public boolean isRunning()    { return running; }
}