package tw.xiaoxin.relens;

final class Gc1MultiplexEvent {
    static final int UNKNOWN = 0;
    static final int BOOT_READY = 1;
    static final int PHONE_WIFI_RESULT = 2;
    static final int HARDWARE_STATUS = 3;
    static final int CAMERA_ERROR = 4;

    private Gc1MultiplexEvent() { }

    static int target(byte eventId) {
        int id = eventId & 0xff;
        if (id == 0x11) return BOOT_READY;
        if (id == 0x12) return HARDWARE_STATUS;
        if (id == 0x34) return PHONE_WIFI_RESULT;
        if (id == 0x85) return CAMERA_ERROR;
        return UNKNOWN;
    }
}
