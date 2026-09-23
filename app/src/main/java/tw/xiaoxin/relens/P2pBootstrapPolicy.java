package tw.xiaoxin.relens;

final class P2pBootstrapPolicy {
    private P2pBootstrapPolicy() { }

    static boolean shouldAutoStart(boolean startRequested, boolean hasGroup) {
        return !startRequested && !hasGroup;
    }

    static boolean canReuseOwnerGroup(boolean isOwner, String ssid, String passphrase) {
        return isOwner && ssid != null && !ssid.isEmpty()
                && passphrase != null && !passphrase.isEmpty();
    }
}
