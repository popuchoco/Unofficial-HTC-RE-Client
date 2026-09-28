# 測試計畫

## 0.3.0 必測項目

- 單元測試確認第二個 GATT packet 在第一個 `onCharacteristicWrite` 前不得送出。
- 單元測試確認 36-byte 長命令分成 17、18、1-byte payload 的三包，序號依序為首包、`0x02`、`0x04`。
- 實機測試 CCCD callback 失敗、任一 characteristic write 失敗、60 秒設定逾時與 BLE 中途斷線。
- 實機確認 Android 10+ 只建立或沿用 2.4 GHz group；預先存在 5 GHz group 時必須移除並重建，不得將 5180 MHz 送給 RE。
- 單元測試確認自主 owner group 的 SSID 符合 `DIRECT-xy` 規則、passphrase 長度在 8–63 之間；Builder 參數無效時 App 不得閃退。
- 在背景監看開啟、Activity 重建、BLE 斷線與連續點擊連線情境下，確認只有一個 GATT session、一次認證與一組 AE01/AE02 訂閱。
- 連續重試 Wi-Fi bootstrap，確認 UDP 7777 無 `BindException`，舊 session 的 IP 不會覆蓋新 session。
- GC1 405 分段下載必測第一包 1-byte status 計入 wire offset、後續包連續性、中斷後 socket 重建與斷點續傳。
- A000 拍照取景器嚴格驗證 `261(00)` → `234(60 09)` → `233(02)` → `235(02)` → `130` 的操作順序，且在 event `0x4012` 前不得啟動播放器；空 URI 使用 `rtsp://<camera-ip>:8554/MJPEG_unicast`。預覽啟動中切頁、131 逾時與 Activity 重建不得阻擋相簿重新整理或讓舊 callback 寫入新頁面。
- Android 10+ 下載成功後，照片須出現在 `Pictures/RE Lens`、影片須出現在 `Movies/RE Lens`；傳輸失敗不得留下 pending MediaStore 項目。Android 8–9 驗證儲存權限與媒體掃描。
- Samsung S21 實機已確認 MediaStore 照片下載成功並產生公開 Images URI；影片與 Android 8–9 路徑仍列入硬體矩陣。
- 確認匯出 log 不含 SSID、passphrase、OAuth token 或 YouTube stream key。
- YouTube 測試涵蓋取消授權、API 錯誤、建立並綁定、stream 未 active 時禁止 live、正常 complete。

## 自動測試

- HTTP method、路徑與 JSON request。
- `200`／`206` Range 續傳及錯誤 Content-Range。
- 媒體 ID／檔名正規化與路徑邊界。
- GC1 相簿空清單、未知媒體類型、detail handle 不符及 fragment offset 不連續。
- 連線狀態機的合法及非法轉移。
- Android 8、11、12、13、14、15 權限流程與 Activity 重建。
- 深淺色切換、最大字體、五頁導覽及 Console 顯示／隱藏。
- 背景服務啟停、斷線通知開關與 Log 匯出內容遮蔽。

## 實體 HTC RE 整合測試

| 編號 | 情境 | 驗收條件 |
|---|---|---|
| HW-01 | BLE 掃描 | 10 秒內找到裝置或顯示可重試錯誤 |
| HW-02 | GATT 配對 | 完成認證並取得基本資訊 |
| HW-03 | P2P group | 相機加入且手機取得可連線 IP |
| HW-04 | 拍照 | 單次命令只產生一張照片 |
| HW-05 | 錄影 | 開始／停止一致且檔案可列出 |
| HW-06 | 相簿 | `401/404` 項目數、檔名、類型與大小和 RE 實際內容一致 |
| HW-07 | 照片下載 | 檔案可開啟且大小正確 |
| HW-08 | 影片續傳 | 中斷後續傳，結果可播放 |
| HW-09 | 30 分鐘連線 | 無資源洩漏或無限重連 |
| HW-10 | RTSP | `130` URI 可播放；延遲、方向、離頁 `131` 與解碼器釋放符合規格 |
| HW-11 | GC1 續傳 | 中斷後以現有長度作為 `405` offset，輸出檔案雜湊與來源一致 |

手機矩陣至少包含 Android 8、Android 12、Android 15，以及兩家不同品牌／晶片的 Wi‑Fi Direct 實作。

## 失敗注入

- GATT timeout、非零 status、通知順序錯誤。
- 建立 group 失敗或相機逾時未加入。
- HTTP 4xx／5xx、截斷 body、錯誤 Range。
- 下載中關閉相機、切換網路或 App 被終止。
- 儲存空間不足與 MediaStore 寫入失敗。

## 發布門檻

- Clean build 與 lint 通過。
- Manifest 權限與 exported component 核驗完成。
- HW-01 至 HW-09 在 Android 12、15 通過。
- 無明文記錄密碼、序號及媒體內容。
- 已知限制與支援韌體寫入 release notes。
