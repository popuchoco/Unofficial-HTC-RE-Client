# 裝置通訊介面

本文件描述 RE Lens 使用的裝置互通介面；未經實體硬體測試確認的項目均標示為待驗證。

## 傳輸分工

- BLE：裝置探索、配對、網路參數設定及低流量控制。
- Wi‑Fi Direct：建立手機與相機之間的 IP 資料鏈路。
- HTTP：控制請求、媒體清單及檔案下載。
- RTSP：即時預覽；目前只保留介面。

## GC1 與 GC2 傳輸不可混用

A000／GC1 在取得相機 IP 後使用五條 TCP 通道：`9000` 命令送出、`9001` 命令回覆、`9002` 事件、`9003` 檔案、`9004` 縮圖。每個命令封包採 16-byte 小端序標頭（command、總長、sequence、flags），同一時間只允許一筆命令等待回覆。連線建立後必須先完成 command `501` 握手，才能送出拍照或錄影命令。

`ws://<camera-ip>:3000/sock` 是 GC2 的控制路徑，不能套用到 A000／GC1。舊版文件所列 `http://<camera-ip>:3000/v1/...` REST 路徑未獲 GC1 實機證實，已自 GC1 實作移除。RTSP 與媒體下載端點仍須分別經實機確認。

## 已確認的 GC1 控制命令

| 功能 | 方法 | 路徑 | 狀態 |
|---|---:|---|---|
| GC1 握手／版本 | TCP | command `501` | 已實作，待硬體驗證 |
| 拍照 | TCP | command `311`, payload `00` | 已實作，待硬體驗證 |
| 開始一般錄影 | TCP | command `106`, payload `00` | 已實作，待硬體驗證 |
| 停止錄影 | TCP | command `107`, empty payload | 已實作，待硬體驗證 |

相簿列舉、原檔下載與縮圖下載尚未完成。GC1 必須接續實作 `9003/9004` 的檔案 frame 與相對應命令；在完成前，不把 `3000/v1` 路徑標示為 A000 可用功能。

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
