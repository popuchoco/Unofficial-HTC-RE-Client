package tw.xiaoxin.relens;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test public void encodesAllMediaDescendingQuery() {
        assertArrayEquals(new byte[]{0, 0, 0, (byte) 200, 0, 1},
                Gc1SocketClient.encodeMediaQuery(0, 200));
    }

    @Test(expected=IllegalArgumentException.class)
    public void rejectsEmptyMediaPage() {
        Gc1SocketClient.encodeMediaQuery(0, 0);
    }

    @Test public void firstDownloadFragmentCountsStatusByteInWireOffset() {
        assertEquals(32769L, Gc1SocketClient.nextFragmentWireOffset(0, 32769));
        assertEquals(65537L, Gc1SocketClient.nextFragmentWireOffset(32769, 32768));
    }

    @Test public void statusOnlyLiveViewResponseUsesGc1Uri() throws Exception {
        assertEquals("rtsp://192.168.49.74:8554/MJPEG_unicast",
                Gc1SocketClient.liveViewUri("192.168.49.74", new byte[0]));
        assertEquals("rtsp://camera/custom", Gc1SocketClient.liveViewUri("ignored",
                "rtsp://camera/custom\0".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test public void encodesGc1LiveViewProfile() {
        assertArrayEquals(new byte[]{0}, Gc1SocketClient.liveStillModePayload());
        assertArrayEquals(new byte[]{0x60, 0x09}, Gc1SocketClient.liveFrameRatePayload());
        assertArrayEquals(new byte[]{2}, Gc1SocketClient.liveSizePayload());
        assertArrayEquals(new byte[]{2}, Gc1SocketClient.liveCompressionPayload());
    }

    @Test public void only4012MarksLiveViewReady() {
        assertTrue(Gc1SocketClient.isLiveReadyEvent(0x4012));
        assertFalse(Gc1SocketClient.isLiveReadyEvent(0x5002));
    }
}
