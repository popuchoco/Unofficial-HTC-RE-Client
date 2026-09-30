# Unofficial HTC RE Client — RE Lens

RE Lens 是為 HTC RE 相機重新打造的非官方 Android 用戶端，採 Material 3 介面並針對目前 Android 權限與元件生命週期設計。本專案與 HTC Corporation 無關。

## 目前功能

> 目前版本：`0.6.3`。依連線流程稽核補齊 Wi-Fi station 失敗恢復：第一次等候 IP 逾時或 A304 回報失敗時，會移除舊 Wi-Fi Direct 群組、建立新群組並完整重送一次；舊 session 的 A304／UDP 回覆不會寫回新嘗試。此修正仍需 HTC RE 實機確認。

Wi‑Fi Direct 建立採非同步群組資訊查詢：Android 接受 `createGroup()` 後會等待 owner group 的 SSID 與密碼真正可用，才啟動 BLE bootstrap。

`0.6.3` 不會在 IP 逾時後沿用同一組 owner group 無限重送。App 會自動重建一次；若第二次仍失敗才停止並保留「下次連線強制重建群組」標記，避免故障群組殘留。

- 「連線」頁位於最左側：BLE 掃描／連線、Wi-Fi Direct group、背景連線、斷線提醒及藍牙／Wi-Fi 狀態。
- 連線層以序列 GATT 佇列傳送 Wi-Fi Direct SSID、密碼與 station/config；每一個封包都必須收到 characteristic callback 才會前進。
- 解析 RE 的 Wi-Fi 設定狀態與相機 IPv4，供 GC1 socket 與 RTSP 使用。
- A000／GC1 原生 socket 拍照、錄影、版本與儲存空間讀取、相簿列舉、刪除及原檔分段下載。
- 即時預覽列為未來功能；串流頁保留 YouTube Live 控制面，RTSP→RTMP 媒體 relay 尚待完成。
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

為相容其他相機 profile 的區域 HTTP，Manifest 保留 `usesCleartextTraffic=true`；目前 A000 的已驗證拍攝、裝置與相簿功能實際走 GC1 socket。YouTube API 一律使用 HTTPS。

## 連線方式

首次使用時，手機先透過 BLE 與 RE 建立控制通道，再建立 Wi-Fi Direct group。App 將 group 的 SSID 與 passphrase 經 GATT 依序送給 RE，RE 以 station 模式加入並回報 IPv4；GC1 控制、檔案下載與 RTSP 預覽再走此 IP 網路。

一般使用者請依 [連線操作指南](docs/CONNECTION_GUIDE.md) 完成首次連線、日常重連與問題回報。協議握手與佇列規則見 [BLE 與 Wi-Fi Direct 連線引導](docs/CONNECTION_BOOTSTRAP.md)，狀態與測試閘門見 [A000 連線流程稽核](docs/A000_CONNECTION_FLOW_AUDIT.md)。

## 連線異常與回報

若 Android 偶發拒絕 GATT 操作、控制通道沒有完成訂閱，或 Wi-Fi bootstrap 沒有繼續，請保持 RE 開機且靠近手機，在「連線」頁再次按一次 Wi-Fi Direct 連線並等待流程完成；請勿快速連續點擊。若重試後仍無法恢復，請到「裝置」頁開啟偵錯、匯出操作 Log，並在 [GitHub Issues](https://github.com/popuchoco/Unofficial-HTC-RE-Client/issues/new) 附上：

- RE Lens 版本、手機型號及 Android 版本。
- 問題發生前的操作步驟與畫面訊息。
- 匯出的 Log（送出前仍請確認不含自行輸入的密碼或其他個人資料）。

## 相簿下載位置

Android 10 以上透過 MediaStore 儲存，不再放在 App 專屬的 `Android/data`。照片與影片分別出現在：

```text
Pictures/RE Lens/
Movies/RE Lens/
```

檔案可由 Samsung Gallery、Google Photos 或 Samsung「我的檔案」開啟，解除安裝 App 時不會刪除。Android 8–9 使用相同的公開資料夾，首次使用時需允許儲存權限。

## 文件

- [文件索引與狀態](docs/README.md)
- [產品規格](docs/SPECIFICATION.md)
- [軟體設計](docs/SOFTWARE_DESIGN.md)
- [架構決策](docs/DECISIONS.md)
- [Android 相容性](docs/ANDROID_COMPATIBILITY.md)
- [通訊協議備註](docs/PROTOCOL_NOTES.md)
- [BLE 與 Wi-Fi Direct 連線引導](docs/CONNECTION_BOOTSTRAP.md)
- [連線操作指南](docs/CONNECTION_GUIDE.md)
- [A000 連線流程稽核](docs/A000_CONNECTION_FLOW_AUDIT.md)
- [Code Review 處理紀錄](docs/CODE_REVIEW_RESPONSE.md)
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
