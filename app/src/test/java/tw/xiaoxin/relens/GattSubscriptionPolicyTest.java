package tw.xiaoxin.relens;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GattSubscriptionPolicyTest {
    @Test public void retriesStatus11OnlyOnce() {
        assertTrue(GattSubscriptionPolicy.shouldRediscover(11, 1));
        assertFalse(GattSubscriptionPolicy.shouldRediscover(11, 2));
    }

    @Test public void doesNotRetryUnrelatedGattFailure() {
        assertFalse(GattSubscriptionPolicy.shouldRediscover(5, 1));
    }
}
