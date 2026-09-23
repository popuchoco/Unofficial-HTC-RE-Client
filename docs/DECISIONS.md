# 架構決策紀錄

## ADR-001：獨立 App

採用獨立套件 `tw.xiaoxin.relens`、獨立 UI 與通訊層，避免與其他 App 的簽章、資料及更新流程衝突。

## ADR-002：BLE 控制面與 Wi‑Fi Direct 資料面分離

BLE 用於探索與網路啟動；照片、影片和預覽由 Wi‑Fi Direct IP 鏈路承載，以取得合理速度與可靠性。

## ADR-003：Target SDK 32

設定 `minSdk 26`、`targetSdk 32`、`compileSdk 35`：

- Android 12+ 使用 `BLUETOOTH_SCAN` 與 `BLUETOOTH_CONNECT`。
- 避免 target 33+ 導入 `NEARBY_WIFI_DEVICES` 的不同執行期權限路徑。
- Android 15 上仍可使用新版編譯工具與公開 API。
- Compile SDK 不改變 HTC RE 的 BLE、Wi‑Fi 或 HTTP 協定。

若 Google Play 發布要求、Android 停止相容 target 32，或 target 35 完成全套 P2P 實機回歸測試，重新評估此決定。

## ADR-004：保留 Cleartext HTTP

設定 `android:usesCleartextTraffic="true"`，因裝置位於點對點區域網路且其介面使用 HTTP。未來可用 Network Security Config 進一步限縮目的地。

## ADR-005：不使用隱藏 Wi‑Fi API

API 29+ 使用公開的 `WifiP2pGroup.getFrequency()`；較舊版本將頻率視為選填資訊，不呼叫 `getOptFreq`。頻率不得成為連線流程的單點失敗。

## ADR-006：HTTP Range 續傳

媒體下載以既有檔案長度作為 Range 起點，並處理 `200` 與 `206`，降低大檔案因鏈路中斷而重傳的成本。

## ADR-007：RTSP 延後

保留 `rtsp://<camera-ip>/live` 介面，首版不內建播放器。RTSP 解碼涉及生命週期、硬體解碼、延遲及授權評估，不以外部 Intent 或 WebView代替完整實作。
