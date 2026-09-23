package tw.xiaoxin.relens;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import org.junit.Test;

public class Gc1LongValueCodecTest {
    @Test public void fragmentsAndReassemblesFirstGenerationValues() {
        byte[] source = new byte[23];
        for (int index = 0; index < source.length; index++) source[index] = (byte) index;
        List<byte[]> packets = Gc1LongValueCodec.fragment(source);
        assertEquals(2, packets.size());
        assertEquals(18, packets.get(0)[0] & 0x7f);
        assertEquals(0, packets.get(0)[1]);
        assertEquals(5, packets.get(1)[0] & 0x7f);
        assertEquals(0x80, packets.get(1)[0] & 0x80);
        assertEquals(18, packets.get(1)[1]);

        Gc1LongValueCodec.Collector collector = new Gc1LongValueCodec.Collector();
        assertNull(collector.add(packets.get(0)));
        assertArrayEquals(source, collector.add(packets.get(1)));
    }
}
