package tw.xiaoxin.relens;

import java.nio.charset.StandardCharsets;

final class Gc1FirmwareVersion {
    static final int NEW_BOOT_FLOW_THRESHOLD = 2250;

    private Gc1FirmwareVersion() { }

    static int parse(byte[] value) {
        if (value == null || value.length == 0) return -1;
        String text = new String(value, StandardCharsets.US_ASCII).trim();
        if (text.isEmpty()) return -1;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) return -1;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    static boolean usesNewBootFlow(int version) {
        if (version < 0) throw new IllegalArgumentException("BLE firmware version is unknown");
        return version > NEW_BOOT_FLOW_THRESHOLD;
    }
}
