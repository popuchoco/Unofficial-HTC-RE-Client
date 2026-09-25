package tw.xiaoxin.relens;

final class Gc1MultiplexEvent {
    static final int UNKNOWN = 0;
    static final int BOOT_READY = 1;
    static final int PHONE_WIFI_RESULT = 2;

    private Gc1MultiplexEvent() { }

    static int target(byte eventId) {
        int id = eventId & 0xff;
        if (id == 0x11) return BOOT_READY;
        if (id == 0x34) return PHONE_WIFI_RESULT;
        return UNKNOWN;
    }
}
