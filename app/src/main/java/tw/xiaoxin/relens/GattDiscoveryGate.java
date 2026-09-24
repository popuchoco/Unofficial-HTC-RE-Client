package tw.xiaoxin.relens;

final class GattDiscoveryGate {
    private GattDiscoveryGate() { }

    static boolean shouldDiscover(boolean gattConnected, boolean aclConnected, boolean started) {
        return gattConnected && aclConnected && !started;
    }

    static boolean shouldRetryEmpty(int retries) {
        return retries < 1;
    }
}
