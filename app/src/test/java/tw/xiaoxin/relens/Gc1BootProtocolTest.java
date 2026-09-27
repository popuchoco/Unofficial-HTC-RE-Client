package tw.xiaoxin.relens;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Gc1BootProtocolTest {
    @Test public void newFirmwareArmsWaiterBeforeWritingAndRetriesWithoutPolling() {
        assertEquals(Gc1BootProtocol.Branch.NEW_FW, Gc1BootProtocol.branch(2251));
        assertEquals(Gc1BootProtocol.FirstOperation.ARM_WAITER_AND_WRITE_A107,
                Gc1BootProtocol.firstOperation(2251));
        assertEquals(Gc1BootProtocol.TimeoutOperation.RETRY_ARM_WAITER_AND_WRITE_A107,
                Gc1BootProtocol.timeoutOperation(2251));
        assertEquals(3_000L, Gc1BootProtocol.timeoutMs(2251));
    }

    @Test public void legacyFirmwareReadsA101BeforeAndAfterWakeAttempt() {
        assertEquals(Gc1BootProtocol.Branch.LEGACY_FW, Gc1BootProtocol.branch(2250));
        assertEquals(Gc1BootProtocol.FirstOperation.READ_A101,
                Gc1BootProtocol.firstOperation(2250));
        assertEquals(Gc1BootProtocol.TimeoutOperation.READ_A101,
                Gc1BootProtocol.timeoutOperation(2250));
        assertEquals(2_500L, Gc1BootProtocol.timeoutMs(2250));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownFirmwareCannotSelectABranch() {
        Gc1BootProtocol.branch(-1);
    }
}
