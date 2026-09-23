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
        assertTrue(P2pBootstrapPolicy.canReuseOwnerGroup(true, "DIRECT-RE", "secret"));
        assertFalse(P2pBootstrapPolicy.canReuseOwnerGroup(false, "DIRECT-RE", "secret"));
        assertFalse(P2pBootstrapPolicy.canReuseOwnerGroup(true, "", "secret"));
        assertFalse(P2pBootstrapPolicy.canReuseOwnerGroup(true, "DIRECT-RE", null));
    }
}
