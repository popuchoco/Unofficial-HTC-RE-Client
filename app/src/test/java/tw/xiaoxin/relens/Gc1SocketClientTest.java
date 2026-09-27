package tw.xiaoxin.relens;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Gc1SocketClientTest {
    @Test public void encodesLittleEndianHeaderAndPayload() {
        byte[] packet = Gc1SocketClient.encodeRequest(311, 7, 0, new byte[]{0});
        ByteBuffer data = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(311, data.getInt());
        assertEquals(17, data.getInt());
        assertEquals(7, data.getInt());
        assertEquals(0, data.getInt());
        byte[] payload = new byte[data.remaining()];
        data.get(payload);
        assertArrayEquals(new byte[]{0}, payload);
    }

    @Test public void encodesEmptyStopRecordingRequest() {
        byte[] packet = Gc1SocketClient.encodeRequest(107, 2, 0, null);
        ByteBuffer data = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(107, data.getInt());
        assertEquals(16, data.getInt());
        assertEquals(2, data.getInt());
        assertEquals(0, data.getInt());
        assertEquals(0, data.remaining());
    }
}
