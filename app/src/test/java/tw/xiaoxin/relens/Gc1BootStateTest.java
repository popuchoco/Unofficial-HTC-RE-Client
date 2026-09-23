package tw.xiaoxin.relens;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Gc1BootStateTest {
    @Test public void decodesBootReadyBit() {
        assertTrue(Gc1BootState.isReady(new byte[]{1}));
        assertTrue(Gc1BootState.isReady(new byte[]{3}));
        assertFalse(Gc1BootState.isReady(new byte[]{0}));
        assertFalse(Gc1BootState.isReady(null));
    }
}
