package com.socket.edge.tester.core.iso;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * ISO 8583 message: MTI + binary bitmap + field data.
 * Wire format: [4-byte ASCII MTI][8-byte primary bitmap][8-byte secondary bitmap?][fields...]
 * FIXED fields: caller must supply correctly formatted values (zero-padded for numeric, space-padded for alpha).
 */
public class IsoMessage {

    private String mti;
    private final TreeMap<Integer, String> fields = new TreeMap<>();

    public IsoMessage() {}

    public IsoMessage(String mti) {
        this.mti = mti;
    }

    public String getMti() { return mti; }
    public void setMti(String mti) { this.mti = mti; }

    public void setField(int de, String value) {
        if (value != null) fields.put(de, value);
    }

    public String getField(int de) {
        String v = fields.get(de);
        return v == null ? null : v.stripTrailing();
    }

    public boolean hasField(int de) { return fields.containsKey(de); }

    public Map<Integer, String> getFields() { return Collections.unmodifiableMap(fields); }

    // -------------------------------------------------------------------------
    // Encode
    // -------------------------------------------------------------------------

    public byte[] encode() {
        boolean hasSecondary = fields.keySet().stream().anyMatch(de -> de > 64);

        byte[] primaryBitmap   = new byte[8];
        byte[] secondaryBitmap = new byte[8];

        if (hasSecondary) primaryBitmap[0] |= (byte) 0x80;

        ByteArrayOutputStream fieldData = new ByteArrayOutputStream(256);

        for (Map.Entry<Integer, String> entry : fields.entrySet()) {
            int de = entry.getKey();
            String value = entry.getValue();
            if (value == null) continue;

            setBitmapBit(de, primaryBitmap, secondaryBitmap);

            if (!IsoFieldDefs.has(de)) continue;
            IsoFieldDefs.FieldDef def = IsoFieldDefs.get(de);
            byte[] vb = value.getBytes(StandardCharsets.ISO_8859_1);

            switch (def.type()) {
                case FIXED -> {
                    int len = def.maxLength();
                    byte[] padded = new byte[len];
                    int copy = Math.min(vb.length, len);
                    System.arraycopy(vb, 0, padded, 0, copy);
                    for (int i = copy; i < len; i++) padded[i] = ' ';
                    fieldData.write(padded, 0, len);
                }
                case LLVAR -> {
                    int len = vb.length;
                    fieldData.write('0' + len / 10);
                    fieldData.write('0' + len % 10);
                    fieldData.write(vb, 0, len);
                }
                case LLLVAR -> {
                    int len = vb.length;
                    fieldData.write('0' + len / 100);
                    fieldData.write('0' + (len / 10) % 10);
                    fieldData.write('0' + len % 10);
                    fieldData.write(vb, 0, len);
                }
            }
        }

        byte[] mtiBytes   = mti.getBytes(StandardCharsets.US_ASCII);
        byte[] fieldBytes = fieldData.toByteArray();
        int total = 4 + 8 + (hasSecondary ? 8 : 0) + fieldBytes.length;
        byte[] result = new byte[total];
        int pos = 0;

        System.arraycopy(mtiBytes, 0, result, pos, 4);              pos += 4;
        System.arraycopy(primaryBitmap, 0, result, pos, 8);         pos += 8;
        if (hasSecondary) {
            System.arraycopy(secondaryBitmap, 0, result, pos, 8);   pos += 8;
        }
        System.arraycopy(fieldBytes, 0, result, pos, fieldBytes.length);

        return result;
    }

    // -------------------------------------------------------------------------
    // Decode
    // -------------------------------------------------------------------------

    public static IsoMessage decode(byte[] data) {
        IsoMessage msg = new IsoMessage();
        int pos = 0;

        msg.mti = new String(data, pos, 4, StandardCharsets.US_ASCII);
        pos += 4;

        byte[] primaryBitmap = new byte[8];
        System.arraycopy(data, pos, primaryBitmap, 0, 8);
        pos += 8;

        byte[] secondaryBitmap = null;
        if ((primaryBitmap[0] & 0x80) != 0) {
            secondaryBitmap = new byte[8];
            System.arraycopy(data, pos, secondaryBitmap, 0, 8);
            pos += 8;
        }

        for (int de = 2; de <= 64; de++) {
            if (!isBitSet(primaryBitmap, de) || !IsoFieldDefs.has(de)) continue;
            int[] rl = readLength(data, pos, IsoFieldDefs.get(de));
            msg.fields.put(de, new String(data, pos + rl[0], rl[1], StandardCharsets.ISO_8859_1));
            pos += rl[0] + rl[1];
        }

        if (secondaryBitmap != null) {
            for (int de = 65; de <= 128; de++) {
                if (!isBitSet(secondaryBitmap, de - 64) || !IsoFieldDefs.has(de)) continue;
                int[] rl = readLength(data, pos, IsoFieldDefs.get(de));
                msg.fields.put(de, new String(data, pos + rl[0], rl[1], StandardCharsets.ISO_8859_1));
                pos += rl[0] + rl[1];
            }
        }

        return msg;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void setBitmapBit(int de, byte[] primary, byte[] secondary) {
        if (de <= 64) {
            int b = (de - 1) / 8, bit = 7 - (de - 1) % 8;
            primary[b] |= (byte)(1 << bit);
        } else {
            int b = (de - 65) / 8, bit = 7 - (de - 65) % 8;
            secondary[b] |= (byte)(1 << bit);
        }
    }

    private static boolean isBitSet(byte[] bitmap, int bitNum) {
        int b = (bitNum - 1) / 8, bit = 7 - (bitNum - 1) % 8;
        return (bitmap[b] & (1 << bit)) != 0;
    }

    /** Returns [prefixLen, dataLen] */
    private static int[] readLength(byte[] data, int pos, IsoFieldDefs.FieldDef def) {
        return switch (def.type()) {
            case FIXED  -> new int[]{ 0, def.maxLength() };
            case LLVAR  -> new int[]{ 2, Integer.parseInt(new String(data, pos, 2, StandardCharsets.US_ASCII)) };
            case LLLVAR -> new int[]{ 3, Integer.parseInt(new String(data, pos, 3, StandardCharsets.US_ASCII)) };
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("IsoMessage{mti=").append(mti);
        fields.forEach((de, v) -> sb.append(", DE").append(de).append("=").append(maskIfPan(de, v)));
        return sb.append('}').toString();
    }

    private static String maskIfPan(int de, String v) {
        if (de == 2 && v != null && v.length() >= 13)
            return v.substring(0, 6) + "******" + v.substring(v.length() - 4);
        return v;
    }
}