package tw.xiaoxin.relens;

final class GattSubscriptionPolicy {
    // BluetoothGattCallback exposes the ATT/GATT status directly; 0x0B is GATT_NOT_LONG.
    static final int GATT_NOT_LONG = 0x0b;
    private static final int MAX_ATTEMPTS = 2;

    private GattSubscriptionPolicy() { }

    static boolean shouldRediscover(int status, int completedAttempts) {
        return status == GATT_NOT_LONG && completedAttempts < MAX_ATTEMPTS;
    }
}
