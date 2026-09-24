package tw.xiaoxin.relens;

import java.util.ArrayList;
import java.util.List;

/** Fragment format used by the first-generation HTC RE A000 GATT profile. */
final class Gc1LongValueCodec {
    private Gc1LongValueCodec() { }

    static List<byte[]> fragment(byte commandId, byte[] payload) {
        ArrayList<byte[]> packets = new ArrayList<>();
        int firstLength = Math.min(17, payload.length);
        byte[] first = new byte[firstLength + 3];
        first[0] = commandId;
        first[1] = (byte) ((payload.length >> 8) & 0xff);
        first[2] = (byte) (payload.length & 0xff);
        if (firstLength > 0) System.arraycopy(payload, 0, first, 3, firstLength);
        packets.add(first);
        int offset = firstLength;
        int sequence = 1;
        while (offset < payload.length) {
            int length = Math.min(18, payload.length - offset);
            byte[] packet = new byte[length + 2];
            packet[0] = commandId;
            packet[1] = (byte) (sequence * 2);
            System.arraycopy(payload, offset, packet, 2, length);
            packets.add(packet);
            offset += length;
            sequence++;
        }
        return packets;
    }
}
