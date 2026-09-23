package tw.xiaoxin.relens;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Fragment format used by the first-generation HTC RE A000 GATT profile. */
final class Gc1LongValueCodec {
    private Gc1LongValueCodec() { }

    static List<byte[]> fragment(byte[] payload) {
        ArrayList<byte[]> packets = new ArrayList<>();
        int offset = 0;
        do {
            int length = Math.min(18, payload.length - offset);
            boolean last = offset + length >= payload.length;
            byte[] packet = new byte[length + 2];
            packet[0] = (byte) (length | (last ? 0x80 : 0));
            packet[1] = (byte) offset;
            if (length > 0) System.arraycopy(payload, offset, packet, 2, length);
            packets.add(packet);
            offset += length;
        } while (offset < payload.length);
        return packets;
    }

    static final class Collector {
        private final List<byte[]> packets = new ArrayList<>();
        private int expected = -1;

        byte[] add(byte[] packet) {
            if (packet == null || packet.length < 2) return null;
            int length = packet[0] & 0x7f;
            int offset = packet[1] & 0xff;
            if (packet.length != length + 2) { reset(); return null; }
            packets.add(Arrays.copyOf(packet, packet.length));
            if ((packet[0] & 0x80) != 0) expected = offset + length;
            int received = 0;
            for (byte[] item : packets) received += item[0] & 0x7f;
            if (expected < 0 || received != expected) return null;
            packets.sort(Comparator.comparingInt(item -> item[1] & 0xff));
            ByteArrayOutputStream output = new ByteArrayOutputStream(expected);
            int nextOffset = 0;
            for (byte[] item : packets) {
                if ((item[1] & 0xff) != nextOffset) { reset(); return null; }
                int itemLength = item[0] & 0x7f;
                output.write(item, 2, itemLength);
                nextOffset += itemLength;
            }
            byte[] result = output.toByteArray();
            reset();
            return result;
        }

        void reset() { packets.clear(); expected = -1; }
    }
}
