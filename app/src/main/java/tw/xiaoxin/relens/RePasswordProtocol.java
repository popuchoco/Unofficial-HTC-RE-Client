package tw.xiaoxin.relens;

final class RePasswordProtocol {
    private RePasswordProtocol() { }

    static byte[] verificationPayload(String password) {
        return payload(0, password);
    }

    static byte[] changePayload(String password) {
        return payload(1, password);
    }

    private static byte[] payload(int operation, String password) {
        String value = password == null ? "" : password;
        char[] characters = value.toCharArray();
        byte[] payload = new byte[characters.length + 2];
        payload[0] = (byte) operation;
        for (int index = 0; index < characters.length; index++) {
            payload[index + 1] = (byte) characters[index];
        }
        payload[payload.length - 1] = 0;
        return payload;
    }

    static int verificationResult(byte[] value) {
        if (value == null || value.length == 0) return -1;
        return value[value.length - 1] & 0xff;
    }
}
