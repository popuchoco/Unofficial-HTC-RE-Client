# Unofficial HTC RE Client — RE Lens

RE Lens 是為 HTC RE 相機重新打造的非官方 Android 用戶端，採 Material 3 介面並針對目前 Android 權限與元件生命週期設計。本專案與 HTC Corporation 無關。

## 目前功能

> 目前診斷版：`0.5.1`。A000 初始化在密碼驗證與 AE01/AE02 訂閱後，先讀取 Device Information `180A/2A26` 的 BLE firmware version；解析成功後才建立 Wi-Fi Direct group，並依 `BLE FW > 2250` 嚴格選擇新版或舊版 boot 分支。GATT session 會阻止重複連線與重複初始化；Android 10+ 則強制建立 2.4 GHz Wi-Fi Direct group，不沿用會使 RE 回報 `A304 status=26` 的 5 GHz group。Wi-Fi bootstrap 使用 A201、A301/A302 分段、3-byte A303，以及 A304／UDP 7777 雙路徑取得結果。

Wi‑Fi Direct 建立採非同步群組資訊查詢：Android 接受 `createGroup()` 後會等待 owner group 的 SSID 與密碼真正可用，才啟動 BLE bootstrap。

- 「連線」頁位於最左側：BLE 掃描／連線、Wi-Fi Direct group、背景連線、斷線提醒及藍牙／Wi-Fi 狀態。
- 連線層以序列 GATT 佇列傳送 Wi-Fi Direct SSID、密碼與 station/config；每一個封包都必須收到 characteristic callback 才會前進。
- 解析 RE 的 Wi-Fi 設定狀態與相機 IPv4，供 GC1 socket 與 RTSP 使用。
- A000／GC1 原生 socket 拍照、錄影、版本讀取、相簿列舉及原檔續傳。
- 拍攝頁內建 RTSP 即時預覽；串流頁保留 YouTube Live 控制面，RTSP→RTMP 媒體 relay 尚待完成。
- 裝置資訊、深色／淺色／系統主題、偵錯 console 與操作 log 匯出。

## 平台設定

| 項目 | 設定 |
|---|---:|
| minSdk | 26 |
| targetSdk | 32 |
| compileSdk | 35 |
| Java | 17 |
| Application ID | `tw.xiaoxin.relens` |

target 32 是目前的相容性決策：支援 Android 12+ 的 `BLUETOOTH_SCAN`／`BLUETOOTH_CONNECT` 權限模型，同時暫不切入 target 33+ 的 `NEARBY_WIFI_DEVICES` 路徑。compileSdk 35 讓程式可使用新版 SDK 編譯；升級 target 前必須完成 HTC RE 實機矩陣。

RE 的區域網路 HTTP 端點需要 cleartext，相機流量因此保留 `usesCleartextTraffic=true`。YouTube API 仍使用 HTTPS。

## 連線方式

首次使用時，手機先透過 BLE 與 RE 建立控制通道，再建立 Wi-Fi Direct group。App 將 group 的 SSID 與 passphrase 經 GATT 依序送給 RE，RE 以 station 模式加入並回報 IPv4；GC1 控制、檔案下載與 RTSP 預覽再走此 IP 網路。

詳細握手與佇列規則見 [BLE 與 Wi-Fi Direct 連線引導](docs/CONNECTION_BOOTSTRAP.md)。目前連線層已暫停試誤式發版；下一版必須先符合 [A000 連線流程稽核](docs/A000_CONNECTION_FLOW_AUDIT.md) 的狀態、分支、追蹤與測試閘門。

## 文件

- [產品規格](docs/SPECIFICATION.md)
- [軟體設計](docs/SOFTWARE_DESIGN.md)
- [架構決策](docs/DECISIONS.md)
- [Android 相容性](docs/ANDROID_COMPATIBILITY.md)
- [通訊協議備註](docs/PROTOCOL_NOTES.md)
- [BLE 與 Wi-Fi Direct 連線引導](docs/CONNECTION_BOOTSTRAP.md)
- [A000 連線流程稽核](docs/A000_CONNECTION_FLOW_AUDIT.md)
- [YouTube Live 第三階段](docs/YOUTUBE_LIVE.md)
- [測試計畫](docs/TEST_PLAN.md)

## 建置

需要 Android SDK 35 與 JDK 17。在 `local.properties` 指定 Android SDK 後執行：

```text
gradle assembleDebug
```

APK 產出於 `app/build/outputs/apk/debug/`。

## 第三方功能參考

YouTube Live 的使用情境參考 [JALsnipe/HTC-RE-YouTube-Live-Android](https://github.com/JALsnipe/HTC-RE-YouTube-Live-Android)。因該專案未提供可辨識的授權條款，本專案沒有匯入其程式碼、資源或原生函式庫，而是以 Google 現行授權與 YouTube Data API 獨立實作控制面。

## 商標聲明

HTC 與 HTC RE 為其各自權利人的商標。本專案為社群相容用戶端，不代表原廠授權、認可或支援。
