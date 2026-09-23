# Android 相容性

## SDK 策略

`minSdk 26`、`targetSdk 32`、`compileSdk 35`。Compile SDK 是編譯界線；Target SDK 才決定系統套用的相容行為。Compile SDK 35 不會要求 HTC RE 理解 Android 15，也不會改變相機收到的資料。

## 權限矩陣

| Android | BLE | Wi‑Fi Direct |
|---|---|---|
| 8–11 | `BLUETOOTH`、`BLUETOOTH_ADMIN`、位置權限 | 粗略＋精確位置 |
| 12–15（target 32） | `BLUETOOTH_SCAN`、`BLUETOOTH_CONNECT` | 粗略＋精確位置相容路徑 |

目前不使用 target 33+ 的 `NEARBY_WIFI_DEVICES`。提升 target SDK 前，必須在 Android 13–15 重測掃描、建 group、入網與路由綁定。

## Component 與 HTTP

- 含 intent filter 的 component 明確設定 `android:exported`；內部 component 預設為 `false`。
- 動態 Receiver 在新版 Android 明確指定 export 行為。
- 保留 `android:usesCleartextTraffic="true"` 以連接相機 HTTP 介面。
- HTTP client 必須綁定 P2P `Network`，避免相機請求走一般網際網路。

## Wi‑Fi P2P 頻率

- API 29+ 使用 `WifiP2pGroup.getFrequency()`。
- API 26–28 將頻率視為選填資訊，不使用隱藏 API。
- 相機可加入的頻段及 channel 必須實機驗證。
- 若手機建立不相容頻段，提供重新建立 group 或裝置提示，不臆測頻率。

## Android 15 驗證重點

- 權限拒絕與從設定撤回後的行為。
- 背景／前景切換後 GATT 狀態。
- P2P Network 是否正確承載 HTTP。
- 前景服務限制對下載與串流的影響。
- Target 32 相容模式的安裝及系統提示。
