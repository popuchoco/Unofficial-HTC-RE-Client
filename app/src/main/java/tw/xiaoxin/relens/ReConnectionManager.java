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
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
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
    private static final UUID GC1_SERVICE = UUID.fromString("0000a000-0000-1000-8000-00805f9b34fb");
    private static final UUID GC1_SERVER_BAND = UUID.fromString("0000a201-0000-1000-8000-00805f9b34fb");
    private static final UUID GC1_PHONE_SSID = UUID.fromString("0000a301-0000-1000-8000-00805f9b34fb");
    private static final UUID GC1_PHONE_PASSWORD = UUID.fromString("0000a302-0000-1000-8000-00805f9b34fb");
    private static final UUID GC1_PHONE_CONFIG = UUID.fromString("0000a303-0000-1000-8000-00805f9b34fb");
    private static final UUID GC1_PHONE_RESULT = UUID.fromString("0000a304-0000-1000-8000-00805f9b34fb");
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
    private BluetoothGattCharacteristic gc1ServerBand;
    private BluetoothGattCharacteristic gc1PhoneSsid;
    private BluetoothGattCharacteristic gc1PhonePassword;
    private BluetoothGattCharacteristic gc1PhoneConfig;
    private BluetoothGattCharacteristic gc1PhoneResult;
    private final Gc1LongValueCodec.Collector gc1ResultCollector = new Gc1LongValueCodec.Collector();
    private int controlProfile;
    private BluetoothGattCharacteristic pendingNotificationCharacteristic;
    private WifiP2pGroup pendingGroup;
    private WifiP2pManager.Channel p2pChannel;
    private boolean reGattConnected;
    private boolean notificationsReady;
    private boolean awaitingConfigStatus;
    private boolean p2pStartRequested;
    private int notificationSubscriptionAttempts;
    private String cameraIp;
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
        IntentFilter bondFilter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        context.registerReceiver(bondReceiver, bondFilter);
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
    String cameraIp() { return cameraIp; }

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
        reGattConnected = false;
        notificationsReady = false;
        shortCommand = null;
        longCommand = null;
        clearGc1Characteristics();
        controlProfile = 0;
        pendingGroup = null;
        p2pStartRequested = false;
        cameraIp = null;
        pendingNotificationCharacteristic = null;
        notificationSubscriptionAttempts = 0;
        if (gatt != null) {
            try { gatt.disconnect(); gatt.close(); } catch (SecurityException ignored) { }
            gatt = null;
        }
        if (updateState) setBle("未連線");
    }

    void createP2pGroup() {
        if (!ReConnectionGate.canStartWifiDirect(reGattConnected,
                hasRequiredCharacteristics(), notificationsReady)) {
            setP2p("已阻擋：請先完成 HTC RE 的 BLE 控制通道連線");
            AppLog.w("P2P", "Create group blocked: verified RE GATT channel is not ready");
            return;
        }
        if (!wifiOn()) { setP2p("Wi-Fi 未開啟"); return; }
        WifiP2pManager manager = context.getSystemService(WifiP2pManager.class);
        if (manager == null) { setP2p("不支援 Wi-Fi Direct"); return; }
        if (p2pChannel == null) {
            p2pChannel = manager.initialize(context, context.getMainLooper(), () -> {
                p2pChannel = null;
                p2pStartRequested = false;
                setP2p("P2P channel 中斷");
            });
        }
        p2pStartRequested = true;
        setP2p("正在檢查 Wi-Fi Direct 群組");
        try {
            manager.requestGroupInfo(p2pChannel, group -> {
                if (group != null && P2pBootstrapPolicy.canReuseOwnerGroup(group.isGroupOwner(),
                        group.getNetworkName(), group.getPassphrase())) {
                    AppLog.i("P2P", "Reusing existing owner group");
                    onP2pGroupReady(group);
                    return;
                }
                createNewP2pGroup(manager, p2pChannel);
            });
        } catch (SecurityException error) {
            p2pStartRequested = false;
            setP2p("缺少 Wi-Fi 權限");
        }
    }

    private void createNewP2pGroup(WifiP2pManager manager, WifiP2pManager.Channel channel) {
        setP2p("正在建立 Wi-Fi Direct 群組");
        try {
            manager.createGroup(channel, new WifiP2pManager.ActionListener() {
                @Override public void onSuccess() {
                    manager.requestGroupInfo(channel, group -> {
                        if (group == null) { setP2p("無法讀取群組資訊"); return; }
                        onP2pGroupReady(group);
                    });
                }
                @Override public void onFailure(int reason) {
                    p2pStartRequested = false;
                    setP2p("建立失敗（" + reason + "）");
                }
            });
        } catch (SecurityException error) {
            p2pStartRequested = false;
            setP2p("缺少 Wi-Fi 權限");
        }
    }

    private void onP2pGroupReady(WifiP2pGroup group) {
        pendingGroup = group;
        int frequency = Build.VERSION.SDK_INT >= 29 ? group.getFrequency() : 0;
        setP2p("群組已建立" + (frequency > 0 ? " · " + frequency + " MHz" : ""));
        startWifiBootstrapIfReady();
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt current, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                reGattConnected = true;
                setBle("已連線");
                AppLog.i("BLE", "GATT connected status=" + status);
                try { current.discoverServices(); } catch (SecurityException ignored) { }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                reGattConnected = false;
                notificationsReady = false;
                shortCommand = null;
                longCommand = null;
                clearGc1Characteristics();
                controlProfile = 0;
                pendingNotificationCharacteristic = null;
                notificationSubscriptionAttempts = 0;
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
            BluetoothGattService gc1 = current.getService(GC1_SERVICE);
            if (gc1 != null) {
                controlProfile = 1;
                gc1ServerBand = gc1.getCharacteristic(GC1_SERVER_BAND);
                gc1PhoneSsid = gc1.getCharacteristic(GC1_PHONE_SSID);
                gc1PhonePassword = gc1.getCharacteristic(GC1_PHONE_PASSWORD);
                gc1PhoneConfig = gc1.getCharacteristic(GC1_PHONE_CONFIG);
                gc1PhoneResult = gc1.getCharacteristic(GC1_PHONE_RESULT);
                if (!hasRequiredCharacteristics()) { setBle("RE 第一代控制通道不完整"); return; }
                AppLog.i("BLE", "HTC RE control profile=A000");
                ensureBondThenEnableNotifications(current, gc1PhoneResult);
                return;
            }
            BluetoothGattService gc2 = current.getService(RE_SERVICE);
            if (gc2 != null) {
                controlProfile = 2;
                shortCommand = gc2.getCharacteristic(SHORT_COMMAND);
                longCommand = gc2.getCharacteristic(LONG_COMMAND);
                if (!hasRequiredCharacteristics()) { setBle("RE 第二代控制通道不完整"); return; }
                AppLog.i("BLE", "HTC RE control profile=5678");
                enableStatusNotifications(current, shortCommand);
                return;
            }
            StringBuilder available = new StringBuilder();
            for (BluetoothGattService service : current.getServices()) {
                if (available.length() > 0) available.append(',');
                available.append(service.getUuid().toString(), 4, 8);
            }
            AppLog.w("BLE", "No supported RE control service; available=" + available);
            setBle("找不到支援的 RE 控制服務");
        }

        @Override public void onDescriptorWrite(BluetoothGatt current, BluetoothGattDescriptor descriptor, int status) {
            if (!CCCD.equals(descriptor.getUuid())) return;
            notificationsReady = status == BluetoothGatt.GATT_SUCCESS;
            AppLog.i("BLE", "CCCD write status=" + status + " characteristic="
                    + descriptor.getCharacteristic().getUuid() + " properties="
                    + descriptor.getCharacteristic().getProperties());
            if (!notificationsReady) {
                if (GattSubscriptionPolicy.shouldRediscover(status, notificationSubscriptionAttempts)) {
                    setP2p("控制通道屬性已失效，正在重新探索服務…");
                    AppLog.w("BLE", "CCCD rejected with GATT_NOT_LONG; rediscovering services once");
                    main.postDelayed(() -> rediscoverServices(current, "CCCD status=11"), 300L);
                    return;
                }
                setP2p("無法訂閱 RE 狀態通知（status=" + status + "）");
                return;
            }
            AppLog.i("BLE", "Wi-Fi status notification ready");
            startWifiBootstrapIfReady();
            startP2pAutomatically();
        }

        @Override public void onCharacteristicWrite(BluetoothGatt current, BluetoothGattCharacteristic characteristic, int status) {
            commandQueue.onCharacteristicWrite(characteristic.getUuid(), status);
        }

        @Override public void onCharacteristicChanged(BluetoothGatt current, BluetoothGattCharacteristic characteristic) {
            handleStatusNotification(characteristic.getUuid(), characteristic.getValue());
        }
    };

    private final BroadcastReceiver bondReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context receiverContext, Intent intent) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null || gatt == null || !device.equals(gatt.getDevice())) return;
            int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
            int previous = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);
            AppLog.i("BLE", "Bond state " + previous + " -> " + state);
            if (state == BluetoothDevice.BOND_BONDED && pendingNotificationCharacteristic != null) {
                pendingNotificationCharacteristic = null;
                setP2p("BLE 配對完成，正在重新探索 RE 控制服務…");
                main.postDelayed(() -> rediscoverServices(gatt, "bond completed"), 400L);
            } else if (state == BluetoothDevice.BOND_NONE && previous == BluetoothDevice.BOND_BONDING) {
                pendingNotificationCharacteristic = null;
                setP2p("HTC RE BLE 配對失敗");
            }
        }
    };

    private void ensureBondThenEnableNotifications(BluetoothGatt current,
            BluetoothGattCharacteristic characteristic) {
        BluetoothDevice device = current.getDevice();
        try {
            int bondState = device.getBondState();
            AppLog.i("BLE", "A000 bond state=" + bondState);
            if (bondState == BluetoothDevice.BOND_BONDED) {
                enableStatusNotifications(current, characteristic);
                return;
            }
            pendingNotificationCharacteristic = characteristic;
            setP2p("正在建立 HTC RE BLE 配對");
            if (bondState == BluetoothDevice.BOND_NONE && !device.createBond()) {
                pendingNotificationCharacteristic = null;
                setP2p("無法啟動 HTC RE BLE 配對");
            }
        } catch (SecurityException error) {
            pendingNotificationCharacteristic = null;
            setP2p("缺少藍牙配對權限");
        }
    }

    private void enableStatusNotifications(BluetoothGatt current, BluetoothGattCharacteristic notificationCharacteristic) {
        BluetoothGattDescriptor descriptor = notificationCharacteristic.getDescriptor(CCCD);
        if (descriptor == null) { setP2p("找不到 RE 狀態通知描述元"); return; }
        try {
            if (!current.setCharacteristicNotification(notificationCharacteristic, true)) {
                setP2p("無法啟用 RE 狀態通知");
                return;
            }
            if (!GattSubscriptionPolicy.requiresDescriptorWrite(controlProfile)) {
                notificationsReady = true;
                AppLog.i("BLE", "A000 local notification registration ready; A304 CCCD write skipped");
                setP2p("RE 控制通道已就緒");
                startWifiBootstrapIfReady();
                startP2pAutomatically();
                return;
            }
            int properties = notificationCharacteristic.getProperties();
            boolean indicateOnly = (properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0
                    && (properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;
            byte[] cccdValue = indicateOnly ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
            notificationSubscriptionAttempts++;
            AppLog.i("BLE", "Enabling " + (indicateOnly ? "indication" : "notification")
                    + " characteristic=" + notificationCharacteristic.getUuid()
                    + " properties=" + properties + " permissions=" + notificationCharacteristic.getPermissions()
                    + " descriptorPermissions=" + descriptor.getPermissions()
                    + " valueLength=" + cccdValue.length
                    + " attempt=" + notificationSubscriptionAttempts);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                int result = current.writeDescriptor(descriptor, cccdValue);
                if (result != android.bluetooth.BluetoothStatusCodes.SUCCESS) {
                    setP2p("無法送出狀態通知設定（result=" + result + "）");
                }
            } else {
                descriptor.setValue(cccdValue);
                if (!current.writeDescriptor(descriptor)) setP2p("無法送出狀態通知設定");
            }
        } catch (SecurityException error) {
            setP2p("缺少藍牙連線權限");
        }
    }

    private void rediscoverServices(BluetoothGatt current, String reason) {
        if (current == null || current != gatt || !reGattConnected) return;
        clearGc1Characteristics();
        shortCommand = null;
        longCommand = null;
        controlProfile = 0;
        notificationsReady = false;
        try {
            boolean started = current.discoverServices();
            AppLog.i("BLE", "Service rediscovery reason=" + reason + " started=" + started);
            if (!started) setP2p("無法重新探索 RE 控制服務");
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
        List<GattCommandQueue.Packet> writes = new ArrayList<>();
        if (controlProfile == 1) {
            String country = normalizedCountry(Locale.getDefault().getCountry());
            writes.add(new GattCommandQueue.Packet(GC1_SERVER_BAND,
                    new byte[]{1, 0, (byte) country.charAt(1), (byte) country.charAt(0)}, "設定 RE 國別與頻段"));
            addGc1LongPackets(writes, GC1_PHONE_SSID, ssid.getBytes(StandardCharsets.UTF_8), "傳送 Wi-Fi SSID");
            addGc1LongPackets(writes, GC1_PHONE_PASSWORD, passphrase.getBytes(StandardCharsets.UTF_8), "傳送 Wi-Fi 密碼");
            int transaction = ((int) (System.nanoTime() & 0x0f) << 4) | 1;
            writes.add(new GattCommandQueue.Packet(GC1_PHONE_CONFIG,
                    new byte[]{(byte) transaction, 4, 1}, "設定 station 模式並加入群組"));
        } else {
            byte[] config = makeStationConfig(frequency, Locale.getDefault().getCountry());
            writes.addAll(GattCommandQueue.longCommand(LONG_COMMAND, WIFI_SET_SSID_REQUEST,
                    ssid.getBytes(StandardCharsets.UTF_8), "傳送 Wi-Fi SSID"));
            writes.addAll(GattCommandQueue.longCommand(LONG_COMMAND, WIFI_SET_PASSWORD_REQUEST,
                    passphrase.getBytes(StandardCharsets.UTF_8), "傳送 Wi-Fi 密碼"));
            writes.add(GattCommandQueue.shortCommand(SHORT_COMMAND, WIFI_CONFIG_REQUEST, config,
                    "設定 station 模式並加入群組"));
        }
        AppLog.i("BLE", "Starting serial Wi-Fi bootstrap; credentials redacted");
        commandQueue.replace(writes);
    }

    private void startP2pAutomatically() {
        if (!P2pBootstrapPolicy.shouldAutoStart(p2pStartRequested, pendingGroup != null)) return;
        main.post(this::createP2pGroup);
    }

    private byte[] makeStationConfig(int frequency, String country) {
        String normalizedCountry = normalizedCountry(country);
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
        BluetoothGattCharacteristic characteristic = findWritableCharacteristic(characteristicId);
        if (current == null || characteristic == null || !hasBluetoothPermission(Manifest.permission.BLUETOOTH_CONNECT)) return false;
        try {
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            characteristic.setValue(value);
            return current.writeCharacteristic(characteristic);
        } catch (SecurityException error) {
            return false;
        }
    }

    private void handleStatusNotification(UUID characteristicId, byte[] value) {
        if (controlProfile == 1 && GC1_PHONE_RESULT.equals(characteristicId)) {
            value = gc1ResultCollector.add(value);
            if (value == null) return;
            finishWifiConfig(value, 1);
            return;
        }
        if (value == null || value.length < 2 || value[0] != WIFI_CONFIG_STATUS_EVENT) return;
        finishWifiConfig(value, 1);
    }

    private void finishWifiConfig(byte[] value, int statusIndex) {
        if (value.length <= statusIndex) return;
        main.removeCallbacks(configTimeout);
        awaitingConfigStatus = false;
        int status = value[statusIndex] & 0xff;
        if (status != 0) {
            setP2p("RE 加入群組失敗（status=" + status + "）");
            return;
        }
        int ipIndex = statusIndex + 1;
        String ip = value.length >= ipIndex + 4
                ? (value[ipIndex] & 0xff) + "." + (value[ipIndex + 1] & 0xff) + "." + (value[ipIndex + 2] & 0xff) + "." + (value[ipIndex + 3] & 0xff)
                : "尚未提供";
        if (!"尚未提供".equals(ip) && !"0.0.0.0".equals(ip)) cameraIp = ip;
        setP2p("RE 已加入 · IP " + ip);
        setHttpState("待連線 · " + ip);
        AppLog.i("BLE", "Wi-Fi bootstrap completed; camera IP=" + ip);
    }

    private void addGc1LongPackets(List<GattCommandQueue.Packet> writes, UUID characteristic,
            byte[] payload, String label) {
        for (byte[] packet : Gc1LongValueCodec.fragment(payload)) {
            writes.add(new GattCommandQueue.Packet(characteristic, packet, label));
        }
    }

    private String normalizedCountry(String country) {
        return country == null || country.length() < 2 ? "TW" : country.toUpperCase(Locale.ROOT);
    }

    private boolean hasRequiredCharacteristics() {
        if (controlProfile == 1) return gc1ServerBand != null && gc1PhoneSsid != null
                && gc1PhonePassword != null && gc1PhoneConfig != null && gc1PhoneResult != null;
        if (controlProfile == 2) return shortCommand != null && longCommand != null;
        return false;
    }

    private BluetoothGattCharacteristic findWritableCharacteristic(UUID id) {
        if (SHORT_COMMAND.equals(id)) return shortCommand;
        if (LONG_COMMAND.equals(id)) return longCommand;
        if (GC1_SERVER_BAND.equals(id)) return gc1ServerBand;
        if (GC1_PHONE_SSID.equals(id)) return gc1PhoneSsid;
        if (GC1_PHONE_PASSWORD.equals(id)) return gc1PhonePassword;
        if (GC1_PHONE_CONFIG.equals(id)) return gc1PhoneConfig;
        return null;
    }

    private void clearGc1Characteristics() {
        gc1ServerBand = null;
        gc1PhoneSsid = null;
        gc1PhonePassword = null;
        gc1PhoneConfig = null;
        gc1PhoneResult = null;
        gc1ResultCollector.reset();
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
