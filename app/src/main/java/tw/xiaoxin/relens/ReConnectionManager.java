package tw.xiaoxin.relens;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

final class ReConnectionManager {
    interface Listener { void onStatus(String status); void onFound(String name, String address); }

    private static final UUID RE_SERVICE = UUID.fromString("00005678-0000-1000-8000-00805f9b34fb");
    private static final UUID SHORT_COMMAND = UUID.fromString("0000cf01-0000-1000-8000-00805f9b34fb");
    private static final UUID LONG_COMMAND = UUID.fromString("0000cf02-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final byte WIFI_CONFIG_REQUEST = 0x21;
    private static final byte WIFI_SET_SSID_REQUEST = 0x22;
    private static final byte WIFI_SET_PASSWORD_REQUEST = 0x23;
    private static final byte WIFI_CONFIG_STATUS_EVENT = 0x26;
    private static final long CONFIG_TIMEOUT_MS = 60_000L;

    private static ReConnectionManager instance;
    static synchronized ReConnectionManager get(Context context) {
        if (instance == null) instance = new ReConnectionManager(context.getApplicationContext());
        return instance;
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final GattCommandQueue commandQueue;
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic shortCommand;
    private BluetoothGattCharacteristic longCommand;
    private WifiP2pGroup pendingGroup;
    private boolean notificationsReady;
    private boolean awaitingConfigStatus;
    private String ble = "未連線";
    private String p2p = "未建立";
    private String http = "未連線";
    private String foundAddress;

    private final Runnable configTimeout = () -> {
        if (!awaitingConfigStatus) return;
        awaitingConfigStatus = false;
        setP2p("RE 加入逾時，請重試");
        AppLog.w("BLE", "Wi-Fi config status timeout");
    };

    private ReConnectionManager(Context context) {
        this.context = context;
        commandQueue = new GattCommandQueue(this::writeGattPacket, new GattCommandQueue.Listener() {
            @Override public void onProgress(String label, int remaining) {
                setP2p(label + "（尚有 " + remaining + " 個封包）");
            }
            @Override public void onComplete() {
                awaitingConfigStatus = true;
                setP2p("設定已送出，等待 RE 回報 IP");
                main.removeCallbacks(configTimeout);
                main.postDelayed(configTimeout, CONFIG_TIMEOUT_MS);
            }
            @Override public void onError(String message) {
                awaitingConfigStatus = false;
                setP2p(message);
                AppLog.w("BLE", message);
            }
        });
    }

    void addListener(Listener listener) { listeners.addIfAbsent(listener); emit(); }
    void removeListener(Listener listener) { listeners.remove(listener); }
    String bleState() { return ble; }
    String p2pState() { return p2p; }
    String httpState() { return http; }
    String foundAddress() { return foundAddress; }

    boolean bluetoothOn() {
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        return manager != null && manager.getAdapter() != null && manager.getAdapter().isEnabled();
    }

    boolean wifiOn() {
        WifiManager manager = context.getSystemService(WifiManager.class);
        return manager != null && manager.isWifiEnabled();
    }

    void setHttpState(String state) { http = state; AppLog.i("HTTP", state); emit(); }

    void scanBle() {
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        if (!bluetoothOn()) { setBle("藍牙未開啟"); return; }
        if (!hasBluetoothPermission(Manifest.permission.BLUETOOTH_SCAN)) { setBle("缺少藍牙掃描權限"); return; }
        scanner = manager.getAdapter().getBluetoothLeScanner();
        if (scanner == null) { setBle("無法取得掃描器"); return; }
        stopScan();
        foundAddress = null;
        setBle("正在掃描");
        scanCallback = new ScanCallback() {
            @Override public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                String name = null;
                try { name = device.getName(); } catch (SecurityException ignored) { }
                byte[] record = result.getScanRecord() == null ? null : result.getScanRecord().getBytes();
                if (!ReAdvertisementMatcher.matches(name, record)) return;
                if (name == null || name.isEmpty()) name = "HTC RE";
                foundAddress = device.getAddress();
                setBle("找到 " + name);
                for (Listener listener : listeners) listener.onFound(name, foundAddress);
                stopScan();
            }
        };
        try { scanner.startScan(scanCallback); }
        catch (SecurityException error) { setBle("缺少藍牙掃描權限"); return; }
        main.postDelayed(this::stopScan, 10_000L);
    }

    void connectFound() {
        if (foundAddress == null) { setBle("請先掃描裝置"); return; }
        if (!hasBluetoothPermission(Manifest.permission.BLUETOOTH_CONNECT)) { setBle("缺少藍牙連線權限"); return; }
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        BluetoothDevice device = manager.getAdapter().getRemoteDevice(foundAddress);
        disconnect(false);
        setBle("正在連線");
        try { gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE); }
        catch (SecurityException error) { setBle("缺少藍牙連線權限"); }
    }

    void disconnect() { disconnect(true); }

    private void disconnect(boolean updateState) {
        stopScan();
        main.removeCallbacks(configTimeout);
        commandQueue.cancel();
        awaitingConfigStatus = false;
        notificationsReady = false;
        shortCommand = null;
        longCommand = null;
        pendingGroup = null;
        if (gatt != null) {
            try { gatt.disconnect(); gatt.close(); } catch (SecurityException ignored) { }
            gatt = null;
        }
        if (updateState) setBle("未連線");
    }

    void createP2pGroup() {
        if (!wifiOn()) { setP2p("Wi-Fi 未開啟"); return; }
        WifiP2pManager manager = context.getSystemService(WifiP2pManager.class);
        if (manager == null) { setP2p("不支援 Wi-Fi Direct"); return; }
        WifiP2pManager.Channel channel = manager.initialize(context, context.getMainLooper(), () -> setP2p("P2P channel 中斷"));
        setP2p("正在建立群組");
        try {
            manager.createGroup(channel, new WifiP2pManager.ActionListener() {
                @Override public void onSuccess() {
                    manager.requestGroupInfo(channel, group -> {
                        if (group == null) { setP2p("無法讀取群組資訊"); return; }
                        pendingGroup = group;
                        int frequency = Build.VERSION.SDK_INT >= 29 ? group.getFrequency() : 0;
                        setP2p("群組已建立" + (frequency > 0 ? " · " + frequency + " MHz" : ""));
                        startWifiBootstrapIfReady();
                    });
                }
                @Override public void onFailure(int reason) { setP2p("建立失敗（" + reason + "）"); }
            });
        } catch (SecurityException error) {
            setP2p("缺少 Wi-Fi 權限");
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt current, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                setBle("已連線");
                AppLog.i("BLE", "GATT connected status=" + status);
                try { current.discoverServices(); } catch (SecurityException ignored) { }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                setBle("連線中斷");
                AppLog.w("BLE", "GATT disconnected status=" + status + describeGattStatus(status));
                commandQueue.cancel();
                main.removeCallbacks(configTimeout);
                awaitingConfigStatus = false;
                ConnectionMonitorService.notifyDisconnect(context);
            }
        }

        @Override public void onServicesDiscovered(BluetoothGatt current, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) { setBle("探索服務失敗"); return; }
            BluetoothGattService service = current.getService(RE_SERVICE);
            if (service == null) { setBle("找不到 RE 控制服務"); return; }
            shortCommand = service.getCharacteristic(SHORT_COMMAND);
            longCommand = service.getCharacteristic(LONG_COMMAND);
            if (shortCommand == null || longCommand == null) { setBle("RE 控制通道不完整"); return; }
            enableStatusNotifications(current);
        }

        @Override public void onDescriptorWrite(BluetoothGatt current, BluetoothGattDescriptor descriptor, int status) {
            if (!CCCD.equals(descriptor.getUuid())) return;
            notificationsReady = status == BluetoothGatt.GATT_SUCCESS;
            if (!notificationsReady) { setP2p("無法訂閱 RE 狀態通知"); return; }
            AppLog.i("BLE", "Wi-Fi status notification ready");
            startWifiBootstrapIfReady();
        }

        @Override public void onCharacteristicWrite(BluetoothGatt current, BluetoothGattCharacteristic characteristic, int status) {
            commandQueue.onCharacteristicWrite(characteristic.getUuid(), status);
        }

        @Override public void onCharacteristicChanged(BluetoothGatt current, BluetoothGattCharacteristic characteristic) {
            handleStatusNotification(characteristic.getValue());
        }
    };

    private void enableStatusNotifications(BluetoothGatt current) {
        BluetoothGattDescriptor descriptor = shortCommand.getDescriptor(CCCD);
        if (descriptor == null) { setP2p("找不到 RE 狀態通知描述元"); return; }
        try {
            if (!current.setCharacteristicNotification(shortCommand, true)) {
                setP2p("無法啟用 RE 狀態通知");
                return;
            }
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (!current.writeDescriptor(descriptor)) setP2p("無法送出狀態通知設定");
        } catch (SecurityException error) {
            setP2p("缺少藍牙連線權限");
        }
    }

    private void startWifiBootstrapIfReady() {
        if (pendingGroup == null || !notificationsReady || commandQueue.isBusy() || awaitingConfigStatus) return;
        String ssid = pendingGroup.getNetworkName();
        String passphrase = pendingGroup.getPassphrase();
        if (ssid == null || ssid.isEmpty() || passphrase == null || passphrase.isEmpty()) {
            setP2p("群組缺少 SSID 或密碼");
            return;
        }
        int frequency = Build.VERSION.SDK_INT >= 29 ? pendingGroup.getFrequency() : 0;
        byte[] config = makeStationConfig(frequency, Locale.getDefault().getCountry());
        List<GattCommandQueue.Packet> writes = new ArrayList<>();
        writes.addAll(GattCommandQueue.longCommand(LONG_COMMAND, WIFI_SET_SSID_REQUEST,
                ssid.getBytes(StandardCharsets.UTF_8), "傳送 Wi-Fi SSID"));
        writes.addAll(GattCommandQueue.longCommand(LONG_COMMAND, WIFI_SET_PASSWORD_REQUEST,
                passphrase.getBytes(StandardCharsets.UTF_8), "傳送 Wi-Fi 密碼"));
        writes.add(GattCommandQueue.shortCommand(SHORT_COMMAND, WIFI_CONFIG_REQUEST, config,
                "設定 station 模式並加入群組"));
        AppLog.i("BLE", "Starting serial Wi-Fi bootstrap; credentials redacted");
        commandQueue.replace(writes);
    }

    private byte[] makeStationConfig(int frequency, String country) {
        String normalizedCountry = country == null || country.length() < 2 ? "TW" : country.toUpperCase(Locale.ROOT);
        byte[] config = new byte[10];
        config[0] = 1; // station mode
        config[1] = (byte) normalizedCountry.charAt(1);
        config[2] = (byte) normalizedCountry.charAt(0);
        config[3] = frequency >= 2500 ? (byte) 1 : (byte) 0; // 5 GHz / 2.4 GHz band
        config[4] = 4; // WPA2
        config[5] = frequencyToChannel(frequency);
        // config[6..9] remain zero to request DHCP; a future static-IP path may populate them.
        return config;
    }

    private byte frequencyToChannel(int frequency) {
        if (frequency == 2484) return 14;
        if (frequency >= 2412 && frequency <= 2472) return (byte) ((frequency - 2407) / 5);
        if (frequency >= 4915 && frequency <= 5825) return (byte) ((frequency - 5000) / 5);
        return 0;
    }

    private boolean writeGattPacket(UUID characteristicId, byte[] value) {
        BluetoothGatt current = gatt;
        BluetoothGattCharacteristic characteristic = characteristicId.equals(SHORT_COMMAND) ? shortCommand : longCommand;
        if (current == null || characteristic == null || !hasBluetoothPermission(Manifest.permission.BLUETOOTH_CONNECT)) return false;
        try {
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            characteristic.setValue(value);
            return current.writeCharacteristic(characteristic);
        } catch (SecurityException error) {
            return false;
        }
    }

    private void handleStatusNotification(byte[] value) {
        if (value == null || value.length < 2 || value[0] != WIFI_CONFIG_STATUS_EVENT) return;
        main.removeCallbacks(configTimeout);
        awaitingConfigStatus = false;
        int status = value[1] & 0xff;
        if (status != 0) {
            setP2p("RE 加入群組失敗（status=" + status + "）");
            return;
        }
        String ip = value.length >= 6
                ? (value[2] & 0xff) + "." + (value[3] & 0xff) + "." + (value[4] & 0xff) + "." + (value[5] & 0xff)
                : "尚未提供";
        setP2p("RE 已加入 · IP " + ip);
        setHttpState("待連線 · " + ip);
        AppLog.i("BLE", "Wi-Fi bootstrap completed; camera IP=" + ip);
    }

    private boolean hasBluetoothPermission(String permission) {
        return Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private String describeGattStatus(int status) {
        if (status == 147) return " (connection timeout / target unavailable)";
        if (status == 133) return " (generic Android GATT error)";
        return "";
    }

    private void stopScan() {
        try {
            if (hasBluetoothPermission(Manifest.permission.BLUETOOTH_SCAN) && scanner != null && scanCallback != null) {
                scanner.stopScan(scanCallback);
            }
        } catch (SecurityException ignored) { }
        scanCallback = null;
    }

    private void setBle(String state) { ble = state; AppLog.i("BLE", state); emit(); }
    private void setP2p(String state) { p2p = state; AppLog.i("P2P", state); emit(); }
    private void emit() {
        String status = "BLE " + ble + " · Wi-Fi Direct " + p2p;
        for (Listener listener : listeners) listener.onStatus(status);
    }
}
