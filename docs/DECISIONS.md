# 架構決策紀錄

文件狀態：已依 RE Lens `0.6.2` 核對。ADR 編號代表穩定決策，不以實作版本重新編號。

## ADR-001：獨立 App

採用獨立套件 `tw.xiaoxin.relens`、獨立 UI 與通訊層，避免與其他 App 的簽章、資料及更新流程衝突。

## ADR-002：BLE 控制面與 Wi‑Fi Direct 資料面分離

BLE 負責探索、密碼驗證、boot 與 Wi‑Fi credential bootstrap；照片、影片、裝置資訊和預覽由 Wi‑Fi Direct IP 鏈路承載。A000 實機使用 GC1 `9000`–`9004` socket；port 3000 HTTP／WebSocket 僅保留為其他 profile 的相容邊界，不得套用到 A000。

## ADR-003：Target SDK 32

設定 `minSdk 26`、`targetSdk 32`、`compileSdk 35`：

- Android 12+ 使用 `BLUETOOTH_SCAN` 與 `BLUETOOTH_CONNECT`。
- 暫不導入 target 33+ 的 `NEARBY_WIFI_DEVICES` 執行期權限路徑。
- Compile SDK 35 只影響可編譯 API，不改變 HTC RE 收到的 BLE、Wi‑Fi 或 GC1 協議。

若發行平台不再接受 target 32，或 target 35 完成 Android 13–15 的 BLE／P2P 實機矩陣，再重新評估。Manifest 已先加入 Android 14 connected-device 前景服務權限。

## ADR-004：保留 Cleartext 相容能力

保留 `android:usesCleartextTraffic="true"`，供區域相機 profile 使用 HTTP。A000 的已驗證功能走 GC1 socket。RE 位址由 Wi‑Fi Direct 動態分配，而 Android Network Security Config 無法同時按動態子網及 TCP port 限縮，因此不宣稱它能完成此白名單；外部 YouTube API 一律使用 HTTPS。

## ADR-005：不使用隱藏 Wi‑Fi API

API 29+ 使用公開的 `WifiP2pGroup.getFrequency()` 並建立 2.4 GHz autonomous group；API 26–28 將頻率視為選填資訊，不呼叫 `getOptFreq` 或 `getFrequency` 隱藏方法。

## ADR-006：Wi‑Fi 引導使用序列 GATT 佇列

所有 credential characteristic write 維持單一 in-flight；收到 callback 後仍保留約 1.5 秒裝置節流才送下一包。AE01／AE02、2A26 與 boot 必須先完成，A201／A301／A302／A303 不得並行寫入。

## ADR-007：GC1 分段下載與 MediaStore 提交分離

GC1 command `405` 使用 handle 與 offset 分段取回原檔。Android 10+ 每次操作建立新的 MediaStore pending 項目，成功後提交，失敗即刪除；因此目前支援分段下載，但不宣稱跨工作階段續傳。Android 8–9 使用公開媒體目錄與 media scan。

## ADR-008：RTSP 預覽暫列未來功能

實機已證明 command `130` 與 event `0x4012` 能啟動串流，但 SDP 使用 Media3 不支援的 RTP/JPEG payload type 26。拍照與錄影控制保留；待加入 RFC 2435 相容 depacketizer／decoder 後再恢復內嵌預覽。

## ADR-009：預覽品質預設 Auto

已知取景參數為 Still mode、24 fps、M 尺寸及 High 壓縮率，但目前播放器未啟用，因此不提供沒有可觀察效果的設定 UI。未來恢復預覽後，手動覆寫放在「裝置 → 進階 → 預覽品質」，預設 Auto 並保存偏好。

## ADR-010：YouTube Live 分離控制面與媒體面

串流頁提供 Google 授權及 YouTube broadcast／stream／bind／transition 控制；RTSP 到 RTMP relay 是獨立的未來媒體元件。建立 YouTube session 不得顯示成已開始傳送影音。
