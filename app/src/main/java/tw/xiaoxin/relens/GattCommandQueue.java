package tw.xiaoxin.relens;

import android.bluetooth.BluetoothGatt;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Serializes GATT writes. A packet advances only after its write callback succeeds. */
final class GattCommandQueue {
    interface Writer { boolean write(UUID characteristic, byte[] value); }
    interface Scheduler { void schedule(Runnable action, long delayMs); }
    interface Listener {
        void onProgress(String label, int remaining);
        void onComplete();
        void onError(String message);
    }

    static final class Packet {
        final UUID characteristic;
        final byte[] value;
        final String label;

        Packet(UUID characteristic, byte[] value, String label) {
            this.characteristic = characteristic;
            this.value = Arrays.copyOf(value, value.length);
            this.label = label;
        }
    }

    private final Writer writer;
    private final Listener listener;
    private final Scheduler scheduler;
    private final long throttleMs;
    private final ArrayDeque<Packet> packets = new ArrayDeque<>();
    private Packet inFlight;
    private boolean waitingForThrottle;
    private long generation;

    GattCommandQueue(Writer writer, Listener listener) {
        this(writer, listener, (action, delayMs) -> action.run(), 0L);
    }

    GattCommandQueue(Writer writer, Listener listener, Scheduler scheduler, long throttleMs) {
        this.writer = writer;
        this.listener = listener;
        this.scheduler = scheduler;
        this.throttleMs = Math.max(0L, throttleMs);
    }

    synchronized void replace(List<Packet> commands) {
        generation++;
        packets.clear();
        packets.addAll(commands);
        inFlight = null;
        waitingForThrottle = false;
        writeNext();
    }

    synchronized void cancel() {
        generation++;
        packets.clear();
        inFlight = null;
        waitingForThrottle = false;
    }

    synchronized void onCharacteristicWrite(UUID characteristic, int status) {
        if (inFlight == null || !inFlight.characteristic.equals(characteristic)) return;
        if (status != BluetoothGatt.GATT_SUCCESS) {
            fail("GATT 寫入失敗（status=" + status + "，characteristic="
                    + inFlight.characteristic + "，length=" + inFlight.value.length
                    + "，stage=" + inFlight.label + "）");
            return;
        }
        inFlight = null;
        if (packets.isEmpty() || throttleMs == 0L) {
            writeNext();
            return;
        }
        waitingForThrottle = true;
        long expectedGeneration = generation;
        scheduler.schedule(() -> resumeAfterThrottle(expectedGeneration), throttleMs);
    }

    synchronized boolean isBusy() {
        return inFlight != null || waitingForThrottle || !packets.isEmpty();
    }

    private synchronized void resumeAfterThrottle(long expectedGeneration) {
        if (generation != expectedGeneration || !waitingForThrottle) return;
        waitingForThrottle = false;
        writeNext();
    }

    private void writeNext() {
        if (inFlight != null) return;
        Packet next = packets.pollFirst();
        if (next == null) {
            listener.onComplete();
            return;
        }
        inFlight = next;
        listener.onProgress(next.label, packets.size());
        if (!writer.write(next.characteristic, next.value)) {
            fail("Android 未接受 GATT 寫入請求");
        }
    }

    private void fail(String message) {
        generation++;
        packets.clear();
        inFlight = null;
        waitingForThrottle = false;
        listener.onError(message);
    }

    static List<Packet> longCommand(UUID characteristic, byte command, byte[] payload, String label) {
        ArrayList<Packet> result = new ArrayList<>();
        int firstLength = Math.min(payload.length, 17);
        byte[] first = new byte[firstLength + 3];
        first[0] = command;
        first[1] = (byte) ((payload.length >>> 8) & 0xff);
        first[2] = (byte) (payload.length & 0xff);
        System.arraycopy(payload, 0, first, 3, firstLength);
        result.add(new Packet(characteristic, first, label));

        int offset = firstLength;
        int sequence = 1;
        while (offset < payload.length) {
            int length = Math.min(18, payload.length - offset);
            byte[] continuation = new byte[length + 2];
            continuation[0] = command;
            continuation[1] = (byte) (sequence * 2);
            System.arraycopy(payload, offset, continuation, 2, length);
            result.add(new Packet(characteristic, continuation, label));
            offset += length;
            sequence++;
        }
        return result;
    }

    static Packet shortCommand(UUID characteristic, byte command, byte[] payload, String label) {
        byte[] frame = new byte[payload.length + 1];
        frame[0] = command;
        System.arraycopy(payload, 0, frame, 1, payload.length);
        return new Packet(characteristic, frame, label);
    }
}
