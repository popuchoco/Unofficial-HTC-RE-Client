package tw.xiaoxin.relens;

import java.util.Locale;

/** Identifies HTC RE advertisements without accepting unrelated unnamed BLE devices. */
final class ReAdvertisementMatcher {
    private ReAdvertisementMatcher() { }

    static boolean matches(String deviceName, byte[] scanRecord) {
        if (deviceName != null && deviceName.toUpperCase(Locale.ROOT).contains("HTC GC")) return true;
        return hasGc1Marker(scanRecord) || hasGc2ManufacturerMarker(scanRecord);
    }

    private static boolean hasGc1Marker(byte[] record) {
        return record != null && record.length > 7
                && (record[6] & 0xff) == 0xa0 && (record[5] & 0xff) == 0x00;
    }

    private static boolean hasGc2ManufacturerMarker(byte[] record) {
        if (record == null) return false;
        int offset = 0;
        while (offset < record.length) {
            int length = record[offset] & 0xff;
            if (length == 0) break;
            int end = offset + length;
            if (end >= record.length) break;
            int type = record[offset + 1] & 0xff;
            if (type == 0xff && length >= 5
                    && (record[offset + 2] & 0xff) == 0x0f
                    && (record[offset + 3] & 0xff) == 0x00
                    && (record[offset + 4] & 0xff) == 0xcf
                    && (record[offset + 5] & 0xff) == 0x00) return true;
            offset = end + 1;
        }
        return false;
    }
}
