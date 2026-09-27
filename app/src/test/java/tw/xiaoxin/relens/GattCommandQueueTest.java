package tw.xiaoxin.relens;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothGatt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public class GattCommandQueueTest {
    private static final UUID CHARACTERISTIC = UUID.fromString("0000cf02-0000-1000-8000-00805f9b34fb");

    @Test public void waitsForCallbackBeforeWritingNextPacket() {
        List<byte[]> writes = new ArrayList<>();
        boolean[] complete = {false};
        GattCommandQueue queue = new GattCommandQueue((id, value) -> {
            writes.add(value);
            return true;
        }, new GattCommandQueue.Listener() {
            @Override public void onProgress(String label, int remaining) { }
            @Override public void onComplete() { complete[0] = true; }
            @Override public void onError(String message) { throw new AssertionError(message); }
        });

        List<GattCommandQueue.Packet> packets = Arrays.asList(
                new GattCommandQueue.Packet(CHARACTERISTIC, new byte[]{1}, "one"),
                new GattCommandQueue.Packet(CHARACTERISTIC, new byte[]{2}, "two"));
        queue.replace(packets);
        assertEquals(1, writes.size());
        assertFalse(complete[0]);

        queue.onCharacteristicWrite(CHARACTERISTIC, BluetoothGatt.GATT_SUCCESS);
        assertEquals(2, writes.size());
        assertFalse(complete[0]);

        queue.onCharacteristicWrite(CHARACTERISTIC, BluetoothGatt.GATT_SUCCESS);
        assertTrue(complete[0]);
    }

    @Test public void fragmentsLongCommandIntoProtocolSizedPackets() {
        byte[] payload = new byte[36];
        for (int index = 0; index < payload.length; index++) payload[index] = (byte) index;
        List<GattCommandQueue.Packet> packets = GattCommandQueue.longCommand(
                CHARACTERISTIC, (byte) 0x22, payload, "ssid");

        assertEquals(3, packets.size());
        assertEquals(20, packets.get(0).value.length);
        assertArrayEquals(new byte[]{0x22, 0, 36}, Arrays.copyOf(packets.get(0).value, 3));
        assertEquals(20, packets.get(1).value.length);
        assertEquals(0x02, packets.get(1).value[1]);
        assertEquals(3, packets.get(2).value.length);
        assertEquals(0x04, packets.get(2).value[1]);
    }

    @Test public void failureIdentifiesPacketThatWasRejected() {
        String[] error = {null};
        GattCommandQueue queue = new GattCommandQueue((id, value) -> true,
                new GattCommandQueue.Listener() {
                    @Override public void onProgress(String label, int remaining) { }
                    @Override public void onComplete() { }
                    @Override public void onError(String message) { error[0] = message; }
                });
        queue.replace(Arrays.asList(new GattCommandQueue.Packet(
                CHARACTERISTIC, new byte[]{1, 2, 3, 4}, "country")));
        queue.onCharacteristicWrite(CHARACTERISTIC, 11);
        assertTrue(error[0].contains("status=11"));
        assertTrue(error[0].contains("length=4"));
        assertTrue(error[0].contains("stage=country"));
    }

    @Test public void throttleWaitsAfterCallbackBeforeWritingNextPacket() {
        List<byte[]> writes = new ArrayList<>();
        List<Runnable> scheduled = new ArrayList<>();
        List<Long> delays = new ArrayList<>();
        GattCommandQueue queue = new GattCommandQueue((id, value) -> {
            writes.add(value.clone());
            return true;
        }, new NoOpListener(), (action, delayMs) -> {
            scheduled.add(action);
            delays.add(delayMs);
        }, 1_500L);

        queue.replace(Arrays.asList(
                new GattCommandQueue.Packet(CHARACTERISTIC, new byte[]{1}, "one"),
                new GattCommandQueue.Packet(CHARACTERISTIC, new byte[]{2}, "two")));
        queue.onCharacteristicWrite(CHARACTERISTIC, BluetoothGatt.GATT_SUCCESS);

        assertEquals(1, writes.size());
        assertTrue(queue.isBusy());
        assertEquals(Arrays.asList(1_500L), delays);
        scheduled.get(0).run();
        assertEquals(2, writes.size());
    }

    private static final class NoOpListener implements GattCommandQueue.Listener {
        @Override public void onProgress(String label, int remaining) { }
        @Override public void onComplete() { }
        @Override public void onError(String message) { }
    }
}
