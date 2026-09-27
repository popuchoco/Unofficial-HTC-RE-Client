package tw.xiaoxin.relens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Gc1FirmwareVersionTest {
    @Test public void parsesAsciiDecimalFirmwareRevision() {
        assertEquals(2251, Gc1FirmwareVersion.parse(new byte[]{'2', '2', '5', '1'}));
        assertEquals(2250, Gc1FirmwareVersion.parse(new byte[]{'2', '2', '5', '0', 0x20}));
    }

    @Test public void rejectsMissingOrNonDecimalFirmwareRevision() {
        assertEquals(-1, Gc1FirmwareVersion.parse(null));
        assertEquals(-1, Gc1FirmwareVersion.parse(new byte[0]));
        assertEquals(-1, Gc1FirmwareVersion.parse(new byte[]{'2', '.', '2'}));
    }

    @Test public void thresholdIsStrictlyGreaterThan2250() {
        assertFalse(Gc1FirmwareVersion.usesNewBootFlow(2250));
        assertTrue(Gc1FirmwareVersion.usesNewBootFlow(2251));
    }
}
