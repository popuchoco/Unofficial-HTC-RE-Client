package tw.xiaoxin.relens;

/** Pure prerequisite gate shared by the BLE-to-Wi-Fi transition and its tests. */
final class ReConnectionGate {
    private ReConnectionGate() { }

    static boolean canStartWifiDirect(boolean gattConnected, boolean reCharacteristicsReady,
            boolean statusNotificationsReady) {
        return gattConnected && reCharacteristicsReady && statusNotificationsReady;
    }
}
