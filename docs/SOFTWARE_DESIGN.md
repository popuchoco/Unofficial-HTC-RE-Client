# 軟體設計文件（SD）

文件狀態：已依 RE Lens `0.6.2` 核對。下表先列現行元件，再列規劃中的拆分，避免把目標架構誤認為已完成。

## 現行元件

| 元件 | 責任 |
|---|---|
| `GattCommandQueue` | 保證單一 in-flight GATT write，以 callback 驅動下一個封包，處理長命令分段 |
| `ReConnectionManager` | 協調 BLE、通知訂閱、Wi-Fi Direct group、station bootstrap 與 RE IP 回報 |
| `YouTubeAuthManager` | YouTube scope 授權與記憶體 token 生命週期 |
| `YouTubeLiveClient` | YouTube Live broadcast、stream、bind 與 transition 控制面 |
| `MediaRelay`（規劃） | RE RTSP／媒體片段至 YouTube RTMP 的媒體面 |
| `ReApi` | 對 UI 提供拍攝、裝置、相簿、下載與 RTSP session 介面；A000 委派給 GC1 client |
| `Gc1SocketClient` | 管理 `9000`–`9004`、`501` 握手、同步命令、事件與分段檔案傳輸 |
| `MainActivity` | 五頁 UI、MediaStore 匯出及目前的操作協調；後續需逐步拆分 |
| `ConnectionMonitorService` | 背景監看前景服務及斷線通知 |

使用者操作見 [CONNECTION_GUIDE.md](CONNECTION_GUIDE.md)，連線狀態機詳見 [CONNECTION_BOOTSTRAP.md](CONNECTION_BOOTSTRAP.md)，Code Review 決策見 [CODE_REVIEW_RESPONSE.md](CODE_REVIEW_RESPONSE.md)，直播界線詳見 [YOUTUBE_LIVE.md](YOUTUBE_LIVE.md)。

## 系統脈絡

```text
Android UI → ReConnectionManager
               ├─ BLE GATT ──────────────────→ HTC RE
               └─ Wi-Fi P2P group ───────────→ HTC RE
Android UI → ReApi → Gc1SocketClient / RTSP ──→ HTC RE
```

## 邏輯模組

| 模組 | 責任 |
|---|---|
| UI | 五個主要頁面、權限說明、錯誤與進度 |
| ConnectionCoordinator（規劃拆分） | 跨 BLE／P2P／GC1 的連線狀態機；目前責任在 `ReConnectionManager` |
| BleTransport（規劃拆分） | 掃描、GATT 連線、讀寫與通知 |
| P2pController（規劃拆分） | group 建立、廣播及 group info |
| NetworkBinder（尚未實作） | 若採 `NetworkRequest`，取得 P2P `Network` 並綁定 socket |
| ReApi | 依裝置世代選擇控制與媒體 API；目前 A000 使用 GC1 transport |
| Gc1SocketClient | GC1 的 9000–9004 socket、501 握手、拍攝、相簿、分段下載、RTSP session 與事件追蹤 |
| MediaRepository（規劃拆分） | 清單、分段下載、驗證與 MediaStore；目前責任分布在 `ReApi`、`Gc1SocketClient` 與 Activity |
| RTP/JPEG preview（未來） | RE 使用 payload type 26；需 RFC 2435 depacketizer／decoder，不能由 Media3 直接播放 |
| DeviceRepository | 裝置資訊與儲存狀態 |
| ConnectionMonitorService | 前景服務、背景狀態通知與斷線提醒 |
| AppLog | 有界記憶體操作紀錄、敏感值遮蔽與 SAF 匯出 |

目前程式是已可操作實機的垂直切片，不只是 HTTP 原型；後續應依上表漸進拆分，避免一次重構破壞已驗證的協議順序。

## 連線狀態機

```text
Idle → BleScanning → GattConnected → ServicesReady → PasswordVerifying
     → EventChannelInit → BleFwKnown → P2pGroupReady → BootReady
     → WifiBootstrap → IpReady → GC1Ready（按功能完成 501 握手）
```

每個階段均可轉入 `Failed(recoverable, reason)` 或 `Disconnecting → Idle`。UI 顯示具體階段，不使用單一布林值掩蓋部分失敗。

## 執行緒與生命週期

- GATT callback 指定在 main `Handler`，credential write 由單工 queue 及 1.5 秒節流控制。
- GC1、HTTPS 與檔案 I/O 使用有限大小 executor。
- 連線狀態由具生命週期的 service 或 application-scoped coordinator 管理。
- 使用者中斷時取消 GATT queue、停止 IP discovery 並關閉 GATT；GC1 client 在 API 關閉或錯誤時回收五條 socket。
- 背景連線使用 Android 前景服務與持續通知；此設定不等同開機自動啟動。

## 錯誤模型

`PermissionDenied`、`BluetoothUnavailable`、`GattFailure`、`P2pFailure`、`CameraTimeout`、`HttpFailure`、`ProtocolFailure`、`StorageFailure`。

使用者訊息提供可採取的下一步；診斷資料遮蔽密碼與個人媒體內容。

## 安全與擴充

- Cleartext 能力只供相機區域鏈路相容；A000 已驗證路徑使用 GC1 socket，YouTube 使用 HTTPS。
- 不由外部 Intent 任意指定下載 URL；檔名正規化且路徑限制於 App／MediaStore。
- Release build 不記錄敏感 payload。
- `MediaExportProvider`、`StreamEngine`、`CameraProtocol` 與 `DiagnosticsExporter` 作為後續擴充介面。
