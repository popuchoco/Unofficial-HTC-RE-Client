# Android 相容性

文件狀態：已依 RE Lens `0.6.1` 核對。

## SDK 策略

`minSdk 26`、`targetSdk 32`、`compileSdk 35`。Compile SDK 是編譯界線；Target SDK 才決定系統套用的相容行為。Compile SDK 35 不會要求 HTC RE 理解 Android 15，也不會改變相機收到的資料。

## 權限矩陣

| Android | BLE | Wi‑Fi Direct |
|---|---|---|
| 8–11 | `BLUETOOTH`、`BLUETOOTH_ADMIN`、位置權限 | 粗略＋精確位置 |
| 12–15（target 32） | `BLUETOOTH_SCAN`、`BLUETOOTH_CONNECT` | 粗略＋精確位置相容路徑 |

目前不使用 target 33+ 的 `NEARBY_WIFI_DEVICES`。提升 target SDK 前，必須在 Android 13–15 重測掃描、建 group、入網與路由綁定。

## Component、前景服務與區域網路

- 含 intent filter 的 component 明確設定 `android:exported`；內部 component 預設為 `false`。
- API 33+ 的 Bluetooth 動態 Receiver 明確使用 `RECEIVER_EXPORTED`；bond／ACL 廣播來自高度權限的系統元件，receiver 仍核對 action 與目前 GATT device。
- 前景服務宣告 `connectedDevice` 類型及 `FOREGROUND_SERVICE_CONNECTED_DEVICE` 權限。
- 保留 `android:usesCleartextTraffic="true"` 供其他相機 profile 的區域 HTTP 相容；目前 A000 控制、媒體與下載實際使用 GC1 socket。
- 現行 P2P owner group 路徑以 RE 回報的區域 IPv4 直接連線，尚未實作 `Network.bindSocket()`；若未來改成 Android `NetworkRequest` 路徑，必須新增多網路／行動數據並存測試。

## Wi‑Fi P2P 頻率

- API 29+ 使用 `WifiP2pGroup.getFrequency()`。
- API 26–28 將頻率視為選填資訊，不使用隱藏 API。
- 相機可加入的頻段及 channel 必須實機驗證。
- 若手機建立不相容頻段，提供重新建立 group 或裝置提示，不臆測頻率。

## Android 15 驗證重點

- 權限拒絕與從設定撤回後的行為。
- 背景／前景切換後 GATT 狀態。
- P2P route 是否正確承載 GC1 socket，並在行動數據同時開啟時維持可達。
- 前景服務限制對下載與串流的影響。
- Target 32 相容模式的安裝及系統提示。
