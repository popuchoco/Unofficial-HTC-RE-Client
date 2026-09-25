package tw.xiaoxin.relens;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class Gc1LongValueCodec {
    private static final int CHUNK_SIZE = 18;

    private Gc1LongValueCodec() { }

    static List<GattCommandQueue.Packet> fragment(UUID characteristic, byte[] payload,
            String label) {
        List<GattCommandQueue.Packet> packets = new ArrayList<>();
        for (int offset = 0; offset < payload.length; offset += CHUNK_SIZE) {
            int length = Math.min(CHUNK_SIZE, payload.length - offset);
            boolean last = offset + length >= payload.length;
            byte[] value = new byte[length + 2];
            value[0] = (byte) (length | (last ? 0x80 : 0));
            value[1] = (byte) offset;
            System.arraycopy(payload, offset, value, 2, length);
            packets.add(new GattCommandQueue.Packet(characteristic, value, label));
        }
        return packets;
    }
}
