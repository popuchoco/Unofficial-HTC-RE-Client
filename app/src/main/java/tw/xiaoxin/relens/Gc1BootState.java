package tw.xiaoxin.relens;

final class Gc1BootState {
    private Gc1BootState() { }

    static boolean isReady(byte[] value) {
        return value != null && value.length > 0 && (value[0] & 1) == 1;
    }
}
