package tw.xiaoxin.relens;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Gc1MultiplexEventTest {
    @Test public void mapsKnownFirstGenerationEvents() {
        assertEquals(Gc1MultiplexEvent.BOOT_READY, Gc1MultiplexEvent.target((byte) 0x11));
        assertEquals(Gc1MultiplexEvent.PHONE_WIFI_RESULT, Gc1MultiplexEvent.target((byte) 0x34));
        assertEquals(Gc1MultiplexEvent.UNKNOWN, Gc1MultiplexEvent.target((byte) 0x12));
        assertEquals(Gc1MultiplexEvent.UNKNOWN, Gc1MultiplexEvent.target((byte) 0x26));
        assertEquals(Gc1MultiplexEvent.UNKNOWN, Gc1MultiplexEvent.target((byte) 0x7f));
    }
}
