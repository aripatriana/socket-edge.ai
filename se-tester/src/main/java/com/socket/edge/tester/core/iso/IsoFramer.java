package com.socket.edge.tester.core.iso;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

import java.util.List;

/**
 * Netty codec pair for ISO 8583 length-prefix framing.
 *
 * Supports configurable header size:
 *   headerBytes=2  →  SE-Core compatible  (LengthFieldPrepender(2))
 *   headerBytes=4  →  standalone default  (4-byte big-endian)
 */
public final class IsoFramer {

    private IsoFramer() {}

    public static final class Decoder extends ByteToMessageDecoder {

        private final int headerBytes;
        private Boolean nonIso = null;

        /** Default: 4-byte header (standalone mock). */
        public Decoder() { this(4); }

        public Decoder(int headerBytes) {
            if (headerBytes != 2 && headerBytes != 4)
                throw new IllegalArgumentException("headerBytes must be 2 or 4");
            this.headerBytes = headerBytes;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (in.readableBytes() < 1) return;

            if (nonIso == null) {
                int firstByte = in.getUnsignedByte(in.readerIndex());
                if (firstByte > 0x10) { nonIso = true; ctx.close(); return; }
                nonIso = false;
            }

            if (in.readableBytes() < headerBytes) return;

            in.markReaderIndex();
            int length = headerBytes == 2 ? in.readUnsignedShort() : in.readInt();

            if (length <= 0 || length > 65535) { ctx.close(); return; }

            if (in.readableBytes() < length) { in.resetReaderIndex(); return; }

            byte[] body = new byte[length];
            in.readBytes(body);
            out.add(body);
        }
    }

    public static final class Encoder extends MessageToByteEncoder<byte[]> {

        private final int headerBytes;

        /** Default: 4-byte header (standalone mock). */
        public Encoder() { this(4); }

        public Encoder(int headerBytes) {
            if (headerBytes != 2 && headerBytes != 4)
                throw new IllegalArgumentException("headerBytes must be 2 or 4");
            this.headerBytes = headerBytes;
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, byte[] msg, ByteBuf out) {
            if (headerBytes == 2) out.writeShort(msg.length);
            else                  out.writeInt(msg.length);
            out.writeBytes(msg);
        }
    }
}