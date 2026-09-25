package tw.xiaoxin.relens;

import java.util.Locale;

final class Gc1WifiProtocol {
    private Gc1WifiProtocol() { }

    static byte[] serverBand(String country) {
        String normalized = country == null || country.length() < 2
                ? "TW" : country.toUpperCase(Locale.ROOT);
        return new byte[]{1, 0, (byte) normalized.charAt(1), (byte) normalized.charAt(0)};
    }

    static byte[] stationConfig(int randomValue, boolean hotspot) {
        return new byte[]{
                (byte) (((Math.abs(randomValue) % 16) << 4) | 0x01),
                0x04,
                hotspot ? (byte) 0x08 : (byte) 0x01
        };
    }
}
