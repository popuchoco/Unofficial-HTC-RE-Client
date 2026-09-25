package tw.xiaoxin.relens;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class Gc1WifiProtocolTest {
    @Test public void encodesCountryInFirstGenerationOrder() {
        assertArrayEquals(new byte[]{1, 0, 'W', 'T'}, Gc1WifiProtocol.serverBand("tw"));
    }

    @Test public void encodesThreeByteStationConfig() {
        assertArrayEquals(new byte[]{0x31, 0x04, 0x01},
                Gc1WifiProtocol.stationConfig(3, false));
        assertArrayEquals(new byte[]{0x31, 0x04, 0x08},
                Gc1WifiProtocol.stationConfig(3, true));
    }
}
