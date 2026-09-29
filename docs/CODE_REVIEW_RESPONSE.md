# Code Review 處理紀錄

本文件記錄 2026-09 的外部 Code Review 與專案決策，避免後續審查把既有相容性選擇誤判為遺漏。

| 項目 | 處理 | 說明 |
|---|---|---|
| GATT callback 與主執行緒共用狀態 | 已修正 | `connectGatt` 明確指定 main `Handler`，所有狀態轉移與既有 queue scheduler 使用同一 Looper。 |
| Android 13+ characteristic callback | 已修正 | 補上帶 `byte[]` 的 read／changed overload，並沿用同一解析路徑。Android API 並沒有新版 characteristic-write 或 descriptor-write `byte[]` overload。 |
| `disconnect()` 立即 `close()` | 已修正 | 等待 disconnected callback 關閉；5 秒後才以 fallback 回收未回呼的 GATT。 |
| Android 14 connected-device 前景服務權限 | 已補強 | Manifest 已宣告 `FOREGROUND_SERVICE_CONNECTED_DEVICE`；target 仍依 ADR-003 維持 32。 |
| 動態 receiver export flag | 已補強 | API 33+ 明確使用 `RECEIVER_EXPORTED`：bond／ACL 廣播來自高度權限的 Bluetooth 系統元件；receiver 仍只接受固定 action，並核對目前 GATT device。舊版維持相容 overload。 |
| 檔案 socket 無 timeout | 已修正 | `9003`、`9004` 改用 30 秒 read timeout，避免永久阻塞。 |
| Log 洩漏 token／串流金鑰 | 已補強 | 除 Wi‑Fi 密碼外，新增 Bearer token 與 stream key 遮蔽。 |
| MediaStore 斷點續傳宣稱 | 已校正文件 | GC1 協議本身接受 offset，但目前 MediaStore sink 失敗時刪除 pending 項目；重新下載會從頭開始，因此不宣稱跨工作階段續傳。 |
| 全域 cleartext | 保留並說明 | RE 的位址由 Wi‑Fi Direct 動態分配，Android Network Security Config 無法按動態子網加 TCP port 設白名單。程式只為相機區域鏈路建立 HTTP，外部服務使用 HTTPS；未來 target 升級時再評估 socket-only 架構。 |
| UDP 7777 來源驗證 | 待相容性測試 | 目前 receiver 只在有效 bootstrap generation 與 60 秒窗口存活。不能假設所有 RE 韌體都使用固定 `/24`，未取得跨韌體證據前不加入可能阻斷實機的子網規則。 |
| target 33+ Wi‑Fi 權限路徑 | 歷史決策 | ADR-003 選擇 target 32，以兼容 Android 12+ BLE 權限並暫緩 `NEARBY_WIFI_DEVICES` 遷移；compileSdk 35 用於新 API 編譯。 |
| 大型 Activity／連線管理器拆分 | 技術債 | 已列入 `SOFTWARE_DESIGN.md` 的目標元件；本輪不在已通過實機驗證後做高風險重構。 |
| License | 需要專案擁有者決定 | 未經授權不代為選擇或加入授權條款。 |

## Review 證據基準

`0.6.0` 實機 Log 已證明 BLE／P2P／GC1 握手、版本與容量查詢、照片／影片控制、影片完整下載及媒體刪除可運作。Review 指出的相容性與資源回收問題仍予修正，但不得據此改動已驗證的封包順序或混用不同控制 profile。
