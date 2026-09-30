# BLE 與 Wi-Fi Direct 連線引導

文件狀態：已依 `0.6.3` 程式與最新實機 Log 核對；群組重建恢復仍待下一輪實機驗證。

HTC RE 第一次啟動時，手機先透過 BLE 建立控制通道。手機建立 Wi-Fi Direct group 後，將該 group 的 SSID 與 passphrase 經由 GATT 傳給 RE；RE 再以 station 模式加入 group，成功後由 BLE 通知或 UDP 7777 回報相機 IPv4 位址。後續 GC1、檔案下載與 RTSP 都使用這條 IP 網路。

實機記錄顯示 GC1 在 2412 MHz 可完成 station 加入，但在 5180 MHz 連續回報 `A304 status=26`。Android 10（API 29）以上因此必須以公開 API 指定 2.4 GHz group；已存在的 5 GHz owner group 不得沿用，需先移除再重建。API 26–28 無公開頻帶指定 API，保留系統預設建立路徑並必須在實機確認頻率。

API 29+ 使用 `WifiP2pConfig.Builder` 建立自主 owner group 時，不能只設定頻帶；必須同時提供符合 `DIRECT-xy` 規則的 network name 與 8–63 字元 passphrase。App 每次建群時以 `SecureRandom` 產生新憑證，只將系統建群後回報的憑證透過 GATT 傳給 RE，不寫入 log。Builder 參數若遭 Android 拒絕，必須回報可讀錯誤而不得使 App 閃退。

連線層會在 service discovery 後自動辨識兩種控制 profile：第一代硬體使用 `A000` service 與 `A201/A301/A302/A303/A304` characteristics；另一版使用 `5678` service 與 `CF01/CF02` 命令通道。兩者的長資料分段格式不同，不可混用。

## 必須遵守的寫入順序

1. 建立 BLE GATT 連線；等待 ACL／GATT 穩定後探索服務，必要時完成 bonding。現行程式不依賴隱藏的 GATT cache refresh API。
2. 註冊 Wi-Fi 設定狀態 characteristic；第一代 `A000` 先依序訂閱 `AE01/AE02` multiplex notification，兩筆 CCCD callback 成功後才視為控制通道就緒；multiplex event 再映射回 `A101/A304`。
3. 建立或取得相容的 Wi-Fi Direct owner group，取得 SSID、passphrase 與頻率；既有 5 GHz group 必須移除重建。
4. 讀取標準 `2A26` BLE FW，依 `> 2250` 或 `<= 2250` 嚴格選擇單一 boot 分支；詳細順序見 [A000 連線流程稽核](A000_CONNECTION_FLOW_AUDIT.md)。A101 ready 與 A107 write／readback 都完成後，仍須節流 1.5 秒才可寫入國別與頻段。
5. 寫入 SSID；若超過單包大小則依序分段。
6. 每一段都等待 `onCharacteristicWrite` 成功，才寫下一段。
7. 寫入 passphrase，規則同上。
8. 寫入 station/config 命令，內容含國別、頻段、WPA2、頻道與可選 IP 參數。
9. 等待 Wi-Fi 設定狀態通知或 UDP 7777，成功時取得 RE IPv4；60 秒未回報則逾時。第一次逾時會關閉舊 UDP session、移除 owner group、建立新群組並完整重送一次；第二次仍失敗才停止。後續 A000 功能使用 GC1 socket，而非 port 3000 HTTP。

任一時間只允許一個 in-flight GATT write。不得同時寫入多個 characteristic，也不得用固定延遲取代 callback。

連線按鈕、Activity 重建與背景監看共用同一個連線管理器。只要 GATT 尚在連線或建立中，新的 connect 請求必須被拒絕；被替換的 GATT callback、重複 connected callback 與重複 service-discovery callback 不得重置狀態機或重跑認證。UDP 7777 接收器亦以 session generation 隔離，過期執行緒不得占用 port 或回填舊 IP。

第一代 A303 在稽核基準中是 write 後再 readback 並要求 payload 完全一致；`0.6.3` 現行佇列仍只確認 write callback。這是已知待補差異，不得把它記為完成，也不與本次群組重建修正混在同一個實機判讀中。

GC1 檔案通道的分段 wire offset 與實際寫入檔案的 byte count 必須分開計數：第一個 fragment 的 declared length 包含 1-byte status，因此寫入 32768 bytes 後，下一個 wire offset 為 32769。任一分段驗證失敗都必須關閉並重建 GC1 的 9000–9004 sockets，不得在含有殘留 frame 的 9003 socket 上繼續下一筆命令。

GC1 預覽控制逐筆等待 command 回覆，依序送出 `261`（Still mode，payload=`00`）、`234`（24 fps，payload=`60 09`）、`233`（M 尺寸，payload=`02`）、`235`（High 壓縮率，payload=`02`）及 `130`。`130` 回覆可帶 RTSP URI；`0x4012` 是獨立 ready 事件，不是 URI 回覆。這段控制流程已驗證，但現行 UI 不把 URI 交給播放器，因 RE 的 RTP/JPEG payload type 26 尚無相容 decoder。未來恢復預覽時仍需評估 `222/02` Control mode 與 `201` DR gate；停止命令 `131` 最多等待 3 秒，逾時需重建 GC1 session，避免阻擋相簿或拍攝。

Wi-Fi Direct group 建立前另有硬性前置條件：BLE 必須仍為 connected、RE 的短／長命令 characteristic 均已找到，而且狀態通知 CCCD 寫入成功。一般 BLE 周邊、無名廣播、尚未完成 service discovery 的裝置或已斷線的舊狀態，都不能進入 P2P 階段。

## 命令與資料格式

| 用途 | Command ID | Characteristic | 備註 |
|---|---:|---|---|
| Wi-Fi config | `0x21` | `CF01` | station mode、國別、頻段、安全性、頻道、選用 IPv4 |
| SSID | `0x22` | `CF02` | 長命令，可分段 |
| Passphrase | `0x23` | `CF02` | 長命令，可分段 |
| Config status | `0x26` | `CF01` notification | 狀態碼與 RE IPv4 |

長命令首包為 command、總長度高位、總長度低位及最多 17 bytes；續包為 command、遞增序號及最多 18 bytes。短命令為 command 後接 payload。

目前 IP 參數預設全為零，代表使用 DHCP；架構保留日後提供靜態 IPv4 的位置。頻率在 API 29 以上使用公開的 `WifiP2pGroup.getFrequency()`，不呼叫舊版隱藏 API。

## 安全與診斷

- Console 僅記錄階段、狀態碼、封包數與結果，不記錄 SSID、passphrase、OAuth token 或 YouTube stream key。
- 斷線時取消佇列與逾時計時器，不續送殘留封包。
- callback 回報失敗時立即停止，不猜測裝置已接受資料。
- 實機驗證需涵蓋 2.4 GHz、預先存在的 5 GHz group 重建、長 SSID、不同密碼長度與中途斷線。
