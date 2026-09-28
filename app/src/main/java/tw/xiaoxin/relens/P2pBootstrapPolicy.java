package tw.xiaoxin.relens;

final class P2pBootstrapPolicy {
    private P2pBootstrapPolicy() { }

    static boolean shouldAutoStart(boolean startRequested, boolean hasGroup) {
        return !startRequested && !hasGroup;
    }

    static boolean canReuseOwnerGroup(boolean isOwner, String ssid, String passphrase,
            int frequencyMhz) {
        return isOwner && ssid != null && !ssid.isEmpty()
                && passphrase != null && !passphrase.isEmpty()
                && isGc1CompatibleFrequency(frequencyMhz);
    }

    static boolean isGc1CompatibleFrequency(int frequencyMhz) {
        return frequencyMhz <= 0 || (frequencyMhz >= 2_400 && frequencyMhz <= 2_500);
    }
}
