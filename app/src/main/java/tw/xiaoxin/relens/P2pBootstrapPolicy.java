package tw.xiaoxin.relens;

import java.util.Random;

final class P2pBootstrapPolicy {
    private static final char[] CREDENTIAL_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
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

    static String createGroupNetworkName(Random random) {
        return "DIRECT-" + randomCredential(random, 2) + "-RE-Lens";
    }

    static String createGroupPassphrase(Random random) {
        return randomCredential(random, 16);
    }

    private static String randomCredential(Random random, int length) {
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            value.append(CREDENTIAL_ALPHABET[random.nextInt(CREDENTIAL_ALPHABET.length)]);
        }
        return value.toString();
    }
}
