# 測試計畫

文件狀態：已依 RE Lens `0.6.3` 更新；「已驗證」僅代表目前 Samsung S21 5G+／RE 組合，不代表完整 Android 矩陣。`0.6.3` 的群組重建恢復尚待實機驗證。

## 目前回歸項目

- 單元測試確認第二個 GATT packet 在第一個 `onCharacteristicWrite` 前不得送出。
- 單元測試確認 A101 ready 早於 A107 callback 時不得完成 boot；A107 回讀成功前不得送出 A201。
- 單元測試確認 36-byte 長命令分成 17、18、1-byte payload 的三包，序號依序為首包、`0x02`、`0x04`。
- 實機測試 CCCD callback 失敗、任一 characteristic write 失敗、60 秒設定逾時與 BLE 中途斷線。
- 實機確認 Android 10+ 只建立或沿用 2.4 GHz group；預先存在 5 GHz group 時必須移除並重建，不得將 5180 MHz 送給 RE。
- 單元測試確認自主 owner group 的 SSID 符合 `DIRECT-xy` 規則、passphrase 長度在 8–63 之間；Builder 參數無效時 App 不得閃退。
- 在背景監看開啟、Activity 重建、BLE 斷線與連續點擊連線情境下，確認只有一個 GATT session、一次認證與一組 AE01/AE02 訂閱。
- 連續重試 Wi-Fi bootstrap，確認 UDP 7777 無 `BindException`，舊 session 的 IP 不會覆蓋新 session。
- 單元測試確認第一次 station 失敗可重建群組、第二次停止，且非 active attempt 的 A304 必須忽略；實機須確認第一次 IP timeout 後確實先移除舊群組再建立新群組並重送完整 bootstrap。
- GC1 405 分段下載必測第一包 1-byte status 計入 wire offset、後續包連續性及中斷後 socket 重建；MediaStore 重新操作目前由頭下載。
- RTSP 控制流程保留協議測試，但 UI 不啟動播放器：實機已確認 RTP/JPEG payload type 26 不受 Media3 支援。未來 decoder 接入前，不得恢復自動重試或把狀態顯示為可用。
- Android 10+ 下載成功後，照片須出現在 `Pictures/RE Lens`、影片須出現在 `Movies/RE Lens`；傳輸失敗不得留下 pending MediaStore 項目。Android 8–9 驗證儲存權限與媒體掃描。
- Samsung S21 實機已確認 MediaStore 照片與影片下載成功並產生公開媒體 URI；Android 8–9 路徑仍列入硬體矩陣。
- 確認匯出 log 不含 SSID、passphrase、OAuth token 或 YouTube stream key。
- YouTube 測試涵蓋取消授權、API 錯誤、建立並綁定、stream 未 active 時禁止 live、正常 complete。

## 自動測試

- 非 A000 相容層的 HTTP method、路徑與 JSON request（不得視為 GC1 驗證）。
- HTTP `200`／`206` Range 行為僅屬相容層測試；A000 MediaStore 路徑使用 GC1 `405` 且目前不跨工作階段續傳。
- 媒體 ID／檔名正規化與路徑邊界。
- GC1 相簿空清單、未知媒體類型、detail handle 不符及 fragment offset 不連續。
- GC1 `408` 刪除 payload 必須含 little-endian handle 與結尾 `00000000`；UI 必須二次確認，成功後重新載入清單。
- GC1 `213` 驗證各模式剩餘數量、free bytes、total bytes、格式化容量與使用比例。
- 連線狀態機的合法及非法轉移。
- Android 8、11、12、13、14、15 權限流程與 Activity 重建。
- 深淺色切換、最大字體、五頁導覽及 Console 顯示／隱藏。
- 背景服務啟停、斷線通知開關與 Log 匯出內容遮蔽。

## 實體 HTC RE 整合測試

| 編號 | 情境 | 驗收條件 |
|---|---|---|
| HW-01 | BLE 掃描 | 10 秒內找到 RE 或顯示可重試錯誤（Samsung S21 已驗證） |
| HW-02 | GATT 配對 | 完成認證、通知訂閱及 2A26（Samsung S21 已驗證） |
| HW-03 | P2P group | 相機加入且手機取得可連線 IP（Samsung S21 已驗證） |
| HW-04 | 拍照 | 單次命令只產生一張照片（已驗證） |
| HW-05 | 錄影 | 開始／停止一致且檔案可列出（已驗證） |
| HW-06 | 相簿 | `401/404` 項目數、檔名、類型與大小和 RE 實際內容一致（已驗證） |
| HW-07 | 照片下載 | 檔案可開啟且大小正確（已驗證） |
| HW-08 | 影片下載 | 分段下載完成後可由系統相簿播放（已驗證） |
| HW-09 | 30 分鐘連線 | 無資源洩漏或無限重連 |
| HW-10 | RTSP | 暫緩；待 RFC 2435 相容 decoder 後重新啟用播放驗證 |
| HW-11 | GC1 offset | 驗證單次工作階段內 `405` offset 與 fragment 連續性；跨工作階段續傳列為未來功能 |

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
- 發布為穩定版前，HW-01 至 HW-09 應在 Android 12、15 通過；目前僅完成 Samsung S21 5G+ 的主流程驗證。
- 無明文記錄密碼、序號及媒體內容。
- 已知限制與支援韌體寫入 release notes。
