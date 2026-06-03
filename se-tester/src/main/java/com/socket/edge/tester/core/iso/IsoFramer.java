package com.socket.edge.tester.core.iso;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

import java.util.List;

/**
 * Netty codec pair for the 4-byte big-endian length-prefix frame protocol.
 * Frame = [4-byte big-endian body length][body bytes]
 */
public final class IsoFramer {

    private IsoFramer() {}

    /**
     * Inbound: reads 4-byte length then body, emits byte[].
     * Also rejects non-ISO traffic (first byte > 0x10 = HTTP/other).
     */
    public static final class Decoder extends ByteToMessageDecoder {

        private Boolean nonIso = null;

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (in.readableBytes() < 1) return;

            // First-byte check — done once per connection
            if (nonIso == null) {
                int firstByte = in.getUnsignedByte(in.readerIndex());
                if (firstByte > 0x10) {
                    nonIso = true;
                    ctx.close();
                    return;
                }
                nonIso = false;
            }

            if (in.readableBytes() < 4) return;

            in.markReaderIndex();
            int length = in.readInt();

            if (length <= 0 || length > 65535) {
                ctx.close();
                return;
            }

            if (in.readableBytes() < length) {
                in.resetReaderIndex();
                return;
            }

            byte[] body = new byte[length];
            in.readBytes(body);
            out.add(body);
        }
    }

    /** Outbound: prepends 4-byte big-endian length to byte[] payload. */
    public static final class Encoder extends MessageToByteEncoder<byte[]> {
        @Override
        protected void encode(ChannelHandlerContext ctx, byte[] msg, ByteBuf out) {
            out.writeInt(msg.length);
            out.writeBytes(msg);
        }
    }
}