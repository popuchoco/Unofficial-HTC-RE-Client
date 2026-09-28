# 裝置通訊介面

本文件描述 RE Lens 使用的裝置互通介面；未經實體硬體測試確認的項目均標示為待驗證。

## 傳輸分工

- BLE：裝置探索、配對、網路參數設定及低流量控制。
- Wi‑Fi Direct：建立手機與相機之間的 IP 資料鏈路。
- HTTP：控制請求、媒體清單及檔案下載。
- RTSP：A000／GC1 拍照取景器先以 command `261` 設定 Still mode，再依序以 `234` 設定 24 fps、`233` 設定 M 尺寸、`235` 設定 High 壓縮率，最後送出 `130`；成功回應中狀態位元組後方是 RTSP URI，空 URI 才使用 GC1 fallback。URI 立即交給 Media3，事件 `0x4012` 僅記錄為非阻塞的 stream-ready 訊號，command `131` 停止。

RTSP 預覽參數是傳輸控制，不等同於一般拍攝偏好。協議提供 15／24／30 fps、S／M／L 尺寸與 Low／Medium／High 壓縮率；拍照取景器使用 24 fps、M、High，並依實測 FPS 自動調整尺寸。設定資源中的 1／10／30 fps 是縮時攝影成片的播放速度；照片／影片解析度則是實際拍攝規格，兩者都不得映射成 RTSP 預覽參數。目前未發現預覽 FPS 或壓縮率的持久化使用者設定。

## GC1 與 GC2 傳輸不可混用

A000／GC1 在取得相機 IP 後使用五條 TCP 通道：`9000` 命令送出、`9001` 命令回覆、`9002` 事件、`9003` 檔案、`9004` 縮圖。每個命令封包採 16-byte 小端序標頭（command、總長、sequence、flags），同一時間只允許一筆命令等待回覆。連線建立後必須先完成 command `501` 握手，才能送出拍照或錄影命令。

`ws://<camera-ip>:3000/sock` 是 GC2 的控制路徑，不能套用到 A000／GC1。舊版文件所列 `http://<camera-ip>:3000/v1/...` REST 路徑未獲 GC1 實機證實，已自 GC1 實作移除。GC1 不預設 RTSP 路徑，而是使用 command `130` 回傳的完整 URI。

## 已確認的 GC1 控制命令

| 功能 | 方法 | 路徑 | 狀態 |
|---|---:|---|---|
| GC1 握手／版本 | TCP | command `501` | 已實作，待硬體驗證 |
| 拍照 | TCP | command `311`, payload `00` | 已實作，待硬體驗證 |
| 開始一般錄影 | TCP | command `106`, payload `00` | 已實作，待硬體驗證 |
| 停止錄影 | TCP | command `107`, empty payload | 已實作，待硬體驗證 |
| 啟動／停止即時預覽 | TCP + RTSP | `261(00)` → `234(2400 LE)` → `233(02)` → `235(02)` → `130` 回傳 URI；event `0x4012` 不阻塞播放；`131` 停止 | 已實作，待畫面驗證 |
| 媒體清單 | TCP | command `401`，每筆 9 bytes | 已實作，待硬體驗證 |
| 媒體詳細資料 | TCP | command `404`，payload=handle | 已實作，待硬體驗證 |
| 原檔續傳 | TCP `9003` | command `405`，payload=handle+offset | 已實作，待硬體驗證 |

縮圖 command `403`／`9004` 尚未接入畫面；相簿目前先顯示檔名、類型及大小。任何 A000 功能都不使用 `3000/v1` 路徑。

## 分段傳輸

下載器在目標檔案已存在時，將現有長度放入 command `405` 的 offset。每個 fragment 都核對 sequence、offset 與宣告長度；相機回報取消旗標或 offset 不連續時立即停止，並保留檔案供下次續傳。

## 待完成的 BLE 狀態機

1. 掃描、選取並連接 HTC RE。
2. 探索 GATT 服務並完成配對／認證。
3. 建立手機端 Wi‑Fi Direct group，取得 SSID、passphrase、頻率及 IP 資訊。
4. 依序寫入網路參數並等待每一步回應。
5. 要求相機切換為 station 模式並加入 group。
6. 從 P2P connection info 取得相機位址，建立 HTTP client。
7. 中斷時回收 GATT、P2P group、Network callback 與下載工作。

GATT UUID、封包內容、回應碼及 timeout 必須經實機測試確認後再列為穩定 API。
