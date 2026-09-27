package tw.xiaoxin.relens;

import org.json.JSONObject;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/** GC1/A000 native control transport. One command is in flight at a time. */
final class Gc1SocketClient implements Closeable {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int COMMAND_TIMEOUT_MS = 30_000;
    private static final byte[] CLIENT_GUID = {
            (byte) 0x87, (byte) 0x93, (byte) 0x82, (byte) 0x86,
            (byte) 0x82, (byte) 0x80, (byte) 0x8f, (byte) 0x8f,
            (byte) 0x86, (byte) 0x94, (byte) 0x88, (byte) 0x83,
            (byte) 0xf0, (byte) 0xf1, (byte) 0xf2, (byte) 0xf3
    };
    private static final byte[] CAMERA_GUID = {
            34, 45, 53, 38, 44, 37, 52, 36, 40, 54, 42, 37,
            (byte) 0xf3, (byte) 0xf2, (byte) 0xf1, (byte) 0xf0
    };

    private final String host;
    private Socket commandTx, commandRx, eventRx, fileRx, thumbnailRx;
    private OutputStream tx;
    private InputStream rx;
    private int sequence;
    private Handshake handshake;
    private volatile boolean closing;

    Gc1SocketClient(String host) { this.host = host; }

    synchronized JSONObject cameraInfo() throws Exception {
        ensureConnected();
        return new JSONObject()
                .put("transport", "GC1 native socket")
                .put("host", host)
                .put("protocol_version", handshake.protocolVersion)
                .put("firmware_version", handshake.firmwareVersion)
                .put("bootcode_version", handshake.bootcodeVersion)
                .put("mcu_version", handshake.mcuVersion);
    }

    synchronized JSONObject capture() throws Exception {
        ensureConnected();
        request(311, new byte[]{0});
        return ok("capture");
    }

    synchronized JSONObject startRecording() throws Exception {
        ensureConnected();
        request(106, new byte[]{0});
        return ok("record_start");
    }

    synchronized JSONObject stopRecording() throws Exception {
        ensureConnected();
        request(107, null);
        return ok("record_stop");
    }

    private JSONObject ok(String operation) throws Exception {
        return new JSONObject().put("ok", true).put("operation", operation);
    }

    private void ensureConnected() throws Exception {
        if (handshake != null && commandTx != null && commandTx.isConnected() && !commandTx.isClosed()) return;
        close();
        closing = false;
        try {
            commandTx = open(9000, 0);
            commandRx = open(9001, COMMAND_TIMEOUT_MS);
            eventRx = open(9002, 5500);
            fileRx = open(9003, 0);
            thumbnailRx = open(9004, 0);
            tx = commandTx.getOutputStream();
            rx = commandRx.getInputStream();
            sequence = 0;
            ByteBuffer body = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
            body.put(CLIENT_GUID).putInt(1);
            Response response = exchange(501, body.array());
            if (response.body.length < 30) throw new IOException("GC1 handshake 回應長度異常: " + response.body.length);
            ByteBuffer data = ByteBuffer.wrap(response.body).order(ByteOrder.LITTLE_ENDIAN);
            requireSuccess(data, 501);
            byte[] guid = new byte[16];
            data.get(guid);
            if (!java.util.Arrays.equals(guid, CAMERA_GUID)) throw new IOException("GC1 handshake GUID 不符");
            int protocol = data.getInt();
            if (protocol != 1) throw new IOException("不支援的 GC1 protocol version: " + protocol);
            handshake = new Handshake(protocol, data.getInt(), data.getInt(), data.get() & 0xff);
            AppLog.i("GC1", "Socket handshake ready host=" + host + " fw=" + handshake.firmwareVersion
                    + " boot=" + handshake.bootcodeVersion + " mcu=" + handshake.mcuVersion);
            startEventDrain();
        } catch (Exception error) {
            close();
            throw error;
        }
    }

    private Socket open(int port, int timeout) throws IOException {
        Socket socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(timeout);
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        AppLog.i("GC1", "Connected " + host + ":" + port);
        return socket;
    }

    private void request(int command, byte[] body) throws Exception {
        Response response = exchange(command, body);
        ByteBuffer data = ByteBuffer.wrap(response.body).order(ByteOrder.LITTLE_ENDIAN);
        requireSuccess(data, command);
    }

    private Response exchange(int command, byte[] body) throws Exception {
        int current = sequence++;
        tx.write(encodeRequest(command, current, 0, body));
        tx.flush();
        AppLog.i("GC1", String.format(Locale.ROOT, "TX command=%d seq=%d payload=%s", command, current, hex(body)));

        byte[] headerBytes = readExact(rx, 16);
        ByteBuffer header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN);
        int responseCommand = header.getInt();
        int length = header.getInt();
        int responseSequence = header.getInt();
        int flags = header.getInt();
        if (responseCommand != command) throw new IOException("GC1 command 不符: expected=" + command + " actual=" + responseCommand);
        if (responseSequence != current) throw new IOException("GC1 sequence 不符: expected=" + current + " actual=" + responseSequence);
        if (length < 16 || length > 1_048_576) throw new IOException("GC1 response 長度異常: " + length);
        byte[] responseBody = readExact(rx, length - 16);
        AppLog.i("GC1", String.format(Locale.ROOT, "RX command=%d seq=%d flags=%08X payload=%s",
                responseCommand, responseSequence, flags, hex(responseBody)));
        return new Response(responseBody);
    }

    private static void requireSuccess(ByteBuffer body, int command) throws IOException {
        if (!body.hasRemaining()) throw new IOException("GC1 command " + command + " 缺少狀態碼");
        int status = body.get() & 0xff;
        if (status != 0) throw new IOException("GC1 command " + command + " 失敗 status=" + status);
    }

    private void startEventDrain() {
        final Socket socket = eventRx;
        Thread thread = new Thread(() -> {
            try {
                InputStream input = socket.getInputStream();
                while (!closing && !socket.isClosed()) {
                    try {
                        ByteBuffer header = ByteBuffer.wrap(readExact(input, 12)).order(ByteOrder.LITTLE_ENDIAN);
                        int event = header.getInt(), length = header.getInt(), sequence = header.getInt();
                        if (length < 12 || length > 1036) throw new IOException("GC1 event 長度異常: " + length);
                        byte[] body = readExact(input, length - 12);
                        AppLog.i("GC1-EVENT", String.format(Locale.ROOT,
                                "event=0x%04X seq=%d payload=%s", event, sequence, hex(body)));
                    } catch (SocketTimeoutException timeout) {
                        AppLog.i("GC1-EVENT", "heartbeat wait timeout");
                    }
                }
            } catch (Exception error) {
                if (!closing) AppLog.e("GC1-EVENT", error.toString());
            }
        }, "Gc1EventReader");
        thread.setDaemon(true);
        thread.start();
    }

    private static byte[] readExact(InputStream input, int count) throws IOException {
        byte[] bytes = new byte[count];
        int offset = 0;
        while (offset < count) {
            int read = input.read(bytes, offset, count - offset);
            if (read < 0) throw new EOFException("GC1 socket closed");
            offset += read;
        }
        return bytes;
    }

    static byte[] encodeRequest(int command, int sequence, int flags, byte[] body) {
        int bodyLength = body == null ? 0 : body.length;
        ByteBuffer packet = ByteBuffer.allocate(16 + bodyLength).order(ByteOrder.LITTLE_ENDIAN);
        packet.putInt(command).putInt(16 + bodyLength).putInt(sequence).putInt(flags);
        if (body != null) packet.put(body);
        return packet.array();
    }

    private static String hex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "<empty>";
        StringBuilder out = new StringBuilder();
        for (byte value : bytes) {
            if (out.length() > 0) out.append(' ');
            out.append(String.format(Locale.ROOT, "%02X", value & 0xff));
        }
        return out.toString();
    }

    @Override public synchronized void close() {
        closing = true;
        handshake = null;
        closeSocket(commandTx); closeSocket(commandRx); closeSocket(eventRx);
        closeSocket(fileRx); closeSocket(thumbnailRx);
        commandTx = commandRx = eventRx = fileRx = thumbnailRx = null;
        tx = null; rx = null;
    }

    private static void closeSocket(Socket socket) {
        if (socket != null) try { socket.close(); } catch (IOException ignored) { }
    }

    private static final class Response {
        final byte[] body;
        Response(byte[] body) { this.body = body; }
    }

    private static final class Handshake {
        final int protocolVersion, firmwareVersion, bootcodeVersion, mcuVersion;
        Handshake(int protocolVersion, int firmwareVersion, int bootcodeVersion, int mcuVersion) {
            this.protocolVersion = protocolVersion;
            this.firmwareVersion = firmwareVersion;
            this.bootcodeVersion = bootcodeVersion;
            this.mcuVersion = mcuVersion;
        }
    }
}
