# Unofficial HTC RE Client — RE Lens

RE Lens 是為 HTC RE 相機重新打造的非官方 Android 用戶端，採 Material 3 介面並針對目前 Android 權限與元件生命週期設計。本專案與 HTC Corporation 無關。

## 目前功能

> 目前修正版：`0.3.7`。第一代 `A000` profile 在 BLE bonding 完成後重新探索控制服務；`A304` 採本機 notification 註冊，不對韌體拒絕寫入的 CCCD 送出設定。第二代 profile 仍使用標準 CCCD 訂閱流程。

- 「連線」頁位於最左側：BLE 掃描／連線、Wi-Fi Direct group、背景連線、斷線提醒及藍牙／Wi-Fi 狀態。
- 連線層以序列 GATT 佇列傳送 Wi-Fi Direct SSID、密碼與 station/config；每一個封包都必須收到 characteristic callback 才會前進。
- 解析 RE 的 Wi-Fi 設定狀態與相機 IPv4，供後續 HTTP、RTSP 使用。
- 拍照／錄影、相簿與 HTTP Range 續傳的介面及服務邊界。
- 串流頁包含 RTSP 預覽基礎與 YouTube Live 控制面；RTSP→RTMP 媒體 relay 尚待完成。
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

首次使用時，手機先透過 BLE 與 RE 建立控制通道，再建立 Wi-Fi Direct group。App 將 group 的 SSID 與 passphrase 經 GATT 依序送給 RE，RE 以 station 模式加入並回報 IPv4；HTTP 下載與 RTSP 預覽再走此 IP 網路。

詳細握手與佇列規則見 [BLE 與 Wi-Fi Direct 連線引導](docs/CONNECTION_BOOTSTRAP.md)。

## 文件

- [產品規格](docs/SPECIFICATION.md)
- [軟體設計](docs/SOFTWARE_DESIGN.md)
- [架構決策](docs/DECISIONS.md)
- [Android 相容性](docs/ANDROID_COMPATIBILITY.md)
- [通訊協議備註](docs/PROTOCOL_NOTES.md)
- [BLE 與 Wi-Fi Direct 連線引導](docs/CONNECTION_BOOTSTRAP.md)
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
