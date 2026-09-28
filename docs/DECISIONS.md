# 架構決策紀錄

## ADR-006：Wi-Fi 引導使用序列 GATT 佇列

所有 characteristic write 維持單一 in-flight，只有收到成功 callback 才送下一個封包。通知訂閱先於 SSID、passphrase 與 station/config，最後等待設定狀態與 RE IPv4。拒絕平行寫入與固定延遲，因兩者無法證明相機已接收前一包。

## ADR-007：YouTube Live 分離控制面與媒體面

串流頁先提供 Google 授權與 YouTube broadcast/stream/bind/transition 控制；RTSP 到 RTMP 的 relay 是獨立後續元件。UI 不會把「已建立 YouTube session」誤示為「已開始傳送影音」。

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

## ADR-007：RTSP 使用內建播放器

以 Media3 在拍攝頁內播放 RE 的 RTSP 畫面，並隨頁面生命週期送出啟動／停止命令及釋放解碼器；不以外部 Intent 或 WebView 代替。A000 空 URI 使用其 GC1 位址 `rtsp://<camera-ip>:8554/MJPEG_unicast`，不得混用其他裝置世代的 `/live` 路徑。

## ADR-008：預覽品質先採 Auto

拍照取景器先固定採已確認的 Still mode、24 fps、M 尺寸及 High 壓縮率，並保留日後依播放 FPS 自動調整尺寸的架構。在基準取景流程完成實機驗證前，不把 RTSP FPS、尺寸及壓縮率暴露成一般設定，避免把縮時播放 FPS、拍攝解析度與預覽傳輸品質混為一談。後續若加入手動覆寫，放在「裝置 → 進階 → 預覽品質」，預設 Auto、保存偏好並提供恢復 Auto。
