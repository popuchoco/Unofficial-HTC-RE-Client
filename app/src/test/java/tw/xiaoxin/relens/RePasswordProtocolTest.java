package tw.xiaoxin.relens;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class RePasswordProtocolTest {
    @Test public void emptyPasswordIncludesOperationAndTerminator() {
        assertArrayEquals(new byte[] {0, 0}, RePasswordProtocol.verificationPayload(""));
    }

    @Test public void passwordIsWrappedByOperationAndTerminator() {
        assertArrayEquals(new byte[] {0, 'r', 'e', '1', '2', '3', 0},
                RePasswordProtocol.verificationPayload("re123"));
    }

    @Test public void resultSupportsDirectAndPrefixedNotifications() {
        assertEquals(1, RePasswordProtocol.verificationResult(new byte[] {1}));
        assertEquals(3, RePasswordProtocol.verificationResult(new byte[] {0, 3}));
        assertEquals(-1, RePasswordProtocol.verificationResult(new byte[0]));
    }
}
