package tw.xiaoxin.relens;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReAdvertisementMatcherTest {
    @Test public void acceptsCameraName() {
        assertTrue(ReAdvertisementMatcher.matches("hTC GC 40:a0:58", null));
    }

    @Test public void rejectsUnrelatedAndUnnamedDevices() {
        assertFalse(ReAdvertisementMatcher.matches("Pokemon GO Plus", null));
        assertFalse(ReAdvertisementMatcher.matches(null, new byte[]{2, 1, 6, 0}));
    }

    @Test public void acceptsGc1Marker() {
        assertTrue(ReAdvertisementMatcher.matches(null, new byte[]{0, 0, 0, 0, 0, 0, (byte) 0xa0, 0}));
    }

    @Test public void acceptsGc2ManufacturerMarker() {
        assertTrue(ReAdvertisementMatcher.matches(null,
                new byte[]{5, (byte) 0xff, 0x0f, 0x00, (byte) 0xcf, 0x00, 0}));
    }
}
