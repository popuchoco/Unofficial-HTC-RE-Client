package tw.xiaoxin.relens;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

public class Gc1LongValueCodecTest {
    @Test public void fragmentsFirstGenerationLongValues() {
        byte[] source = new byte[23];
        for (int index = 0; index < source.length; index++) source[index] = (byte) index;
        List<byte[]> packets = Gc1LongValueCodec.fragment((byte) 34, source);
        assertEquals(2, packets.size());
        assertEquals(34, packets.get(0)[0] & 0xff);
        assertEquals(0, packets.get(0)[1]);
        assertEquals(23, packets.get(0)[2]);
        assertEquals(20, packets.get(0).length);
        assertEquals(34, packets.get(1)[0] & 0xff);
        assertEquals(2, packets.get(1)[1]);
        assertEquals(8, packets.get(1).length);
    }
}
