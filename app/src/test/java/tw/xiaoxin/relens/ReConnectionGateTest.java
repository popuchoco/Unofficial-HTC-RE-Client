package tw.xiaoxin.relens;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReConnectionGateTest {
    @Test public void blocksWifiDirectUntilVerifiedReControlChannelIsReady() {
        assertFalse(ReConnectionGate.canStartWifiDirect(false, false, false));
        assertFalse(ReConnectionGate.canStartWifiDirect(true, false, false));
        assertFalse(ReConnectionGate.canStartWifiDirect(true, true, false));
        assertTrue(ReConnectionGate.canStartWifiDirect(true, true, true));
    }

    @Test public void disconnectClosesGateEvenIfOldDiscoveryFlagsRemain() {
        assertFalse(ReConnectionGate.canStartWifiDirect(false, true, true));
    }
}
