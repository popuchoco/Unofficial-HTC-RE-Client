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
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.nio.charset.StandardCharsets;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

final class ReConnectionManager {
    interface Listener { void onStatus(String status); void onFound(String name, String address); }

    private static final UUID RE_SERVICE = UUID.fromString("00005678-0000-1000-8000-00805f9b34fb");
    private static final UUID SHORT_COMMAND = UUID.fromString("0000cf01-0000-1000-8000-00805f9b34fb");
    private static final UUID LONG_COMMAND = UUID.fromString("0000cf02-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final UUID GC1_SERVICE = UUID.fromString("0000a000-0000-1000-8000-00805f9b34fb");
    private static final UUID DEVICE_INFORMATION_SERVICE = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    private static final UUID FIRMWARE_REVISION = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
    private static final UUID GC1_BOOT_READY = UUID.fromString("0000a101-0000-1000-8000-00805f9b34fb");
    private static final UUID GC1_HARDWARE_STATUS = UUID.fromString("0000a102-0000-1000-8000-00805f9b34fb");
    private static final UUID GC1_BOOT_COMMAND = UUID.fromString("0000a107-0000-1000-8000-00805f9b34fb");
    private static final UUID GC1_PASSWORD_REQUEST = UUID.fromString("0000a105-0000-1000-8000-00805f9b34fb");
    private static final UUID GC1_PASSWORD_RESULT = UUID.fromString("0000a106-0000-1000-8000-00805f9b34fb");
    private static final UUID GC1_SERVER_BAND = UUID.fromString("0000a201-0000-1000-8000-00805f9b34fb");
    private static final UUID GC1_PHONE_SSID = UUID.fromString("0000a301-0000-1000-8000-00805f9b34fb");
    private static final UUID GC1_PHONE_PASSWORD = UUID.fromString("0000a302-0000-1000-8000-00805f9b34fb");
    private static final UUID GC1_PHONE_CONFIG = UUID.fromString("0000a303-0000-1000-8000-00805f9b34fb");
    private static final UUID GC1_PHONE_RESULT = UUID.fromString("0000a304-0000-1000-8000-00805f9b34fb");
    private static final UUID GC1_CAMERA_ERROR = UUID.fromString("0000a805-0000-1000-8000-00805f9b34fb");
    private static final UUID GC1_NOTIFY_PRIMARY = UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb");
    private static final UUID GC1_NOTIFY_SECONDARY = UUID.fromString("0000ae02-0000-1000-8000-00805f9b34fb");
    private static final byte WIFI_CONFIG_REQUEST = 0x21;
    private static final byte WIFI_SET_SSID_REQUEST = 0x22;
    private static final byte WIFI_SET_PASSWORD_REQUEST = 0x23;
    private static final byte WIFI_CONFIG_STATUS_EVENT = 0x26;
    private static final int BOOT_MAX_ATTEMPTS = 5;
    private static final long GC1_GATT_THROTTLE_MS = 1_500L;
    private static final long SERVICE_DISCOVERY_STABILIZATION_MS = 3_000L;
    private static final long CONFIG_TIMEOUT_MS = 60_000L;
    private static final int GROUP_INFO_MAX_ATTEMPTS = 12;
    private static final long GROUP_INFO_RETRY_MS = 750L;
    private static final int PASSWORD_NONE = 0;
    private static final int PASSWORD_VERIFY_EXISTING = 1;
    private static final int PASSWORD_SETUP_VERIFY_DEFAULT = 2;
    private static final int PASSWORD_SETUP_CHANGE = 3;
    private static final int PASSWORD_SETUP_VERIFY_NEW = 4;
    private static final String FACTORY_PASSWORD = "00000000";

    private static ReConnectionManager instance;
    static synchronized ReConnectionManager get(Context context) {
        if (instance == null) instance = new ReConnectionManager(context.getApplicationContext());
        return instance;
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Random random = new SecureRandom();
    private final GattCommandQueue commandQueue;
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private Runnable scanTimeout;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic shortCommand;
    private BluetoothGattCharacteristic longCommand;
    private BluetoothGattCharacteristic gc1BootReady;
    private BluetoothGattCharacteristic gc1BootCommand;
    private BluetoothGattCharacteristic gc1PasswordRequest;
    private BluetoothGattCharacteristic gc1PasswordResult;
    private BluetoothGattCharacteristic gc1ServerBand;
    private BluetoothGattCharacteristic gc1PhoneSsid;
    private BluetoothGattCharacteristic gc1PhonePassword;
    private BluetoothGattCharacteristic gc1PhoneConfig;
    private BluetoothGattCharacteristic gc1PhoneResult;
    private BluetoothGattCharacteristic gc1NotifyPrimary;
    private BluetoothGattCharacteristic gc1NotifySecondary;
    private BluetoothGattCharacteristic gc1FirmwareRevision;
    private int controlProfile;
    private BluetoothGattCharacteristic pendingNotificationCharacteristic;
    private WifiP2pGroup pendingGroup;
    private WifiP2pManager.Channel p2pChannel;
    private boolean reGattConnected;
    private boolean connectionAttemptInFlight;
    private boolean notificationsReady;
    private boolean awaitingConfigStatus;
    private boolean p2pStartRequested;
    private boolean bootReadInFlight;
    private boolean securityProbeInFlight;
    private boolean passwordHandshakeInFlight;
    private int passwordOperation;
    private boolean passwordWriteComplete;
    private int pendingPasswordResult = -1;
    private String pendingNewPassword;
    private boolean bootWakeInFlight;
    private int bootWakeAttempts;
    private boolean bootPreparationComplete;
    private boolean firmwareReadInFlight;
    private int gc1BleFirmwareVersion = -1;
    private A000ConnectionState a000State = A000ConnectionState.IDLE;
    private long transactionSequence;
    private long lastBootGattDispatchAtMs = Long.MIN_VALUE;
    private boolean gattConnectedSignal;
    private boolean aclConnectedSignal;
    private boolean serviceDiscoveryStarted;
    private int emptyServiceDiscoveryRetries;
    private int notificationSubscriptionAttempts;
    private int multiplexSubscriptionAttempts;
    private String cameraIp;
    private String ble = "未連線";
    private String p2p = "未建立";
    private String http = "未連線";
    private String foundAddress;
    private String cameraPassword = "";
    private volatile DatagramSocket ipDiscoverySocket;
    private final Object ipDiscoveryLock = new Object();
    private volatile long ipDiscoveryGeneration;

    private final Runnable configTimeout = () -> {
        if (!awaitingConfigStatus) return;
        awaitingConfigStatus = false;
        setP2p("RE 加入逾時，請重試");
        AppLog.w("BLE", "Wi-Fi config status timeout");
    };
    private final Runnable bootTimeout = () -> {
        if (!bootWakeInFlight || bootPreparationComplete) return;
        bootWakeInFlight = false;
        Gc1BootProtocol.TimeoutOperation operation =
                Gc1BootProtocol.timeoutOperation(gc1BleFirmwareVersion);
        trace("BOOT_TIMEOUT", GC1_BOOT_READY, null, "attempt=" + bootWakeAttempts
                + " next=" + operation);
        if (bootWakeAttempts >= BOOT_MAX_ATTEMPTS) {
            transition(A000ConnectionState.ERROR, "boot attempts exhausted");
            setP2p("RE 啟動回覆逾時，請重新連線再試");
        } else if (operation == Gc1BootProtocol.TimeoutOperation.READ_A101) {
            main.postDelayed(this::readGc1BootState, remainingBootGattThrottleMs());
        } else {
            main.postDelayed(this::beginGc1WakeAttempt, remainingBootGattThrottleMs());
        }
    };
    private final Runnable serviceDiscoveryFallback = () -> startServiceDiscovery("ACL fallback");

    private ReConnectionManager(Context context) {
        this.context = context;
        IntentFilter bondFilter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        bondFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        context.registerReceiver(bondReceiver, bondFilter);
        commandQueue = new GattCommandQueue(this::writeGattPacket, new GattCommandQueue.Listener() {
            @Override public void onProgress(String label, int remaining) {
                setP2p(label + "（尚有 " + remaining + " 個封包）");
            }
            @Override public void onComplete() {
                awaitingConfigStatus = true;
                transition(A000ConnectionState.IP_WAITING, "Wi-Fi bootstrap writes complete");
                setP2p("設定已送出，等待 RE 回報 IP");
                main.removeCallbacks(configTimeout);
                main.postDelayed(configTimeout, CONFIG_TIMEOUT_MS);
                if (controlProfile == 1) startGc1IpDiscovery();
            }
            @Override public void onError(String message) {
                awaitingConfigStatus = false;
                setP2p(message);
                AppLog.w("BLE", message);
            }
        }, (action, delayMs) -> main.postDelayed(action, delayMs), GC1_GATT_THROTTLE_MS);
    }

    void addListener(Listener listener) { listeners.addIfAbsent(listener); emit(); }
    void removeListener(Listener listener) { listeners.remove(listener); }
    String bleState() { return ble; }
    String p2pState() { return p2p; }
    String httpState() { return http; }
    String foundAddress() { return foundAddress; }
    String cameraIp() { return cameraIp; }

    void setCameraPassword(String password) {
        cameraPassword = password == null ? "" : password;
    }

    boolean retryPasswordVerification(String password) {
        setCameraPassword(password);
        if (gatt == null || !reGattConnected || gc1PasswordRequest == null
                || gc1PasswordResult == null) {
            setP2p("請先連線 HTC RE，再驗證相機密碼");
            return false;
        }
        if (passwordHandshakeInFlight) {
            setP2p("RE 密碼作業仍在進行，請稍候");
            return false;
        }
        setP2p("正在驗證 RE 相機密碼");
        return writePasswordVerification(gatt, cameraPassword, PASSWORD_VERIFY_EXISTING);
    }

    boolean beginInitialPasswordSetup(String newPassword, String confirmation) {
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 15) {
            setP2p("新密碼必須為 8–15 個字元");
            return false;
        }
        if (!newPassword.equals(confirmation)) {
            setP2p("兩次輸入的新密碼不一致");
            return false;
        }
        for (int index = 0; index < newPassword.length(); index++) {
            char value = newPassword.charAt(index);
            if (value < 0x20 || value > 0x7e) {
                setP2p("新密碼目前僅支援英數字與半形符號");
                return false;
            }
        }
        if (gatt == null || !reGattConnected || gc1PasswordRequest == null
                || gc1PasswordResult == null) {
            setP2p("請先連線已重設的 HTC RE");
            return false;
        }
        if (passwordHandshakeInFlight) {
            setP2p("RE 密碼作業仍在進行，請稍候");
            return false;
        }
        pendingNewPassword = newPassword;
        setP2p("正在確認 RE 原廠密碼狀態");
        return writePasswordVerification(gatt, FACTORY_PASSWORD, PASSWORD_SETUP_VERIFY_DEFAULT);
    }

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

            @Override public void onScanFailed(int errorCode) {
                if (scanCallback != this) return;
                if (scanTimeout != null) main.removeCallbacks(scanTimeout);
                scanTimeout = null;
                scanCallback = null;
                String reason = describeScanFailure(errorCode);
                AppLog.w("BLE", "Scan failed errorCode=" + errorCode + " reason=" + reason);
                setBle("掃描失敗（" + reason + "）");
            }
        };
        ScanCallback activeScan = scanCallback;
        try {
            scanner.startScan(activeScan);
            AppLog.i("BLE", "Scan started");
        }
        catch (SecurityException error) { setBle("缺少藍牙掃描權限"); return; }
        scanTimeout = () -> {
            if (scanCallback != activeScan) return;
            AppLog.i("BLE", "Scan timeout");
            stopScan();
        };
        main.postDelayed(scanTimeout, 10_000L);
    }

    void connectFound() {
        if (foundAddress == null) { setBle("請先掃描裝置"); return; }
        if (!hasBluetoothPermission(Manifest.permission.BLUETOOTH_CONNECT)) { setBle("缺少藍牙連線權限"); return; }
        if (connectionAttemptInFlight || gatt != null) {
            AppLog.i("BLE", "Connect ignored: a GATT session is already active state=" + a000State);
            return;
        }
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        BluetoothDevice device = manager.getAdapter().getRemoteDevice(foundAddress);
        setBle("正在連線");
        resetDiscoveryGate();
        connectionAttemptInFlight = true;
        try { gatt = device.connectGatt(context, false, gattCallback); }
        catch (SecurityException error) {
            connectionAttemptInFlight = false;
            setBle("缺少藍牙連線權限");
        }
    }

    void disconnect() { disconnect(true); }

    private void disconnect(boolean updateState) {
        stopScan();
        main.removeCallbacks(configTimeout);
        main.removeCallbacks(serviceDiscoveryFallback);
        commandQueue.cancel();
        awaitingConfigStatus = false;
        reGattConnected = false;
        connectionAttemptInFlight = false;
        notificationsReady = false;
        shortCommand = null;
        longCommand = null;
        clearGc1Characteristics();
        controlProfile = 0;
        pendingGroup = null;
        p2pStartRequested = false;
        cameraIp = null;
        bootReadInFlight = false;
        securityProbeInFlight = false;
        passwordHandshakeInFlight = false;
        passwordOperation = PASSWORD_NONE;
        passwordWriteComplete = false;
        pendingPasswordResult = -1;
        pendingNewPassword = null;
        bootWakeInFlight = false;
        bootPreparationComplete = false;
        resetDiscoveryGate();
        pendingNotificationCharacteristic = null;
        notificationSubscriptionAttempts = 0;
        multiplexSubscriptionAttempts = 0;
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
                int frequency = group != null && Build.VERSION.SDK_INT >= 29
                        ? group.getFrequency() : 0;
                if (group != null && P2pBootstrapPolicy.canReuseOwnerGroup(group.isGroupOwner(),
                        group.getNetworkName(), group.getPassphrase(), frequency)) {
                    AppLog.i("P2P", "Reusing existing owner group");
                    onP2pGroupReady(group);
                    return;
                }
                if (group != null && group.isGroupOwner()
                        && !P2pBootstrapPolicy.isGc1CompatibleFrequency(frequency)) {
                    AppLog.w("P2P", "Removing incompatible owner group frequency="
                            + frequency + " MHz before GC1 bootstrap");
                    removeGroupThenCreate(manager, p2pChannel);
                    return;
                }
                createNewP2pGroup(manager, p2pChannel);
            });
        } catch (SecurityException error) {
            p2pStartRequested = false;
            setP2p("缺少 Wi-Fi 權限");
        }
    }

    private void removeGroupThenCreate(WifiP2pManager manager,
            WifiP2pManager.Channel channel) {
        try {
            manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override public void onSuccess() {
                    createNewP2pGroup(manager, channel);
                }
                @Override public void onFailure(int reason) {
                    p2pStartRequested = false;
                    setP2p("無法移除不相容的 5 GHz 群組（" + reason + "）");
                    AppLog.w("P2P", "removeGroup failed reason=" + reason);
                }
            });
        } catch (SecurityException error) {
            p2pStartRequested = false;
            setP2p("缺少 Wi-Fi 權限");
        }
    }

    private void createNewP2pGroup(WifiP2pManager manager, WifiP2pManager.Channel channel) {
        setP2p("正在建立 Wi-Fi Direct 群組");
        try {
            WifiP2pManager.ActionListener listener = new WifiP2pManager.ActionListener() {
                @Override public void onSuccess() {
                    AppLog.i("P2P", "createGroup accepted; requestedBand="
                            + (Build.VERSION.SDK_INT >= 29 ? "2.4GHz" : "system default"));
                    requestCreatedGroupInfo(manager, channel, 1);
                }
                @Override public void onFailure(int reason) {
                    p2pStartRequested = false;
                    setP2p("建立失敗（" + reason + "）");
                }
            };
            if (Build.VERSION.SDK_INT >= 29) {
                WifiP2pConfig config = new WifiP2pConfig.Builder()
                        .setNetworkName(P2pBootstrapPolicy.createGroupNetworkName(random))
                        .setPassphrase(P2pBootstrapPolicy.createGroupPassphrase(random))
                        .setGroupOperatingBand(WifiP2pConfig.GROUP_OWNER_BAND_2GHZ)
                        .build();
                manager.createGroup(channel, config, listener);
            } else {
                manager.createGroup(channel, listener);
            }
        } catch (SecurityException error) {
            p2pStartRequested = false;
            setP2p("缺少 Wi-Fi 權限");
        } catch (IllegalArgumentException | IllegalStateException error) {
            p2pStartRequested = false;
            setP2p("Wi-Fi Direct 群組設定無效");
            AppLog.w("P2P", "Group config rejected=" + error.getClass().getSimpleName());
        }
    }

    private void requestCreatedGroupInfo(WifiP2pManager manager,
            WifiP2pManager.Channel channel, int attempt) {
        if (!p2pStartRequested || channel != p2pChannel) return;
        try {
            manager.requestGroupInfo(channel, group -> {
                if (!p2pStartRequested || channel != p2pChannel) return;
                if (group != null && group.isGroupOwner()
                        && group.getNetworkName() != null && !group.getNetworkName().isEmpty()
                        && group.getPassphrase() != null && !group.getPassphrase().isEmpty()) {
                    AppLog.i("P2P", "Owner group details ready attempt=" + attempt);
                    onP2pGroupReady(group);
                    return;
                }
                if (attempt >= GROUP_INFO_MAX_ATTEMPTS) {
                    p2pStartRequested = false;
                    setP2p("群組已建立，但逾時仍無法取得 SSID／密碼");
                    AppLog.w("P2P", "Group info unavailable after attempts=" + attempt
                            + " groupPresent=" + (group != null));
                    return;
                }
                setP2p("群組協商中（" + attempt + "/" + GROUP_INFO_MAX_ATTEMPTS + "）");
                AppLog.i("P2P", "Group info not ready attempt=" + attempt
                        + " groupPresent=" + (group != null));
                main.postDelayed(() -> requestCreatedGroupInfo(manager, channel, attempt + 1),
                        GROUP_INFO_RETRY_MS);
            });
        } catch (SecurityException error) {
            p2pStartRequested = false;
            setP2p("缺少 Wi-Fi 權限");
        }
    }

    private void onP2pGroupReady(WifiP2pGroup group) {
        int frequency = Build.VERSION.SDK_INT >= 29 ? group.getFrequency() : 0;
        if (!P2pBootstrapPolicy.isGc1CompatibleFrequency(frequency)) {
            pendingGroup = null;
            p2pStartRequested = false;
            setP2p("群組不相容（" + frequency + " MHz）");
            AppLog.w("P2P", "Rejecting incompatible GC1 group frequency=" + frequency);
            return;
        }
        pendingGroup = group;
        p2pStartRequested = false;
        setP2p("群組已建立" + (frequency > 0 ? " · " + frequency + " MHz" : ""));
        transition(A000ConnectionState.P2P_GROUP_READY, "owner group credentials ready");
        startWifiBootstrapIfReady();
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt current, int status, int newState) {
            if (current != gatt) {
                AppLog.i("BLE", "Ignoring callback from replaced GATT status=" + status
                        + " state=" + newState);
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    try { current.close(); } catch (SecurityException ignored) { }
                }
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectionAttemptInFlight = false;
                if (reGattConnected) {
                    AppLog.i("BLE", "Ignoring duplicate connected callback state=" + a000State);
                    return;
                }
                reGattConnected = true;
                gattConnectedSignal = true;
                transition(A000ConnectionState.GATT_CONNECTED, "GATT callback status=" + status);
                setBle("已連線");
                AppLog.i("BLE", "GATT connected status=" + status);
                maybeStartServiceDiscovery();
                main.removeCallbacks(serviceDiscoveryFallback);
                main.postDelayed(serviceDiscoveryFallback, 3_000L);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectionAttemptInFlight = false;
                reGattConnected = false;
                notificationsReady = false;
                shortCommand = null;
                longCommand = null;
                clearGc1Characteristics();
                controlProfile = 0;
                pendingNotificationCharacteristic = null;
                notificationSubscriptionAttempts = 0;
                multiplexSubscriptionAttempts = 0;
                main.removeCallbacks(serviceDiscoveryFallback);
                resetDiscoveryGate();
                transition(A000ConnectionState.IDLE, "GATT disconnected status=" + status);
                setBle("連線中斷");
                AppLog.w("BLE", "GATT disconnected status=" + status + describeGattStatus(status));
                commandQueue.cancel();
                main.removeCallbacks(configTimeout);
                awaitingConfigStatus = false;
                try { current.close(); } catch (SecurityException ignored) { }
                if (gatt == current) gatt = null;
                ConnectionMonitorService.notifyDisconnect(context);
            }
        }

        @Override public void onServicesDiscovered(BluetoothGatt current, int status) {
            if (current != gatt) {
                AppLog.i("BLE", "Ignoring services callback from replaced GATT status=" + status);
                return;
            }
            if (controlProfile != 0) {
                AppLog.i("BLE", "Ignoring duplicate services callback profile=" + controlProfile);
                return;
            }
            if (status == BluetoothGatt.GATT_SUCCESS && current.getServices().isEmpty()
                    && GattDiscoveryGate.shouldRetryEmpty(emptyServiceDiscoveryRetries)) {
                emptyServiceDiscoveryRetries++;
                serviceDiscoveryStarted = false;
                AppLog.w("BLE", "Service discovery returned empty; retrying after stabilization");
                main.postDelayed(() -> startServiceDiscovery("empty service retry"), 1_500L);
                return;
            }
            if (status != BluetoothGatt.GATT_SUCCESS) { setBle("探索服務失敗"); return; }
            BluetoothGattService gc1 = current.getService(GC1_SERVICE);
            if (gc1 != null) {
                controlProfile = 1;
                BluetoothGattService deviceInformation = current.getService(DEVICE_INFORMATION_SERVICE);
                gc1FirmwareRevision = deviceInformation == null ? null
                        : deviceInformation.getCharacteristic(FIRMWARE_REVISION);
                gc1BootReady = gc1.getCharacteristic(GC1_BOOT_READY);
                gc1BootCommand = gc1.getCharacteristic(GC1_BOOT_COMMAND);
                gc1PasswordRequest = gc1.getCharacteristic(GC1_PASSWORD_REQUEST);
                gc1PasswordResult = gc1.getCharacteristic(GC1_PASSWORD_RESULT);
                gc1ServerBand = gc1.getCharacteristic(GC1_SERVER_BAND);
                gc1PhoneSsid = gc1.getCharacteristic(GC1_PHONE_SSID);
                gc1PhonePassword = gc1.getCharacteristic(GC1_PHONE_PASSWORD);
                gc1PhoneConfig = gc1.getCharacteristic(GC1_PHONE_CONFIG);
                gc1PhoneResult = gc1.getCharacteristic(GC1_PHONE_RESULT);
                gc1NotifyPrimary = gc1.getCharacteristic(GC1_NOTIFY_PRIMARY);
                gc1NotifySecondary = gc1.getCharacteristic(GC1_NOTIFY_SECONDARY);
                if (!hasRequiredCharacteristics()) { setBle("RE 第一代控制通道不完整"); return; }
                transition(A000ConnectionState.SERVICES_READY, "A000 and 2A26 discovered");
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
            UUID source = descriptor.getCharacteristic().getUuid();
            if (controlProfile == 1) {
                trace("DESCRIPTOR_CALLBACK", source, descriptor.getValue(),
                        "descriptor=2902 status=" + status);
            }
            if (controlProfile == 1 && GC1_PASSWORD_RESULT.equals(source)) {
                AppLog.i("BLE", "A106 password CCCD status=" + status);
                if (status != BluetoothGatt.GATT_SUCCESS
                        && !GattSubscriptionPolicy.isLegacyAttributeNotLong(status)) {
                    passwordHandshakeInFlight = false;
                    setP2p("無法啟用 RE 密碼驗證通道（status=" + status + "）");
                    return;
                }
                main.postDelayed(() -> writePasswordVerification(current, cameraPassword,
                        PASSWORD_VERIFY_EXISTING), GC1_GATT_THROTTLE_MS);
                transition(A000ConnectionState.PASSWORD_VERIFYING, "A106 subscribed");
                return;
            }
            if (controlProfile == 1 && (GC1_NOTIFY_PRIMARY.equals(source)
                    || GC1_NOTIFY_SECONDARY.equals(source))) {
                AppLog.i("BLE", "GC1 multiplex CCCD status=" + status + " characteristic=" + source);
                boolean legacyNotLong = GattSubscriptionPolicy.isLegacyAttributeNotLong(status);
                if (legacyNotLong) {
                    AppLog.w("BLE", "Accepting local multiplex registration for legacy status=11 characteristic="
                            + source);
                } else if (status != BluetoothGatt.GATT_SUCCESS) {
                    if (GattSubscriptionPolicy.shouldRetryMultiplex(status,
                            multiplexSubscriptionAttempts)) {
                        int nextAttempt = multiplexSubscriptionAttempts + 1;
                        AppLog.w("BLE", "GC1 multiplex retry scheduled characteristic=" + source
                                + " status=" + status + " nextAttempt=" + nextAttempt);
                        BluetoothGattCharacteristic retryCharacteristic = GC1_NOTIFY_PRIMARY.equals(source)
                                ? gc1NotifyPrimary : gc1NotifySecondary;
                        String retryLabel = GC1_NOTIFY_PRIMARY.equals(source) ? "AE01" : "AE02";
                        main.postDelayed(() -> writeNotificationDescriptor(current,
                                retryCharacteristic, retryLabel), 2_000L);
                        return;
                    }
                    setP2p("無法訂閱 RE multiplex 通道（status=" + status + "）");
                    return;
                }
                if (GC1_NOTIFY_PRIMARY.equals(source)) {
                    multiplexSubscriptionAttempts = 0;
                    main.postDelayed(() -> writeNotificationDescriptor(current,
                            gc1NotifySecondary, "AE02"), 2_000L);
                    return;
                }
                multiplexSubscriptionAttempts = 0;
                notificationsReady = true;
                transition(A000ConnectionState.BLE_FW_READING, "AE01/AE02 ready");
                AppLog.i("BLE", "GC1 AE01/AE02 multiplex notifications ready");
                setP2p("RE 事件通道已就緒，正在讀取 BLE 韌體版本");
                main.postDelayed(() -> readGc1FirmwareRevision(current), GC1_GATT_THROTTLE_MS);
                return;
            }
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
            if (GC1_PASSWORD_REQUEST.equals(characteristic.getUuid())) {
                AppLog.i("BLE", "A105 password operation=" + passwordOperation
                        + " write status=" + status);
                if (status != BluetoothGatt.GATT_SUCCESS
                        && !GattSubscriptionPolicy.isLegacyAttributeNotLong(status)) {
                    resetPasswordOperation();
                    setP2p("RE 密碼作業失敗（status=" + status + "）");
                    return;
                }
                passwordWriteComplete = true;
                if (passwordOperation == PASSWORD_SETUP_CHANGE) {
                    String nextPassword = pendingNewPassword;
                    resetPasswordOperationState(false);
                    main.postDelayed(() -> {
                        setP2p("新密碼已寫入，正在重新驗證");
                        writePasswordVerification(current, nextPassword, PASSWORD_SETUP_VERIFY_NEW);
                    }, GC1_GATT_THROTTLE_MS);
                } else {
                    completePasswordVerificationIfReady(current);
                }
                return;
            }
            if (GC1_BOOT_COMMAND.equals(characteristic.getUuid())) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    bootWakeInFlight = false;
                    main.removeCallbacks(bootTimeout);
                    setP2p("喚醒 RE 失敗（status=" + status + "）");
                    AppLog.w("BLE", "Boot wake write failed status=" + status);
                    return;
                }
                if (bootPreparationComplete) return;
                AppLog.i("BLE", "RE wake command accepted; waiting for A101 ready event=17");
                trace("WRITE_CALLBACK", GC1_BOOT_COMMAND, characteristic.getValue(),
                        "status=" + status + " throttleMs=" + GC1_GATT_THROTTLE_MS);
                main.postDelayed(() -> readGc1WakeEcho(current), remainingBootGattThrottleMs());
                return;
            }
            if (controlProfile == 1) {
                trace("WRITE_CALLBACK", characteristic.getUuid(), characteristic.getValue(),
                        "status=" + status);
            }
            commandQueue.onCharacteristicWrite(characteristic.getUuid(), status);
        }

        @Override public void onCharacteristicRead(BluetoothGatt current,
                BluetoothGattCharacteristic characteristic, int status) {
            if (FIRMWARE_REVISION.equals(characteristic.getUuid())) {
                firmwareReadInFlight = false;
                byte[] value = characteristic.getValue();
                int version = status == BluetoothGatt.GATT_SUCCESS
                        ? Gc1FirmwareVersion.parse(value) : -1;
                trace("READ_CALLBACK", FIRMWARE_REVISION, value,
                        "status=" + status + " parsed=" + version);
                if (version < 0) {
                    transition(A000ConnectionState.ERROR, "invalid 2A26 firmware revision");
                    setP2p("無法辨識 RE BLE 韌體版本，已停止連線流程");
                    return;
                }
                gc1BleFirmwareVersion = version;
                transition(A000ConnectionState.BLE_FW_KNOWN,
                        "BLE FW=" + version + " branch=" + Gc1BootProtocol.branch(version));
                setP2p("RE BLE 韌體版本 " + version + "，控制初始化完成");
                startP2pAutomatically();
                return;
            }
            if (GC1_BOOT_COMMAND.equals(characteristic.getUuid())) {
                byte[] echo = characteristic.getValue();
                AppLog.i("BLE", "A107 wake echo status=" + status + " length="
                        + (echo == null ? 0 : echo.length) + " first="
                        + (echo == null || echo.length == 0 ? -1 : echo[0] & 0xff));
                boolean matches = status == BluetoothGatt.GATT_SUCCESS && echo != null
                        && echo.length == 1 && echo[0] == 0x01;
                trace("READ_CALLBACK", GC1_BOOT_COMMAND, echo,
                        "status=" + status + " echoMatches=" + matches);
                if (!matches) {
                    main.removeCallbacks(bootTimeout);
                    bootWakeInFlight = false;
                    transition(A000ConnectionState.ERROR, "A107 echo mismatch");
                    setP2p("RE 喚醒命令回讀不一致，已停止本次流程");
                }
                return;
            }
            if (!GC1_BOOT_READY.equals(characteristic.getUuid())) return;
            bootReadInFlight = false;
            if (securityProbeInFlight) {
                securityProbeInFlight = false;
                byte[] probeValue = characteristic.getValue();
                int probeLength = probeValue == null ? 0 : probeValue.length;
                AppLog.i("BLE", "A101 security probe callback status=" + status
                        + " valueLength=" + probeLength);
                if (status != BluetoothGatt.GATT_SUCCESS
                        && !GattSubscriptionPolicy.isLegacyAttributeNotLong(status)) {
                    AppLog.w("BLE", "A101 security probe failed status=" + status);
                    setP2p("RE 安全通道協商中（status=" + status + "）");
                    return;
                }
                BluetoothGattCharacteristic pending = pendingNotificationCharacteristic;
                pendingNotificationCharacteristic = null;
                AppLog.i("BLE", "A101 security probe compatibility path; enabling multiplex notifications");
                if (pending != null) {
                    main.postDelayed(() -> enableStatusNotifications(current, pending), 500L);
                }
                return;
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                setP2p("無法讀取 RE 啟動狀態（status=" + status + "）");
                AppLog.w("BLE", "Boot-ready read failed status=" + status);
                return;
            }
            byte[] value = characteristic.getValue();
            boolean ready = Gc1BootState.isReady(value);
            trace("READ_CALLBACK", GC1_BOOT_READY, value,
                    "status=" + status + " ready=" + ready);
            AppLog.i("BLE", "A101 boot-ready=" + ready + " length="
                    + (value == null ? 0 : value.length) + " first="
                    + (value == null || value.length == 0 ? -1 : value[0] & 0xff));
            if (ready) {
                main.removeCallbacks(bootTimeout);
                bootWakeInFlight = false;
                bootWakeAttempts = 0;
                bootPreparationComplete = true;
                transition(A000ConnectionState.BOOT_READY, "A101 read ready");
                main.postDelayed(ReConnectionManager.this::startWifiBootstrapIfReady, 1500L);
                return;
            }
            if (bootWakeAttempts >= BOOT_MAX_ATTEMPTS) {
                setP2p("RE 啟動回覆逾時，請重新連線再試");
                AppLog.w("BLE", "A101 remained not ready after attempts=" + bootWakeAttempts);
                return;
            }
            main.postDelayed(ReConnectionManager.this::beginGc1WakeAttempt,
                    GC1_GATT_THROTTLE_MS);
        }

        @Override public void onCharacteristicChanged(BluetoothGatt current, BluetoothGattCharacteristic characteristic) {
            UUID id = characteristic.getUuid();
            byte[] value = characteristic.getValue();
            if (GC1_PASSWORD_RESULT.equals(id)) {
                int result = RePasswordProtocol.verificationResult(value);
                AppLog.i("BLE", "A106 password verification result=" + result
                        + " length=" + (value == null ? 0 : value.length));
                if (passwordOperation == PASSWORD_SETUP_CHANGE) return;
                pendingPasswordResult = result;
                completePasswordVerificationIfReady(current);
                return;
            }
            if ((GC1_NOTIFY_PRIMARY.equals(id) || GC1_NOTIFY_SECONDARY.equals(id))
                    && value != null && value.length > 0) {
                UUID mapped = gc1EventCharacteristic(value[0]);
                byte[] payload = new byte[value.length - 1];
                System.arraycopy(value, 1, payload, 0, payload.length);
                if (mapped != null) {
                    AppLog.i("BLE", "GC1 multiplex event=" + (value[0] & 0xff)
                            + " mapped=" + mapped + " length=" + payload.length);
                    trace("EVENT", mapped, payload, "source=" + id
                            + " event=0x" + String.format(Locale.US, "%02X", value[0] & 0xff));
                    handleStatusNotification(mapped, payload);
                } else {
                    AppLog.i("BLE", "GC1 multiplex event=" + (value[0] & 0xff)
                            + " unmapped length=" + (value.length - 1));
                    trace("EVENT_UNMAPPED", null, payload, "source=" + id
                            + " event=0x" + String.format(Locale.US, "%02X", value[0] & 0xff));
                }
                return;
            }
            handleStatusNotification(id, value);
        }
    };

    private final BroadcastReceiver bondReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context receiverContext, Intent intent) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null || gatt == null || !device.equals(gatt.getDevice())) return;
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) {
                aclConnectedSignal = true;
                AppLog.i("BLE", "ACL connected signal received");
                maybeStartServiceDiscovery();
                return;
            }
            int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
            int previous = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);
            AppLog.i("BLE", "Bond state " + previous + " -> " + state);
            if (state == BluetoothDevice.BOND_BONDED && pendingNotificationCharacteristic != null) {
                setP2p("BLE 安全配對完成，保持目前 GATT 連線");
                AppLog.i("BLE", "Bond completed; retaining active GATT");
                main.postDelayed(() -> beginPasswordHandshake(gatt), 500L);
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
                beginPasswordHandshake(current);
                return;
            }
            pendingNotificationCharacteristic = characteristic;
            setP2p("正在建立 RE 密碼驗證通道");
            AppLog.i("BLE", "Starting A106/A105 password handshake before multiplex subscription");
            beginPasswordHandshake(current);
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
            if (controlProfile == 1
                    && GC1_PHONE_RESULT.equals(notificationCharacteristic.getUuid())) {
                AppLog.i("BLE", "A000 local notification registration ready; A304 CCCD write skipped");
                multiplexSubscriptionAttempts = 0;
                main.postDelayed(() -> writeNotificationDescriptor(current,
                        gc1NotifyPrimary, "AE01"), 1_500L);
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
            // targetSdk 32 intentionally uses the mutable GATT API required by legacy RE firmware.
            descriptor.setValue(cccdValue);
            if (!current.writeDescriptor(descriptor)) setP2p("無法送出狀態通知設定");
        } catch (SecurityException error) {
            setP2p("缺少藍牙連線權限");
        }
    }

    private void beginSecurityProbe(BluetoothGatt current) {
        if (current == null || current != gatt || !reGattConnected || gc1BootReady == null
                || securityProbeInFlight) return;
        try {
            securityProbeInFlight = true;
            bootReadInFlight = true;
            AppLog.i("BLE", "Reading A101 as security probe properties="
                    + gc1BootReady.getProperties());
            if (!current.readCharacteristic(gc1BootReady)) {
                securityProbeInFlight = false;
                bootReadInFlight = false;
                setP2p("Android 無法送出 RE 安全探測");
            }
        } catch (SecurityException error) {
            securityProbeInFlight = false;
            bootReadInFlight = false;
            setP2p("缺少藍牙連線權限");
        }
    }

    private void beginPasswordHandshake(BluetoothGatt current) {
        if (current == null || current != gatt || !reGattConnected || gc1PasswordResult == null
                || passwordHandshakeInFlight) return;
        BluetoothGattDescriptor descriptor = gc1PasswordResult.getDescriptor(CCCD);
        if (descriptor == null) {
            setP2p("找不到 RE 密碼驗證描述元");
            return;
        }
        try {
            passwordHandshakeInFlight = true;
            if (!current.setCharacteristicNotification(gc1PasswordResult, true)) {
                passwordHandshakeInFlight = false;
                setP2p("無法啟用 RE 密碼驗證通知");
                return;
            }
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            trace("DESCRIPTOR_WRITE", GC1_PASSWORD_RESULT,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                    "descriptor=2902 passwordChannel=true");
            AppLog.i("BLE", "Subscribing A106 password result properties="
                    + gc1PasswordResult.getProperties());
            if (!current.writeDescriptor(descriptor)) {
                passwordHandshakeInFlight = false;
                setP2p("無法送出 RE 密碼驗證訂閱");
            }
        } catch (SecurityException error) {
            passwordHandshakeInFlight = false;
            setP2p("缺少藍牙連線權限");
        }
    }

    private boolean writePasswordVerification(BluetoothGatt current, String password, int operation) {
        if (current == null || current != gatt || !reGattConnected || gc1PasswordRequest == null) return false;
        byte[] payload = RePasswordProtocol.verificationPayload(password);
        passwordHandshakeInFlight = true;
        passwordOperation = operation;
        passwordWriteComplete = false;
        pendingPasswordResult = -1;
        AppLog.i("BLE", "Writing A105 password verification length=" + payload.length);
        if (!writeGattPacket(GC1_PASSWORD_REQUEST, payload)) {
            resetPasswordOperation();
            setP2p("無法送出 RE 密碼驗證請求");
            return false;
        }
        return true;
    }

    private boolean writePasswordChange(BluetoothGatt current, String password) {
        byte[] payload = RePasswordProtocol.changePayload(password);
        passwordHandshakeInFlight = true;
        passwordOperation = PASSWORD_SETUP_CHANGE;
        passwordWriteComplete = false;
        pendingPasswordResult = -1;
        AppLog.i("BLE", "Writing A105 initial password setup length=" + payload.length);
        if (!writeGattPacket(GC1_PASSWORD_REQUEST, payload)) {
            resetPasswordOperation();
            setP2p("無法送出 RE 新密碼設定");
            return false;
        }
        return true;
    }

    private void completePasswordVerificationIfReady(BluetoothGatt current) {
        if (!passwordWriteComplete || pendingPasswordResult < 0) return;
        int operation = passwordOperation;
        int result = pendingPasswordResult;
        resetPasswordOperationState(false);
        if (operation == PASSWORD_SETUP_VERIFY_DEFAULT) {
            if (result == 0) {
                setP2p("原廠密碼已確認，正在設定新密碼");
                writePasswordChange(current, pendingNewPassword);
            } else {
                pendingNewPassword = null;
                setP2p("RE 不在原廠密碼狀態，請先執行硬體重設");
            }
            return;
        }
        if (operation == PASSWORD_SETUP_VERIFY_NEW) {
            if (result == 2) {
                cameraPassword = pendingNewPassword;
                pendingNewPassword = null;
                finishPasswordVerification(current, "RE 首次密碼設定完成");
            } else {
                pendingNewPassword = null;
                setP2p("新密碼寫入後驗證失敗，請重新連線後再試");
            }
            return;
        }
        if (result == 2) {
            finishPasswordVerification(current, "RE 密碼驗證完成");
        } else if (result == 0) {
            setP2p("RE 使用原廠密碼，請完成首次密碼設定");
        } else if (result == 1 || result == 3) {
            setP2p("RE 已設定密碼，請輸入正確的相機密碼");
        } else {
            setP2p("RE 密碼驗證沒有有效回覆");
        }
    }

    private void finishPasswordVerification(BluetoothGatt current, String message) {
        pendingNotificationCharacteristic = null;
        transition(A000ConnectionState.VERIFIED, message);
        transition(A000ConnectionState.EVENT_CHANNEL_INIT, "subscribing AE01/AE02");
        setP2p(message);
        enableStatusNotifications(current, gc1PhoneResult);
    }

    private void readGc1FirmwareRevision(BluetoothGatt current) {
        if (current == null || current != gatt || !reGattConnected
                || gc1FirmwareRevision == null || firmwareReadInFlight) return;
        try {
            firmwareReadInFlight = true;
            trace("READ_START", FIRMWARE_REVISION, null, "source=180A/2A26");
            if (!current.readCharacteristic(gc1FirmwareRevision)) {
                firmwareReadInFlight = false;
                transition(A000ConnectionState.ERROR, "Android rejected 2A26 read");
                setP2p("Android 無法讀取 RE BLE 韌體版本");
            }
        } catch (SecurityException error) {
            firmwareReadInFlight = false;
            transition(A000ConnectionState.ERROR, "2A26 permission denied");
            setP2p("缺少藍牙連線權限");
        }
    }

    private void resetPasswordOperation() {
        resetPasswordOperationState(true);
    }

    private void resetPasswordOperationState(boolean clearPendingPassword) {
        passwordHandshakeInFlight = false;
        passwordOperation = PASSWORD_NONE;
        passwordWriteComplete = false;
        pendingPasswordResult = -1;
        if (clearPendingPassword) pendingNewPassword = null;
    }

    private void writeNotificationDescriptor(BluetoothGatt current,
            BluetoothGattCharacteristic characteristic, String label) {
        if (current == null || current != gatt || !reGattConnected) return;
        if (characteristic == null) {
            setP2p("找不到 RE " + label + " multiplex 通道");
            return;
        }
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD);
        if (descriptor == null) {
            setP2p("找不到 RE " + label + " CCCD");
            return;
        }
        try {
            if (!current.setCharacteristicNotification(characteristic, true)) {
                setP2p("無法啟用 RE " + label + " notification");
                return;
            }
            byte[] value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
            multiplexSubscriptionAttempts++;
            trace("DESCRIPTOR_WRITE", characteristic.getUuid(), value,
                    "descriptor=2902 label=" + label
                            + " attempt=" + multiplexSubscriptionAttempts);
            AppLog.i("BLE", "Subscribing GC1 multiplex " + label + " properties="
                    + characteristic.getProperties() + " attempt=" + multiplexSubscriptionAttempts);
            descriptor.setValue(value);
            if (!current.writeDescriptor(descriptor)) {
                setP2p("無法送出 RE " + label + " 訂閱");
            }
        } catch (SecurityException error) {
            setP2p("缺少藍牙連線權限");
        }
    }

    private UUID gc1EventCharacteristic(byte eventId) {
        int target = Gc1MultiplexEvent.target(eventId);
        if (target == Gc1MultiplexEvent.BOOT_READY) return GC1_BOOT_READY;
        if (target == Gc1MultiplexEvent.HARDWARE_STATUS) return GC1_HARDWARE_STATUS;
        if (target == Gc1MultiplexEvent.PHONE_WIFI_RESULT) return GC1_PHONE_RESULT;
        if (target == Gc1MultiplexEvent.CAMERA_ERROR) return GC1_CAMERA_ERROR;
        return null;
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

    private void maybeStartServiceDiscovery() {
        if (GattDiscoveryGate.shouldDiscover(gattConnectedSignal, aclConnectedSignal,
                serviceDiscoveryStarted)) {
            main.removeCallbacks(serviceDiscoveryFallback);
            main.postDelayed(() -> startServiceDiscovery("GATT + ACL stabilized"),
                    SERVICE_DISCOVERY_STABILIZATION_MS);
        }
    }

    private void startServiceDiscovery(String reason) {
        BluetoothGatt current = gatt;
        if (current == null || !reGattConnected || serviceDiscoveryStarted) return;
        serviceDiscoveryStarted = true;
        try {
            boolean started = current.discoverServices();
            AppLog.i("BLE", "Service discovery reason=" + reason + " started=" + started);
            if (!started) serviceDiscoveryStarted = false;
        } catch (SecurityException error) {
            serviceDiscoveryStarted = false;
        }
    }

    private void resetDiscoveryGate() {
        gattConnectedSignal = false;
        aclConnectedSignal = false;
        serviceDiscoveryStarted = false;
        emptyServiceDiscoveryRetries = 0;
    }

    private void startWifiBootstrapIfReady() {
        if (pendingGroup == null || !notificationsReady || commandQueue.isBusy() || awaitingConfigStatus) return;
        if (controlProfile == 1 && !bootPreparationComplete) {
            startGc1BootFlow();
            return;
        }
        String ssid = pendingGroup.getNetworkName();
        String passphrase = pendingGroup.getPassphrase();
        if (ssid == null || ssid.isEmpty() || passphrase == null || passphrase.isEmpty()) {
            setP2p("群組缺少 SSID 或密碼");
            return;
        }
        int frequency = Build.VERSION.SDK_INT >= 29 ? pendingGroup.getFrequency() : 0;
        List<GattCommandQueue.Packet> writes = new ArrayList<>();
        if (controlProfile == 1) {
            writes.add(new GattCommandQueue.Packet(GC1_SERVER_BAND,
                    Gc1WifiProtocol.serverBand(Locale.getDefault().getCountry()),
                    "設定第一代 RE Wi-Fi 國別"));
            addGc1LongPackets(writes, GC1_PHONE_SSID,
                    ssid.getBytes(StandardCharsets.UTF_8), "傳送 Wi-Fi SSID");
            addGc1LongPackets(writes, GC1_PHONE_PASSWORD,
                    passphrase.getBytes(StandardCharsets.UTF_8), "傳送 Wi-Fi 密碼");
            byte[] command = Gc1WifiProtocol.stationConfig(random.nextInt(), false);
            writes.add(new GattCommandQueue.Packet(GC1_PHONE_CONFIG,
                    command, "設定 station 模式並加入群組"));
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
        transition(A000ConnectionState.WIFI_BOOTSTRAP, "A201/A301/A302/A303 queued");
        commandQueue.replace(writes);
    }

    private void startGc1BootFlow() {
        if (gc1BleFirmwareVersion < 0) {
            transition(A000ConnectionState.ERROR, "boot requested before 2A26");
            setP2p("尚未取得 RE BLE 韌體版本，禁止猜測啟動分支");
            return;
        }
        if (bootReadInFlight || bootWakeInFlight || bootPreparationComplete) return;
        Gc1BootProtocol.FirstOperation operation =
                Gc1BootProtocol.firstOperation(gc1BleFirmwareVersion);
        trace("BOOT_BRANCH", null, null, "bleFw=" + gc1BleFirmwareVersion
                + " branch=" + Gc1BootProtocol.branch(gc1BleFirmwareVersion)
                + " first=" + operation);
        if (operation == Gc1BootProtocol.FirstOperation.READ_A101) {
            readGc1BootState();
        } else {
            beginGc1WakeAttempt();
        }
    }

    private void readGc1BootState() {
        BluetoothGatt current = gatt;
        if (bootReadInFlight || bootWakeInFlight || current == null || gc1BootReady == null) return;
        try {
            bootReadInFlight = true;
            setP2p("正在讀取 RE 啟動狀態");
            trace("READ_START", GC1_BOOT_READY, null,
                    "properties=" + gc1BootReady.getProperties());
            markBootGattDispatch();
            if (!current.readCharacteristic(gc1BootReady)) {
                bootReadInFlight = false;
                setP2p("Android 未接受 RE 啟動狀態讀取");
            }
        } catch (SecurityException error) {
            bootReadInFlight = false;
            setP2p("缺少藍牙連線權限");
        }
    }

    private void beginGc1WakeAttempt() {
        BluetoothGatt current = gatt;
        if (bootWakeInFlight || current == null || gc1BootCommand == null
                || gc1BleFirmwareVersion < 0) return;
        long throttleDelay = remainingBootGattThrottleMs();
        if (throttleDelay > 0L) {
            main.postDelayed(this::beginGc1WakeAttempt, throttleDelay);
            return;
        }
        bootWakeInFlight = true;
        bootWakeAttempts++;
        transition(A000ConnectionState.BOOT_WAITING, "A101 waiter armed attempt=" + bootWakeAttempts);
        setP2p("RE 處於待機，正在喚醒");
        byte[] command = new byte[]{0x01};
        long timeoutMs = Gc1BootProtocol.timeoutMs(gc1BleFirmwareVersion);
        trace("WRITE_START", GC1_BOOT_COMMAND, command, "attempt=" + bootWakeAttempts
                + " waiterArmed=true timeoutMs=" + timeoutMs);
        main.removeCallbacks(bootTimeout);
        main.postDelayed(bootTimeout, timeoutMs);
        markBootGattDispatch();
        if (!writeGattPacket(GC1_BOOT_COMMAND, command)) {
            main.removeCallbacks(bootTimeout);
            bootWakeInFlight = false;
            setP2p("Android 未接受 RE 喚醒命令");
        }
    }

    private void readGc1WakeEcho(BluetoothGatt expectedGatt) {
        if (expectedGatt == null || expectedGatt != gatt || !reGattConnected
                || !bootWakeInFlight || bootPreparationComplete || gc1BootCommand == null) return;
        try {
            long throttleDelay = remainingBootGattThrottleMs();
            if (throttleDelay > 0L) {
                main.postDelayed(() -> readGc1WakeEcho(expectedGatt), throttleDelay);
                return;
            }
            trace("READ_START", GC1_BOOT_COMMAND, null, "expectedEcho=01");
            markBootGattDispatch();
            if (!expectedGatt.readCharacteristic(gc1BootCommand)) {
                AppLog.w("BLE", "Android rejected A107 read-back");
            }
        } catch (SecurityException error) {
            AppLog.w("BLE", "A107 read-back permission denied");
        }
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
            if (controlProfile == 1) {
                trace("WRITE_DISPATCH", characteristicId, value,
                        "writeType=" + BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            }
            AppLog.i("BLE", "Writing characteristic=" + characteristicId
                    + " length=" + value.length + " writeType="
                    + BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            characteristic.setValue(value);
            return current.writeCharacteristic(characteristic);
        } catch (SecurityException error) {
            return false;
        }
    }

    private void handleStatusNotification(UUID characteristicId, byte[] value) {
        if (controlProfile == 1 && GC1_HARDWARE_STATUS.equals(characteristicId)) {
            if (value != null && value.length == 6) {
                AppLog.i("BLE", "A102 hardware status battery=" + (value[1] & 0xff)
                        + " usbStorage=" + (value[3] & 0xff)
                        + " adapter=" + (value[5] & 0xff));
            }
            return;
        }
        if (controlProfile == 1 && GC1_CAMERA_ERROR.equals(characteristicId)) {
            if (value != null && value.length >= 8) {
                int errorIndex = littleEndianInt(value, 0);
                int errorCode = littleEndianInt(value, 4);
                String meaning = errorIndex == 7 && errorCode == 49
                        ? "ERR_NO_SD_CARD" : "unknown";
                AppLog.w("BLE", "A805 camera error index=" + errorIndex
                        + " code=" + errorCode + " meaning=" + meaning);
            }
            return;
        }
        if (controlProfile == 1 && GC1_BOOT_READY.equals(characteristicId)) {
            if (Gc1BootState.isReady(value)) {
                main.removeCallbacks(bootTimeout);
                bootWakeInFlight = false;
                bootWakeAttempts = 0;
                bootPreparationComplete = true;
                transition(A000ConnectionState.BOOT_READY, "A101 event ready");
                AppLog.i("BLE", "RE A101 boot-ready bit received");
                setP2p("RE 已啟動，正在傳送 Wi-Fi 設定");
                startWifiBootstrapIfReady();
            } else {
                AppLog.w("BLE", "A101 event not ready length="
                        + (value == null ? 0 : value.length) + " first="
                        + (value == null || value.length == 0 ? -1 : value[0] & 0xff));
            }
            return;
        }
        if (controlProfile == 1 && GC1_PHONE_RESULT.equals(characteristicId)) {
            finishWifiConfig(value, 1);
            return;
        }
        if (value == null || value.length < 2 || value[0] != WIFI_CONFIG_STATUS_EVENT) return;
        finishWifiConfig(value, 1);
    }

    private void finishWifiConfig(byte[] value, int statusIndex) {
        if (value.length <= statusIndex) return;
        int status = value[statusIndex] & 0xff;
        if (status != 0) {
            main.removeCallbacks(configTimeout);
            awaitingConfigStatus = false;
            closeGc1IpDiscovery();
            setP2p("RE 加入群組失敗（status=" + status + "）");
            return;
        }
        int ipIndex = statusIndex + 1;
        String ip = value.length >= ipIndex + 4
                ? (value[ipIndex] & 0xff) + "." + (value[ipIndex + 1] & 0xff) + "." + (value[ipIndex + 2] & 0xff) + "." + (value[ipIndex + 3] & 0xff)
                : "尚未提供";
        if (value.length < ipIndex + 4 || "0.0.0.0".equals(ip)) {
            AppLog.i("BLE", "A304 success without IP; continuing UDP 7777 wait");
            return;
        }
        main.removeCallbacks(configTimeout);
        awaitingConfigStatus = false;
        closeGc1IpDiscovery();
        cameraIp = ip;
        transition(A000ConnectionState.IP_READY, "A304 camera IPv4 received");
        setP2p("RE 已加入 · IP " + ip);
        setHttpState("待連線 · " + ip);
        AppLog.i("BLE", "Wi-Fi bootstrap completed; camera IP=" + ip);
    }

    private void startGc1IpDiscovery() {
        closeGc1IpDiscovery();
        final long generation = ipDiscoveryGeneration;
        Thread receiver = new Thread(() -> {
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(7777));
                synchronized (ipDiscoveryLock) {
                    if (generation != ipDiscoveryGeneration) {
                        socket.close();
                        return;
                    }
                    ipDiscoverySocket = socket;
                }
                socket.setSoTimeout((int) CONFIG_TIMEOUT_MS);
                AppLog.i("P2P", "Listening for first-generation RE IP on UDP 7777");
                DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
                socket.receive(packet);
                String ip = packet.getAddress().getHostAddress();
                AppLog.i("P2P", "UDP 7777 received RE IP=" + ip);
                main.post(() -> {
                    if (generation == ipDiscoveryGeneration) finishGc1IpDiscovery(ip);
                });
            } catch (SocketTimeoutException timeout) {
                AppLog.w("P2P", "UDP 7777 RE IP discovery timeout");
            } catch (Exception error) {
                if (awaitingConfigStatus) {
                    AppLog.w("P2P", "UDP 7777 RE IP discovery failed="
                            + error.getClass().getSimpleName());
                }
            } finally {
                if (socket != null && !socket.isClosed()) socket.close();
                synchronized (ipDiscoveryLock) {
                    if (ipDiscoverySocket == socket) ipDiscoverySocket = null;
                }
            }
        }, "re-ip-discovery");
        receiver.setDaemon(true);
        receiver.start();
    }

    private void finishGc1IpDiscovery(String ip) {
        if (ip == null || ip.isEmpty()) return;
        main.removeCallbacks(configTimeout);
        awaitingConfigStatus = false;
        cameraIp = ip;
        transition(A000ConnectionState.IP_READY, "camera IPv4 received");
        setP2p("RE 已連線，IP " + ip);
        setHttpState("等待連線至 " + ip);
    }

    private void closeGc1IpDiscovery() {
        DatagramSocket socket;
        synchronized (ipDiscoveryLock) {
            ipDiscoveryGeneration++;
            socket = ipDiscoverySocket;
            ipDiscoverySocket = null;
        }
        if (socket != null && !socket.isClosed()) socket.close();
    }

    private void addGc1LongPackets(List<GattCommandQueue.Packet> writes, UUID characteristic,
            byte[] payload, String label) {
        writes.addAll(Gc1LongValueCodec.fragment(characteristic, payload, label));
    }

    private String normalizedCountry(String country) {
        return country == null || country.length() < 2 ? "TW" : country.toUpperCase(Locale.ROOT);
    }

    private boolean hasRequiredCharacteristics() {
        if (controlProfile == 1) return gc1BootReady != null && gc1BootCommand != null
                && gc1PasswordRequest != null && gc1PasswordResult != null
                && gc1ServerBand != null && gc1PhoneSsid != null
                && gc1PhonePassword != null && gc1PhoneConfig != null && gc1PhoneResult != null
                && gc1NotifyPrimary != null && gc1NotifySecondary != null
                && gc1FirmwareRevision != null;
        if (controlProfile == 2) return shortCommand != null && longCommand != null;
        return false;
    }

    private BluetoothGattCharacteristic findWritableCharacteristic(UUID id) {
        if (SHORT_COMMAND.equals(id)) return shortCommand;
        if (LONG_COMMAND.equals(id)) return longCommand;
        if (GC1_BOOT_COMMAND.equals(id)) return gc1BootCommand;
        if (GC1_PASSWORD_REQUEST.equals(id)) return gc1PasswordRequest;
        if (GC1_SERVER_BAND.equals(id)) return gc1ServerBand;
        if (GC1_PHONE_SSID.equals(id)) return gc1PhoneSsid;
        if (GC1_PHONE_PASSWORD.equals(id)) return gc1PhonePassword;
        if (GC1_PHONE_CONFIG.equals(id)) return gc1PhoneConfig;
        return null;
    }

    private void clearGc1Characteristics() {
        closeGc1IpDiscovery();
        main.removeCallbacks(bootTimeout);
        bootReadInFlight = false;
        securityProbeInFlight = false;
        resetPasswordOperation();
        bootWakeInFlight = false;
        bootWakeAttempts = 0;
        bootPreparationComplete = false;
        firmwareReadInFlight = false;
        gc1BleFirmwareVersion = -1;
        lastBootGattDispatchAtMs = Long.MIN_VALUE;
        gc1BootReady = null;
        gc1BootCommand = null;
        gc1PasswordRequest = null;
        gc1PasswordResult = null;
        gc1ServerBand = null;
        gc1PhoneSsid = null;
        gc1PhonePassword = null;
        gc1PhoneConfig = null;
        gc1PhoneResult = null;
        gc1NotifyPrimary = null;
        gc1NotifySecondary = null;
        gc1FirmwareRevision = null;
    }

    private void transition(A000ConnectionState next, String reason) {
        A000ConnectionState previous = a000State;
        a000State = next;
        AppLog.i("A000", "STATE " + previous + " -> " + next + " reason=" + reason);
    }

    private void markBootGattDispatch() {
        lastBootGattDispatchAtMs = SystemClock.elapsedRealtime();
    }

    private long remainingBootGattThrottleMs() {
        if (lastBootGattDispatchAtMs == Long.MIN_VALUE) return 0L;
        long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - lastBootGattDispatchAtMs);
        return Math.max(0L, GC1_GATT_THROTTLE_MS - elapsed);
    }

    private static int littleEndianInt(byte[] value, int offset) {
        if (value == null || offset < 0 || value.length < offset + 4) return -1;
        return (value[offset] & 0xff)
                | ((value[offset + 1] & 0xff) << 8)
                | ((value[offset + 2] & 0xff) << 16)
                | ((value[offset + 3] & 0xff) << 24);
    }

    private void trace(String operation, UUID characteristic, byte[] payload, String detail) {
        long transactionId = ++transactionSequence;
        String payloadText;
        if (payload == null) {
            payloadText = "-";
        } else if (GC1_PASSWORD_REQUEST.equals(characteristic)
                || GC1_PHONE_SSID.equals(characteristic)
                || GC1_PHONE_PASSWORD.equals(characteristic)) {
            payloadText = "<redacted:length=" + payload.length + ">";
        } else {
            StringBuilder hex = new StringBuilder();
            for (byte item : payload) {
                if (hex.length() > 0) hex.append(' ');
                hex.append(String.format(Locale.US, "%02X", item & 0xff));
            }
            payloadText = hex.toString();
        }
        AppLog.i("A000", "TX=" + transactionId + " state=" + a000State
                + " op=" + operation + " uuid=" + (characteristic == null ? "-" : characteristic)
                + " payload=" + payloadText + " " + detail);
    }

    private boolean hasBluetoothPermission(String permission) {
        return Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private String describeGattStatus(int status) {
        if (status == 147) return " (connection timeout / target unavailable)";
        if (status == 133) return " (generic Android GATT error)";
        return "";
    }

    private String describeScanFailure(int errorCode) {
        switch (errorCode) {
            case 1: return "already started";
            case 2: return "application registration failed";
            case 3: return "internal error";
            case 4: return "feature unsupported";
            case 5: return "out of hardware resources";
            case 6: return "scanning too frequently";
            default: return "unknown error " + errorCode;
        }
    }

    private void stopScan() {
        if (scanTimeout != null) main.removeCallbacks(scanTimeout);
        scanTimeout = null;
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
