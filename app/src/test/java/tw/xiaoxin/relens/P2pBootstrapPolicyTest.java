package tw.xiaoxin.relens;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import java.util.Random;

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

    @Test public void generatedOwnerCredentialsMeetWifiP2pBuilderRules() {
        Random random = new Random(1234L);
        String networkName = P2pBootstrapPolicy.createGroupNetworkName(random);
        String passphrase = P2pBootstrapPolicy.createGroupPassphrase(random);

        assertTrue(networkName.matches("DIRECT-[A-Za-z0-9]{2}-RE-Lens"));
        assertTrue(passphrase.matches("[A-Za-z0-9]{16}"));
        assertTrue(passphrase.length() >= 8 && passphrase.length() <= 63);
    }
}
