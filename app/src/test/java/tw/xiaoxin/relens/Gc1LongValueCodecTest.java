package tw.xiaoxin.relens;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.Test;

public class Gc1LongValueCodecTest {
    private static final UUID CHARACTERISTIC =
            UUID.fromString("0000a301-0000-1000-8000-00805f9b34fb");

    @Test public void fragmentsUsingLengthLastFlagAndOffset() {
        byte[] payload = new byte[21];
        for (int index = 0; index < payload.length; index++) payload[index] = (byte) index;

        List<GattCommandQueue.Packet> packets =
                Gc1LongValueCodec.fragment(CHARACTERISTIC, payload, "ssid");

        assertEquals(2, packets.size());
        assertEquals(20, packets.get(0).value.length);
        assertArrayEquals(new byte[]{18, 0, 0, 1},
                java.util.Arrays.copyOf(packets.get(0).value, 4));
        assertArrayEquals(new byte[]{(byte) 0x83, 18, 18, 19, 20}, packets.get(1).value);
    }

    @Test public void marksSinglePacketAsLast() {
        List<GattCommandQueue.Packet> packets = Gc1LongValueCodec.fragment(
                CHARACTERISTIC, new byte[]{7, 8}, "password");
        assertArrayEquals(new byte[]{(byte) 0x82, 0, 7, 8}, packets.get(0).value);
    }
}
