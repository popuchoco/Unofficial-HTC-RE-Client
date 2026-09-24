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

    @Test public void firstGenerationUsesLocalNotificationRegistrationOnly() {
        assertFalse(GattSubscriptionPolicy.requiresDescriptorWrite(1));
        assertTrue(GattSubscriptionPolicy.requiresDescriptorWrite(2));
    }

    @Test public void retriesMultiplexDescriptorFailuresUpToTenAttempts() {
        assertTrue(GattSubscriptionPolicy.shouldRetryMultiplex(11, 1));
        assertTrue(GattSubscriptionPolicy.shouldRetryMultiplex(11, 9));
        assertFalse(GattSubscriptionPolicy.shouldRetryMultiplex(11, 10));
        assertFalse(GattSubscriptionPolicy.shouldRetryMultiplex(0, 1));
    }
}
