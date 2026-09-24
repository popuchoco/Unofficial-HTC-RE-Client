package tw.xiaoxin.relens;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GattDiscoveryGateTest {
    @Test public void waitsForGattAndAclSignalsBeforeDiscovery() {
        assertFalse(GattDiscoveryGate.shouldDiscover(true, false, false));
        assertFalse(GattDiscoveryGate.shouldDiscover(false, true, false));
        assertTrue(GattDiscoveryGate.shouldDiscover(true, true, false));
    }

    @Test public void doesNotStartDuplicateDiscovery() {
        assertFalse(GattDiscoveryGate.shouldDiscover(true, true, true));
    }

    @Test public void retriesAnEmptyServiceTableOnlyOnce() {
        assertTrue(GattDiscoveryGate.shouldRetryEmpty(0));
        assertFalse(GattDiscoveryGate.shouldRetryEmpty(1));
    }
}
