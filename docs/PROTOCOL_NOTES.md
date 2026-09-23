# 裝置通訊介面

本文件描述 RE Lens 使用的裝置互通介面；未經實體硬體測試確認的項目均標示為待驗證。

## 傳輸分工

- BLE：裝置探索、配對、網路參數設定及低流量控制。
- Wi‑Fi Direct：建立手機與相機之間的 IP 資料鏈路。
- HTTP：控制請求、媒體清單及檔案下載。
- RTSP：即時預覽；目前只保留介面。

HTTP base URL 為 `http://<camera-ip>:3000`；RTSP URL 為 `rtsp://<camera-ip>/live`。手機端應將請求綁定至 Wi‑Fi Direct 所屬 Android `Network`，避免行動網路成為預設路由。

## HTTP 路徑

| 功能 | 方法 | 路徑 | 狀態 |
|---|---:|---|---|
| 相機資訊 | GET | `/v1/camera` | 已實作，待硬體驗證 |
| 拍照 | POST | `/v1/camera/capture` | 已實作，待硬體驗證 |
| 開始錄影 | POST | `/v1/camera/record/start` | 已實作，待硬體驗證 |
| 停止錄影 | POST | `/v1/camera/record/stop` | 已實作，待硬體驗證 |
| 媒體清單 | GET | `/v1/dcim/items` | 已實作，回應欄位待確認 |
| 可用空間 | GET | `/v1/system/storage/freespace` | 已實作，待硬體驗證 |
| 裝置序號 | GET | `/v1/system/serial_num` | 已實作，待硬體驗證 |
| 媒體下載 | GET + Range | `/v1/dcim/items/<id>/<rendition>/download` | 已實作，待硬體驗證 |

## 分段傳輸

下載器在目標檔案已存在時送出 `Range: bytes=<existing>-`。回覆 `206 Partial Content` 時追加內容；若回覆 `200 OK`，則從頭覆寫。正式版本應在完成後驗證檔案大小或雜湊。

## 待完成的 BLE 狀態機

1. 掃描、選取並連接 HTC RE。
2. 探索 GATT 服務並完成配對／認證。
3. 建立手機端 Wi‑Fi Direct group，取得 SSID、passphrase、頻率及 IP 資訊。
4. 依序寫入網路參數並等待每一步回應。
5. 要求相機切換為 station 模式並加入 group。
6. 從 P2P connection info 取得相機位址，建立 HTTP client。
7. 中斷時回收 GATT、P2P group、Network callback 與下載工作。

GATT UUID、封包內容、回應碼及 timeout 必須經實機測試確認後再列為穩定 API。
