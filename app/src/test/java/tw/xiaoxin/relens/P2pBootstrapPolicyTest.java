package tw.xiaoxin.relens;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class P2pBootstrapPolicyTest {
    @Test public void startsAutomaticallyOnlyWithoutAnActiveRequestOrGroup() {
        assertTrue(P2pBootstrapPolicy.shouldAutoStart(false, false));
        assertFalse(P2pBootstrapPolicy.shouldAutoStart(true, false));
        assertFalse(P2pBootstrapPolicy.shouldAutoStart(false, true));
    }

    @Test public void reusesOnlyAnOwnerGroupWithCredentials() {
        assertTrue(P2pBootstrapPolicy.canReuseOwnerGroup(true, "DIRECT-RE", "secret", 2412));
        assertFalse(P2pBootstrapPolicy.canReuseOwnerGroup(false, "DIRECT-RE", "secret", 2412));
        assertFalse(P2pBootstrapPolicy.canReuseOwnerGroup(true, "", "secret", 2412));
        assertFalse(P2pBootstrapPolicy.canReuseOwnerGroup(true, "DIRECT-RE", null, 2412));
        assertFalse(P2pBootstrapPolicy.canReuseOwnerGroup(true, "DIRECT-RE", "secret", 5180));
    }

    @Test public void gc1AcceptsOnlyKnownTwoPointFourGhzGroups() {
        assertTrue(P2pBootstrapPolicy.isGc1CompatibleFrequency(2412));
        assertTrue(P2pBootstrapPolicy.isGc1CompatibleFrequency(2484));
        assertTrue(P2pBootstrapPolicy.isGc1CompatibleFrequency(0));
        assertFalse(P2pBootstrapPolicy.isGc1CompatibleFrequency(5180));
    }
}
