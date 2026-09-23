# 測試計畫

## 0.3.0 必測項目

- 單元測試確認第二個 GATT packet 在第一個 `onCharacteristicWrite` 前不得送出。
- 單元測試確認 36-byte 長命令分成 17、18、1-byte payload 的三包，序號依序為首包、`0x02`、`0x04`。
- 實機測試 CCCD callback 失敗、任一 characteristic write 失敗、60 秒設定逾時與 BLE 中途斷線。
- 實機測試 2.4 GHz 與 5 GHz group，確認 band/channel byte 與 RE 回報 IPv4。
- 確認匯出 log 不含 SSID、passphrase、OAuth token 或 YouTube stream key。
- YouTube 測試涵蓋取消授權、API 錯誤、建立並綁定、stream 未 active 時禁止 live、正常 complete。

## 自動測試

- HTTP method、路徑與 JSON request。
- `200`／`206` Range 續傳及錯誤 Content-Range。
- 媒體 ID／檔名正規化與路徑邊界。
- 相簿 JSON 缺欄位、未知欄位與空清單。
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
| HW-06 | 相簿 | 分頁及欄位與實際內容一致 |
| HW-07 | 照片下載 | 檔案可開啟且大小正確 |
| HW-08 | 影片續傳 | 中斷後續傳，結果可播放 |
| HW-09 | 30 分鐘連線 | 無資源洩漏或無限重連 |
| HW-10 | RTSP | 延遲、方向與背景釋放符合規格 |

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
