# 軟體設計文件（SD）

## 0.3.0 元件增補

| 元件 | 責任 |
|---|---|
| `GattCommandQueue` | 保證單一 in-flight GATT write，以 callback 驅動下一個封包，處理長命令分段 |
| `ReConnectionManager` | 協調 BLE、通知訂閱、Wi-Fi Direct group、station bootstrap 與 RE IP 回報 |
| `YouTubeAuthManager` | YouTube scope 授權與記憶體 token 生命週期 |
| `YouTubeLiveClient` | YouTube Live broadcast、stream、bind 與 transition 控制面 |
| `MediaRelay`（規劃） | RE RTSP／媒體片段至 YouTube RTMP 的媒體面 |

使用者操作見 [CONNECTION_GUIDE.md](CONNECTION_GUIDE.md)，連線狀態機詳見 [CONNECTION_BOOTSTRAP.md](CONNECTION_BOOTSTRAP.md)，Code Review 決策見 [CODE_REVIEW_RESPONSE.md](CODE_REVIEW_RESPONSE.md)，直播界線詳見 [YOUTUBE_LIVE.md](YOUTUBE_LIVE.md)。

## 系統脈絡

```text
Android UI → ConnectionCoordinator
               ├─ BLE transport ─────────────→ HTC RE
               ├─ Wi-Fi P2P / Network binding → HTC RE
               └─ GC1 socket / RTSP ─────────→ HTC RE
```

## 邏輯模組

| 模組 | 責任 |
|---|---|
| UI | 四個主要頁面、權限說明、錯誤與進度 |
| ConnectionCoordinator | 跨 BLE/P2P/HTTP 的連線狀態機 |
| BleTransport | 掃描、GATT 連線、讀寫與通知 |
| P2pController | group 建立、廣播、group/connection info |
| NetworkBinder | 取得 P2P `Network` 並綁定 socket |
| ReApi | 依裝置世代選擇控制與媒體 API；目前 A000 使用 GC1 transport |
| Gc1SocketClient | GC1 的 9000–9004 socket、501 握手、拍攝、相簿、分段下載、RTSP session 與事件追蹤 |
| MediaRepository | 分頁、下載、續傳、驗證與 MediaStore |
| RTP/JPEG preview（未來） | RE 使用 payload type 26；需 RFC 2435 depacketizer／decoder，不能由 Media3 直接播放 |
| DeviceRepository | 裝置資訊與儲存狀態 |
| ConnectionMonitorService | 前景服務、背景狀態通知與斷線提醒 |
| AppLog | 有界記憶體操作紀錄、敏感值遮蔽與 SAF 匯出 |

目前程式是驗證 HTTP 與 Android 平台能力的垂直切片；後續應依上表拆分，避免 Activity 同時負責 UI、狀態及 I/O。

## 連線狀態機

```text
Idle → PermissionRequired → BleScanning → BleConnecting → BleReady
     → P2pCreating → CredentialsReady → CameraJoining → IpReady → HttpReady
```

每個階段均可轉入 `Failed(recoverable, reason)` 或 `Disconnecting → Idle`。UI 顯示具體階段，不使用單一布林值掩蓋部分失敗。

## 執行緒與生命週期

- BLE callback 序列化進單一 command queue，避免同時寫 characteristic。
- HTTP 與檔案 I/O 使用有限大小 executor。
- 連線狀態由具生命週期的 service 或 application-scoped coordinator 管理。
- 使用者中斷時依序取消下載、停止串流、解除 Network callback、移除 P2P group、關閉 GATT。
- 背景連線使用 Android 前景服務與持續通知；此設定不等同開機自動啟動。

## 錯誤模型

`PermissionDenied`、`BluetoothUnavailable`、`GattFailure`、`P2pFailure`、`CameraTimeout`、`HttpFailure`、`ProtocolFailure`、`StorageFailure`。

使用者訊息提供可採取的下一步；診斷資料遮蔽密碼與個人媒體內容。

## 安全與擴充

- Cleartext HTTP 只經相機 P2P network 使用。
- 不由外部 Intent 任意指定下載 URL；檔名正規化且路徑限制於 App／MediaStore。
- Release build 不記錄敏感 payload。
- `MediaExportProvider`、`StreamEngine`、`CameraProtocol` 與 `DiagnosticsExporter` 作為後續擴充介面。
