# 裝置通訊介面

文件狀態：已依 RE Lens `0.6.3` 與最新實機 Log 核對。未經實體硬體測試確認的項目會明確標示為待驗證。

## 傳輸分工

- BLE：裝置探索、配對、網路參數設定及低流量控制。
- Wi‑Fi Direct：建立手機與相機之間的 IP 資料鏈路。
- GC1 socket：A000 的拍攝控制、裝置資訊、媒體清單、刪除與原檔下載。
- HTTP／WebSocket：保留給非 A000 profile；目前沒有 A000 實機證據支持 port 3000 REST 路徑。
- RTSP：A000／GC1 的控制流程已確認可啟動串流並收到 `0x4012`。實機 SDP 使用 RTP/JPEG static payload type 26；Media3 1.9.4 不支援此格式，參考取景器使用專用逐幀串流元件。因此目前不在拍攝頁啟動播放器，後續需實作 RFC 2435 depacketizer／decoder 或採用經驗證的相容播放器。

RTSP 預覽參數是傳輸控制，不等同於一般拍攝偏好。協議提供 15／24／30 fps、S／M／L 尺寸與 Low／Medium／High 壓縮率；拍照取景器使用 24 fps、M、High，並依實測 FPS 自動調整尺寸。設定資源中的 1／10／30 fps 是縮時攝影成片的播放速度；照片／影片解析度則是實際拍攝規格，兩者都不得映射成 RTSP 預覽參數。目前未發現預覽 FPS 或壓縮率的持久化使用者設定。

## GC1 與 GC2 傳輸不可混用

A000／GC1 在取得相機 IP 後使用五條 TCP 通道：`9000` 命令送出、`9001` 命令回覆、`9002` 事件、`9003` 檔案、`9004` 縮圖。每個命令封包採 16-byte 小端序標頭（command、總長、sequence、flags），同一時間只允許一筆命令等待回覆。連線建立後必須先完成 command `501` 握手，才能送出拍照或錄影命令。

`ws://<camera-ip>:3000/sock` 是 GC2 的控制路徑，不能套用到 A000／GC1。舊版文件所列 `http://<camera-ip>:3000/v1/...` REST 路徑未獲 GC1 實機證實，已自 GC1 實作移除。GC1 不預設 RTSP 路徑，而是使用 command `130` 回傳的完整 URI。

## 已確認的 GC1 控制命令

| 功能 | 方法 | 路徑 | 狀態 |
|---|---:|---|---|
| GC1 握手／版本 | TCP | command `501` | 已完成實機驗證 |
| 拍照 | TCP | command `311`, payload `00` | 已完成實機驗證 |
| 開始一般錄影 | TCP | command `106`, payload `00` | 已完成實機驗證 |
| 停止錄影 | TCP | command `107`, empty payload | 已完成實機驗證 |
| 啟動／停止即時預覽 | TCP + RTSP | 控制流程及 `0x4012` 已驗證；影音為 RTP/JPEG payload type 26 | 未來功能：待相容 decoder |
| 媒體清單 | TCP | command `401`，每筆 9 bytes | 已完成實機驗證 |
| 媒體詳細資料 | TCP | command `404`，payload=handle | 已完成實機驗證 |
| 刪除媒體 | TCP | command `408`，payload=一或多個 LE handle，最後接 `00000000` | 已完成實機驗證 |
| 儲存空間 | TCP | command `213`，回傳各模式剩餘數量、free bytes、total bytes | 已完成實機驗證 |
| 原檔分段下載 | TCP `9003` | command `405`，payload=handle+offset | 照片與影片已完成實機驗證 |

縮圖 command `403`／`9004` 尚未接入畫面；相簿目前先顯示檔名、類型及大小。任何 A000 功能都不使用 `3000/v1` 路徑。

## 分段傳輸

GC1 `405` 支援 offset，每個 fragment 都核對 sequence、offset 與宣告長度；相機回報取消旗標或 offset 不連續時立即停止。現行 MediaStore sink 會在失敗時刪除 pending 項目，重新操作會由頭下載，因此尚不提供跨工作階段斷點續傳。

## 已實作的 BLE／Wi‑Fi 狀態機

1. 掃描、選取並連接 HTC RE。
2. 探索 GATT 服務並完成配對／認證。
3. 建立手機端 Wi‑Fi Direct group，取得 SSID、passphrase、頻率及 IP 資訊。
4. 依序寫入網路參數並等待每一步回應。
5. 要求相機切換為 station 模式並加入 group。
6. 由 A304 或 UDP 7777 取得相機位址，按功能建立 GC1 socket session。
7. 中斷時取消 GATT queue、關閉 GATT 與 IP discovery socket；P2P group 由明確的重建流程管理。

這條主路徑已有實機成功紀錄；跨 Android 版本與其他 RE 韌體仍須依 [測試計畫](TEST_PLAN.md) 持續回歸。
