# BLE 與 Wi-Fi Direct 連線引導

HTC RE 第一次啟動時，手機先透過 BLE 建立控制通道。手機建立 Wi-Fi Direct group 後，將該 group 的 SSID 與 passphrase 經由 GATT 傳給 RE；RE 再以 station 模式加入 group，成功後由 BLE 通知回報相機 IPv4 位址。後續 HTTP 與 RTSP 都使用這條 IP 網路。

連線層會在 service discovery 後自動辨識兩種控制 profile：第一代硬體使用 `A000` service 與 `A201/A301/A302/A303/A304` characteristics；另一版使用 `5678` service 與 `CF01/CF02` 命令通道。兩者的長資料分段格式不同，不可混用。

## 必須遵守的寫入順序

1. 建立 BLE GATT 連線並探索 RE 控制服務。
2. 註冊 Wi-Fi 設定狀態 characteristic；第一代 `A000/A304` 使用本機 notification 註冊，其他 profile 收到 CCCD write callback 後才視為通知就緒。
3. 優先承接手機既有的 Wi-Fi Direct owner group；沒有可用 group 時建立新群組，取得 SSID、passphrase 與頻率。
4. 第一代 `A000` profile 先寫入 `A107={1}` 喚醒 RE 控制處理器，再寫入國別與頻段；所有寫入都使用同一序列佇列。
5. 寫入 SSID；若超過單包大小則依序分段。
6. 每一段都等待 `onCharacteristicWrite` 成功，才寫下一段。
7. 寫入 passphrase，規則同上。
8. 寫入 station/config 命令，內容含國別、頻段、WPA2、頻道與可選 IP 參數。
9. 等待 Wi-Fi 設定狀態通知，成功時解析 RE 的 IPv4 並自動更新 HTTP client；60 秒未回報則逾時。

任一時間只允許一個 in-flight GATT write。不得同時寫入多個 characteristic，也不得用固定延遲取代 callback。

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
- 實機驗證需涵蓋 2.4 GHz、5 GHz、長 SSID、不同密碼長度與中途斷線。
