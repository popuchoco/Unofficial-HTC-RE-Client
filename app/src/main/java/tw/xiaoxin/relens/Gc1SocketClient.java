package tw.xiaoxin.relens;

import org.json.JSONObject;
import org.json.JSONArray;

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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/** GC1/A000 native control transport. One command is in flight at a time. */
final class Gc1SocketClient implements Closeable {
    interface TransferProgress { void onProgress(long done, long total); }
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

    synchronized String startLiveView() throws Exception {
        ensureConnected();
        Response response = exchange(130, null);
        ByteBuffer data = ByteBuffer.wrap(response.body).order(ByteOrder.LITTLE_ENDIAN);
        requireSuccess(data, 130);
        byte[] uri = new byte[data.remaining()];
        data.get(uri);
        String value = liveViewUri(host, uri);
        if (uri.length == 0) AppLog.i("GC1-RTSP", "Command 130 returned status only; using GC1 live URI");
        AppLog.i("GC1-RTSP", "Live view URI=" + value);
        return value;
    }

    synchronized void stopLiveView() throws Exception {
        if (handshake == null) return;
        int previousTimeout = commandRx.getSoTimeout();
        try {
            commandRx.setSoTimeout(3_000);
            request(131, null);
        } catch (IOException timeout) {
            close();
            throw timeout;
        } finally {
            if (commandRx != null && !commandRx.isClosed()) commandRx.setSoTimeout(previousTimeout);
        }
    }

    synchronized JSONArray media() throws Exception {
        ensureConnected();
        Response response = exchange(401, encodeMediaQuery(0, 200));
        ByteBuffer data = ByteBuffer.wrap(response.body).order(ByteOrder.LITTLE_ENDIAN);
        requireSuccess(data, 401);
        if (data.remaining() % 9 != 0) throw new IOException("GC1 media list 長度異常: " + data.remaining());
        List<MediaSummary> summaries = new ArrayList<>();
        while (data.remaining() >= 9) {
            int handle = data.getInt();
            int time = data.getShort() & 0xffff;
            int date = data.getShort() & 0xffff;
            int type = data.get() & 0xff;
            summaries.add(new MediaSummary(handle, fatTimeMillis(date, time), type));
        }
        Collections.sort(summaries, (a, b) -> Long.compare(b.createdAt, a.createdAt));
        JSONArray result = new JSONArray();
        for (MediaSummary summary : summaries) result.put(mediaDetail(summary));
        AppLog.i("GC1-MEDIA", "Listed items=" + result.length());
        return result;
    }

    synchronized void download(int handle, long offset, long total, OutputStream output,
                               TransferProgress progress) throws Exception {
        ensureConnected();
        boolean completed = false;
        try {
        if (offset < 0 || offset > 0xffffffffL) throw new IOException("GC1 download offset 超出範圍");
        ByteBuffer body = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(handle).putInt((int) offset);
        int current = sequence++;
        tx.write(encodeRequest(405, current, 0, body.array()));
        tx.flush();
        AppLog.i("GC1", "TX command=405 seq=" + current + " handle=" + handle + " offset=" + offset);
        InputStream input = fileRx.getInputStream();
        long done = offset;
        Frame frame = readFrame(input, 405, current);
        ByteBuffer payload = ByteBuffer.wrap(frame.body).order(ByteOrder.LITTLE_ENDIAN);
        if (frame.flags == 0) {
            requireSuccess(payload, 405);
            byte[] bytes = new byte[payload.remaining()];
            payload.get(bytes); output.write(bytes); done += bytes.length; progress.onProgress(done, total);
        } else {
          boolean first = true;
          long wireOffset = 0;
          while (true) {
            long fragmentOffset = Integer.toUnsignedLong(payload.getInt());
            int fragmentLength = payload.getInt();
            int wireLength = fragmentLength;
            if (fragmentOffset != wireOffset) throw new IOException("GC1 fragment offset 不連續: " + fragmentOffset + " != " + wireOffset);
            if (first) { requireSuccess(payload, 405); fragmentLength--; first = false; }
            if (fragmentLength < 0 || payload.remaining() != fragmentLength)
                throw new IOException("GC1 fragment length 不符: " + fragmentLength + "/" + payload.remaining());
            byte[] bytes = new byte[fragmentLength]; payload.get(bytes); output.write(bytes);
            done += bytes.length; progress.onProgress(done, total);
            wireOffset = nextFragmentWireOffset(wireOffset, wireLength);
            if ((frame.flags & 0x04000000) != 0) throw new IOException("GC1 已取消下載");
            if ((frame.flags & 0x02000000) == 0) break;
            frame = readFrame(input, 405, current);
            payload = ByteBuffer.wrap(frame.body).order(ByteOrder.LITTLE_ENDIAN);
          }
        }
        output.flush();
        AppLog.i("GC1-MEDIA", "Download completed handle=" + handle + " bytes=" + done);
        completed = true;
        } finally {
            if (!completed) {
                AppLog.w("GC1-MEDIA", "Resetting GC1 session after interrupted file transfer");
                close();
            }
        }
    }

    static long nextFragmentWireOffset(long currentOffset, int declaredLength) {
        if (declaredLength < 0) throw new IllegalArgumentException("negative fragment length");
        return currentOffset + declaredLength;
    }

    static String liveViewUri(String host, byte[] responseUri) throws IOException {
        String value = trimCString(responseUri);
        if (value.isEmpty()) return "rtsp://" + host + "/live";
        if (!value.startsWith("rtsp://")) throw new IOException("GC1 未回傳有效 RTSP URI: " + value);
        return value;
    }

    private JSONObject mediaDetail(MediaSummary summary) throws Exception {
        ByteBuffer request = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(summary.handle);
        Response response = exchange(404, request.array());
        ByteBuffer data = ByteBuffer.wrap(response.body).order(ByteOrder.LITTLE_ENDIAN);
        requireSuccess(data, 404);
        if (data.remaining() < 72) throw new IOException("GC1 media detail 長度異常: " + data.remaining());
        int handle = data.getInt();
        if (handle != summary.handle) throw new IOException("GC1 media handle 不符");
        byte[] folderBytes = new byte[9], nameBytes = new byte[13], dateBytes = new byte[20];
        data.get(folderBytes); data.get(nameBytes);
        int type = data.get() & 0xff;
        data.get(dateBytes);
        long size = Integer.toUnsignedLong(data.getInt());
        long duration = Integer.toUnsignedLong(data.getInt());
        long extra1 = data.getLong(), extra2 = data.getLong();
        int state = data.get() & 0xff;
        String folder = trimCString(folderBytes), name = trimCString(nameBytes);
        return new JSONObject()
                .put("id", Integer.toUnsignedString(handle))
                .put("handle", Integer.toUnsignedLong(handle))
                .put("name", name)
                .put("path", "/DCIM/" + folder + "/" + name)
                .put("type", mediaType(type))
                .put("created_at", trimCString(dateBytes))
                .put("size", size).put("duration_ms", duration)
                .put("extra_1", extra1).put("extra_2", extra2).put("state", state);
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

        Frame frame = readFrame(rx, command, current);
        AppLog.i("GC1", String.format(Locale.ROOT, "RX command=%d seq=%d flags=%08X payload=%s",
                command, current, frame.flags, hex(frame.body)));
        return new Response(frame.body);
    }

    private static Frame readFrame(InputStream input, int command, int sequence) throws IOException {
        ByteBuffer header = ByteBuffer.wrap(readExact(input, 16)).order(ByteOrder.LITTLE_ENDIAN);
        int responseCommand = header.getInt(), length = header.getInt();
        int responseSequence = header.getInt(), flags = header.getInt();
        if (responseCommand != command) throw new IOException("GC1 command 不符: expected=" + command + " actual=" + responseCommand);
        if (responseSequence != sequence) throw new IOException("GC1 sequence 不符: expected=" + sequence + " actual=" + responseSequence);
        if (length < 16 || length > 16 * 1024 * 1024) throw new IOException("GC1 response 長度異常: " + length);
        return new Frame(flags, readExact(input, length - 16));
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

    static byte[] encodeMediaQuery(int offset, int count) {
        if (offset < 0 || offset > 0xffff || count < 1 || count > 0xffff)
            throw new IllegalArgumentException("media query range");
        return ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) 0).putShort((short) offset).putShort((short) count).put((byte) 1).array();
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

    private static String trimCString(byte[] bytes) {
        int length = 0;
        while (length < bytes.length && bytes[length] != 0) length++;
        return new String(bytes, 0, length, java.nio.charset.StandardCharsets.UTF_8).trim();
    }

    private static String mediaType(int type) {
        if (type == 0) return "photo";
        if (type == 3) return "video";
        if (type == 8) return "timelapse";
        if (type == 9) return "slow_motion";
        return "unknown_" + type;
    }

    private static long fatTimeMillis(int date, int time) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(((date & 0xfe00) >> 9) + 1980, ((date & 0x01e0) >> 5) - 1,
                date & 0x1f, (time & 0xf800) >> 11, (time & 0x07e0) >> 5, (time & 0x1f) * 2);
        return calendar.getTimeInMillis();
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

    private static final class Frame {
        final int flags; final byte[] body;
        Frame(int flags, byte[] body) { this.flags = flags; this.body = body; }
    }

    private static final class MediaSummary {
        final int handle, type; final long createdAt;
        MediaSummary(int handle, long createdAt, int type) {
            this.handle = handle; this.createdAt = createdAt; this.type = type;
        }
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
