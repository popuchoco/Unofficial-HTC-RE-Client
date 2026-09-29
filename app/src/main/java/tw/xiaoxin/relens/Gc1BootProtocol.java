package tw.xiaoxin.relens;

final class Gc1BootProtocol {
    enum Branch { NEW_FW, LEGACY_FW }
    enum FirstOperation { ARM_WAITER_AND_WRITE_A107, READ_A101 }
    enum TimeoutOperation { RETRY_ARM_WAITER_AND_WRITE_A107, READ_A101 }

    private Gc1BootProtocol() { }

    static Branch branch(int bleFirmwareVersion) {
        return Gc1FirmwareVersion.usesNewBootFlow(bleFirmwareVersion)
                ? Branch.NEW_FW : Branch.LEGACY_FW;
    }

    static FirstOperation firstOperation(int bleFirmwareVersion) {
        return branch(bleFirmwareVersion) == Branch.NEW_FW
                ? FirstOperation.ARM_WAITER_AND_WRITE_A107
                : FirstOperation.READ_A101;
    }

    static TimeoutOperation timeoutOperation(int bleFirmwareVersion) {
        return branch(bleFirmwareVersion) == Branch.NEW_FW
                ? TimeoutOperation.RETRY_ARM_WAITER_AND_WRITE_A107
                : TimeoutOperation.READ_A101;
    }

    static long timeoutMs(int bleFirmwareVersion) {
        return branch(bleFirmwareVersion) == Branch.NEW_FW ? 3_000L : 2_500L;
    }

    static boolean canCompleteWake(boolean readyObserved, boolean echoVerified) {
        return readyObserved && echoVerified;
    }
}
