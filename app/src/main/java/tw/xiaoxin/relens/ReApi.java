package tw.xiaoxin.relens;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class ReApi implements Closeable {
    interface Progress { void onProgress(long done, long total); }
    private final String base;
    private final Gc1SocketClient gc1;
    ReApi(String host) { base = "http://" + host + ":3000"; gc1 = new Gc1SocketClient(host); }
    String baseUrl() { return base; }
    JSONObject cameraInfo() throws Exception { return gc1.cameraInfo(); }
    JSONObject capture() throws Exception { return gc1.capture(); }
    JSONObject startRecording() throws Exception { return gc1.startRecording(); }
    JSONObject stopRecording() throws Exception { return gc1.stopRecording(); }
    JSONArray media() throws Exception { return gc1.media(); }
    JSONObject storage() throws Exception { return json("GET", "/v1/system/storage/freespace", null); }
    JSONObject serial() throws Exception { return json("GET", "/v1/system/serial_num", null); }
    String startLiveView() throws Exception { return gc1.startLiveView(); }
    void stopLiveView() throws Exception { gc1.stopLiveView(); }
    @Override public void close() { gc1.close(); }
    void download(JSONObject item, File target, Progress progress) throws Exception {
        long existing = target.exists() ? target.length() : 0;
        long total = item.optLong("size", 0);
        int handle = (int) item.getLong("handle");
        try (RandomAccessFile file = new RandomAccessFile(target, "rw")) {
            if (existing > total && total > 0) { file.setLength(0); existing = 0; }
            file.seek(existing);
            final RandomAccessFile destination = file;
            gc1.download(handle, existing, total, new OutputStream() {
                @Override public void write(int value) throws IOException { destination.write(value); }
                @Override public void write(byte[] bytes, int offset, int length) throws IOException { destination.write(bytes, offset, length); }
            }, progress::onProgress);
        }
    }
    void download(JSONObject item, OutputStream target, Progress progress) throws Exception {
        long total = item.optLong("size", 0);
        int handle = (int) item.getLong("handle");
        gc1.download(handle, 0, total, target, progress::onProgress);
    }
    private JSONObject json(String method, String path, JSONObject body) throws Exception {
        HttpURLConnection c = open(base + path, method);
        if (body != null) { c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json"); try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));} }
        int code = c.getResponseCode(); InputStream raw = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = raw == null ? "" : read(raw); c.disconnect();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code + (text.isEmpty()?"":": "+text));
        if (text.trim().isEmpty()) return new JSONObject().put("ok", true);
        try { return new JSONObject(text); } catch (Exception ignored) { return new JSONObject().put("response", text); }
    }
    private static HttpURLConnection open(String url, String method) throws Exception { HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setRequestMethod(method); c.setConnectTimeout(5000); c.setReadTimeout(15000); c.setRequestProperty("Accept","application/json"); return c; }
    private static String read(InputStream in) throws Exception { try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){StringBuilder b=new StringBuilder(); String s; while((s=r.readLine())!=null)b.append(s); return b.toString();} }
    private static String enc(String s) { try{return URLEncoder.encode(s,"UTF-8");}catch(Exception e){return s;} }
}
