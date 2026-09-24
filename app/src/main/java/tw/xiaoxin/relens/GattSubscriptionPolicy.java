package tw.xiaoxin.relens;

final class GattSubscriptionPolicy {
    // BluetoothGattCallback exposes the ATT/GATT status directly; 0x0B is GATT_NOT_LONG.
    static final int GATT_NOT_LONG = 0x0b;
    private static final int MAX_ATTEMPTS = 2;
    private static final int MAX_MULTIPLEX_ATTEMPTS = 10;

    private GattSubscriptionPolicy() { }

    static boolean shouldRediscover(int status, int completedAttempts) {
        return status == GATT_NOT_LONG && completedAttempts < MAX_ATTEMPTS;
    }

    static boolean requiresDescriptorWrite(int controlProfile) {
        // First-generation A000 firmware publishes a CCCD on A304 but rejects its write.
        return controlProfile != 1;
    }

    static boolean shouldRetryMultiplex(int status, int startedAttempts) {
        return status != 0 && startedAttempts < MAX_MULTIPLEX_ATTEMPTS;
    }

    static boolean isLegacyAttributeNotLong(int status) {
        return status == GATT_NOT_LONG;
    }
}
