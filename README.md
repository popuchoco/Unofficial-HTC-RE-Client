# Unofficial HTC RE Client — RE Lens

RE Lens 是為 HTC RE 相機設計的非官方、開放原始碼 Android 用戶端。專案使用獨立套件與程式架構，目標是在新版 Android 手機上提供裝置連線、遠端拍攝、媒體傳輸與裝置資訊功能。

> 專案尚在硬體整合階段。功能完成度請以本文與測試文件為準。

## 功能範圍

- BLE 裝置探索及 Android 12+ Nearby Devices 權限處理
- 獨立連線頁與 BLE、Wi‑Fi Direct、HTTP、RTSP 分層狀態
- 可選背景連線服務、斷線通知及藍牙／Wi‑Fi 開關檢查
- 建立手機端 Wi‑Fi Direct group
- 經相機區域網路 API 拍照、開始／停止錄影
- 瀏覽 HTC RE 上的照片與影片
- HTTP Range 分段／續傳下載
- 顯示相機、儲存空間、序號及 App 版本資訊
- 深色／淺色介面切換
- 可選操作 Console 與文字 Log 匯出
- 預留 RTSP 即時預覽及外部服務擴充介面

目前不包含社群平台登入、雲端硬碟或自動上傳功能。

## Android 相容性

| 項目 | 設定 |
|---|---|
| 最低版本 | Android 8.0 / API 26 |
| Target SDK | 32 |
| Compile SDK | 35 |
| Java | 17 |
| 套件名稱 | `tw.xiaoxin.relens` |

Target 32 讓 Android 12–15 使用 `BLUETOOTH_SCAN` 與 `BLUETOOTH_CONNECT`，同時維持 Wi‑Fi Direct 的位置權限相容路徑。Compile SDK 35 只影響建置時可用的 Android API，不會改變傳送給相機的通訊格式。

## 文件

- [產品與功能規格](docs/SPECIFICATION.md)
- [軟體設計文件](docs/SOFTWARE_DESIGN.md)
- [架構決策紀錄](docs/DECISIONS.md)
- [Android 相容性](docs/ANDROID_COMPATIBILITY.md)
- [裝置通訊介面](docs/PROTOCOL_NOTES.md)
- [測試計畫](docs/TEST_PLAN.md)

## 建置

安裝 Android Studio、Android SDK 35 與 JDK 17，在專案根目錄建立未納入版本控制的 `local.properties`：

```properties
sdk.dir=C\:\\path\\to\\Android\\Sdk
```

使用 Android Studio，或以 Gradle 8.9 執行 `gradle assembleDebug`。APK 位於 `app/build/outputs/apk/debug/`。

目前已通過 Android 編譯與 Manifest 靜態檢查。完整 BLE 設定狀態機、P2P 入網、媒體欄位及長時間傳輸仍需搭配實體 HTC RE 驗證。

## 商標聲明

HTC 與 HTC RE 是其權利人的商標。本專案與 HTC Corporation 無隸屬、授權或背書關係。
