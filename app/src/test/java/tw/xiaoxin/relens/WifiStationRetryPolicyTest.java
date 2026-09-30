package tw.xiaoxin.relens;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WifiStationRetryPolicyTest {
    @Test public void retriesFirstTimeoutWithFreshGroup() {
        assertTrue(WifiStationRetryPolicy.shouldRecreateGroup(true, 1, 2));
    }

    @Test public void stopsAfterAttemptBudgetOrGattDisconnect() {
        assertFalse(WifiStationRetryPolicy.shouldRecreateGroup(true, 2, 2));
        assertFalse(WifiStationRetryPolicy.shouldRecreateGroup(false, 1, 2));
    }

    @Test public void acceptsEarlyResultOnlyInsideActiveAttempt() {
        assertFalse(WifiStationRetryPolicy.shouldAcceptResult(false));
        assertTrue(WifiStationRetryPolicy.shouldAcceptResult(true));
    }
}
