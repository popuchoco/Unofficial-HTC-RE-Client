package tw.xiaoxin.relens;

final class WifiStationRetryPolicy {
    private WifiStationRetryPolicy() { }

    static boolean shouldRecreateGroup(boolean gattConnected, int completedAttempts,
            int maxAttempts) {
        return gattConnected && completedAttempts > 0 && completedAttempts < maxAttempts;
    }

    static boolean shouldAcceptResult(boolean stationAttemptActive) {
        return stationAttemptActive;
    }
}
