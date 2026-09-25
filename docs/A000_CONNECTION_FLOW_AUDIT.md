# A000 連線流程稽核

狀態：2026-09-25 完成第一輪靜態流程稽核。此文件是後續連線層修改的基準；在表內的必要前置條件與分支尚未實作、且沒有實機封包證據前，不以調整 timeout、重試次數或猜測 payload 的方式發版。

## 稽核結論

目前 `0.4.19` 已能完成 BLE 掃描、GATT 連線、A105/A106 密碼驗證及 AE01/AE02 訂閱，但初始化流程尚未忠實重現 A000 狀態機：

1. 啟動分支使用的是 **BLE firmware version**，來源是標準 Device Information service `180A` 的 Firmware Revision String `2A26`，不是 A108 內的 Boot code version。
2. 參考流程在密碼驗證成功後先啟用 AE01/AE02，再讀取 `2A26` 並寫入裝置模型；後續 boot callable 才能依 `BLE FW > 2250` 選定唯一分支。
3. `BLE FW > 2250` 分支不先讀 A101，而是先建立 A101 waiter，再寫入 A107=`01`。
4. `BLE FW <= 2250` 分支才先讀 A101；只有 bit 0 為 0 時才建立 waiter 並寫入 A107=`01`。
5. 底層 GATT queue 嚴格單工，必須等待 callback 才處理下一筆，並在裝置操作之間保留約 1.5 秒節流。現行 App 只有 callback serialization，沒有等價節流。
6. `0.4.19` 是兩個 boot 分支的混合版本，因此不能再把它的 A101=`00` 結果視為忠實執行任一參考分支的證明。

## 裝置模型與版本來源

| 欄位 | 來源 | 解析 | 寫入時機 | 用途 |
|---|---|---|---|---|
| BLE firmware version | service `180A`, characteristic `2A26` | ASCII 十進位字串，例如 `2251` → integer 2251 | 密碼驗證通過後的初始化工作 | boot 與 Wi-Fi retry 分支條件 `> 2250` |
| Main firmware version | A108 bytes 0..3 | `u16LE(0) * 10000 + u16LE(2)` | boot 成功後執行 Get All Version | 顯示及功能相容性 |
| Boot code version | A108 bytes 4..7 | `u16LE(4) * 10000 + u16LE(6)` | boot 成功後執行 Get All Version | 顯示及更新判斷；不是 boot 分支條件 |
| MCU version | A108 bytes 8..11 | `u32LE(8)` | 同上 | 顯示及功能相容性 |
| A108 內 BLE version | A108 bytes 12..15 | `u32LE(12)` | 同上 | 完整版本資訊；初始化分支仍以 `2A26` 寫入的值為準 |

重要循環關係：讀 A108 的工作本身會先呼叫 boot，因此不能靠 A108 決定第一次 boot 分支。第一次 boot 前唯一已知的分支輸入是 `2A26`。

## 端到端狀態轉移表

| 狀態 | 必要前置 | 動作 | 成功條件／回應來源 | 下一狀態 | 失敗處理 |
|---|---|---|---|---|---|
| `IDLE` | Bluetooth 可用且具掃描權限 | 掃描廣播，只接受 GC1/A000 識別或 `hTC GC` | 符合條件的單一 RE | `RE_DISCOVERED` | timeout 或 Android scan error，停止本輪 |
| `RE_DISCOVERED` | 已選定 RE address | `connectGatt(autoConnect=false)` | GATT connected callback | `GATT_STABILIZING` | 關閉該 GATT instance |
| `GATT_STABILIZING` | GATT connected | 等待約 3 秒，再探索服務 | service discovery success | `SERVICES_READY` | discovery error；不得進 P2P |
| `SERVICES_READY` | A000、180A 及必要 characteristics 存在 | 確認 profile、建立單一操作 queue | A101/A105/A106/A107/AE01/AE02/2A26 可取得 | `PASSWORD_VERIFYING` | 缺任一必要 characteristic 即停止 |
| `PASSWORD_VERIFYING` | A105/A106 可用 | A106 notify on；A105 寫入驗證 frame | A106 回傳 0 或 2 | `VERIFIED` | 1 或 3 表示密碼不正確；等待使用者輸入後重試 |
| `VERIFIED` | 密碼正確 | 依序排入初始化工作 | 不以單一 callback 判定全部完成 | `EVENT_CHANNEL_INIT` | 任一步失敗需記錄 characteristic 與 payload |
| `EVENT_CHANNEL_INIT` | 已驗證 | AE01 CCCD=`01 00`，完成後 AE02 CCCD=`01 00` | 兩次 descriptor callback status=0 | `BLE_FW_READING` | 禁止並行 descriptor write |
| `BLE_FW_READING` | multiplex 已啟用 | 讀 `2A26` | 非空 ASCII 十進位並成功存入 device model | `BLE_FW_KNOWN` | 不得以 unknown 或 `-1` 默認選舊分支 |
| `BLE_FW_KNOWN` | `bleFw` 已知 | 建立手機端 Wi-Fi Direct owner group並取得 SSID/passphrase | group state=`CREATED` 且憑證非空 | `P2P_GROUP_READY` | 依 Android callback 重試 group info |
| `P2P_GROUP_READY` | group 已建立 | 呼叫 boot task | 嚴格依下表選一條分支 | `BOOT_WAITING` 或 `BOOT_READY` | 不允許混合分支 |
| `BOOT_READY` | A101 bit 0=1 | 開始 station task | boot task result=0 | `WIFI_BOOTSTRAP` | 最多五次完整 boot attempt |
| `WIFI_BOOTSTRAP` | P2P group 存在且 boot ready | A201 → A301 → A302；先建立 A304 waiter，再送 A303 | 每次 write callback 成功；A303 另需 readback 相等 | `IP_WAITING` | 任一步失敗即中止本次 attempt |
| `IP_WAITING` | A303 已接受 | 並行等待 A304 與 UDP/7777 | 任一路徑先取得成功結果與 IPv4 | `IP_READY` | 20 秒 timeout；依 BLE FW 選擇下一組 retry 參數 |
| `IP_READY` | camera IPv4 已知 | 將 HTTP/RTSP 綁定到 Wi-Fi Direct network | HTTP health probe 成功 | `CONTROL_READY` | 保留 BLE，報告網路層錯誤 |

## Boot 的互斥分支

### 分支 N：BLE FW > 2250

每次 attempt 必須按以下順序：

1. 在寫入前建立 A101 waiter，timeout 3000 ms。
2. A107 寫入 `01`，等待 characteristic write callback。
3. 讀回 A107，要求 payload 完全等於 `01`。
4. 等待 A101 事件；只有 UUID=A101 且 `(value[0] & 0x01) == 1` 才成功。
5. timeout 或非 ready 結果視為本次失敗，最多五次完整 attempt。

此分支沒有「先讀 A101」步驟。

### 分支 L：BLE FW <= 2250

每次 attempt 必須按以下順序：

1. 讀 A101。
2. 若 `(value[0] & 0x01) == 1`，直接成功，不寫 A107。
3. 若 bit 0=0，先建立 A101 waiter，timeout 2500 ms。
4. A107 寫入 `01`，等待 write callback。
5. 讀回 A107，要求 payload完全等於 `01`。
6. 等待 A101 ready 事件；失敗時最多重做五次完整 attempt。

## Characteristic 與原始 frame

所有數值均為 hexadecimal；密碼及 Wi-Fi 憑證只允許記錄長度和雜湊，不得寫入操作 Log。

| 階段 | Characteristic | 方向 | 原始 frame／解析 | 回應來源 | 狀態影響 |
|---|---|---|---|---|---|
| 驗證密碼 | A105 | write | `00 || password_ascii` | write callback + A106 | 等待結果 |
| 變更密碼 | A105 | write | `01 || new_password_ascii` | write callback/readback | 密碼更新 |
| 密碼結果 | A106 | notify | `00` 未變更且正確；`01` 未變更且錯誤；`02` 已變更且正確；`03` 已變更且錯誤 | A106 notification | 0/2 → verified |
| 長期事件 | AE01 | CCCD | `01 00` | descriptor callback | multiplex primary ready |
| 長期事件 | AE02 | CCCD | `01 00` | descriptor callback | multiplex secondary ready |
| BLE FW | 2A26 | read | ASCII decimal | read callback | 設定 `bleFw` |
| boot 狀態 | A101 | read/event | bit 0：0 standby、1 ready | direct read 或 AE01/AE02 event ID `0x11` | boot state |
| boot 命令 | A107 | write + read | `01`；readback 必須完全相同 | write callback、read callback | 等待 A101 |
| 國別／band | A201 | write | `01 00 country[1] country[0]`；例如 TW → `01 00 57 54` | write callback | Wi-Fi bootstrap step 1 |
| P2P SSID | A301 | write | 下列 18-byte fragmentation | 每片 write callback | step 2 |
| P2P passphrase | A302 | write | 下列 18-byte fragmentation | 每片 write callback | step 3 |
| station config | A303 | write + read | `[random_high_nibble | 01, 04, mode]`；station mode=`01`，soft AP=`08` | write callback + exact readback | 開始連線 |
| Wi-Fi result | A304 | event | byte 1=status；byte 2 起為 IPv4 octets | AE01/AE02 event ID `0x34` | status=0 → IP ready |
| IP workaround | UDP 7777 | receive | payload內容不參與解析；以 datagram source address 為 camera IP | UDP source address | IP ready |

A301/A302 的 GC1 fragmentation：每片最多 18 bytes payload；byte 0=`payload_length`，最後一片再 OR `0x80`；byte 1=`absolute_offset`；byte 2 起為 payload。每片都必須完成 characteristic callback，才可送下一片。

## GATT 排程規則

- 同一時間只允許一筆 read、write 或 descriptor write in flight。
- 下一筆必須等待對應 callback，而不是只依 `writeCharacteristic()` 的 boolean 回傳值。
- 參考 queue 在一次裝置操作開始後，將同裝置下一次可操作時間設在約 1500 ms 之後；這是流程的一部分，不等同於任意 timeout。
- 長 frame 的每個 fragment 亦受同一 queue 管理。
- waiter 必須在可能觸發事件的 write 之前註冊。
- retry 是重做完整 transaction，不是在同一個 in-flight 操作上並行補寫。

## Wi-Fi station retry 矩陣

station task 最多三個外層 attempt；失敗後會移除 P2P group，再依 BLE FW 調整參數。若第一次結果不是特定可重試錯誤，流程可能直接減少剩餘次數。

| BLE FW | 較前 attempt | 最後 attempt | 說明 |
|---|---|---|---|
| `> 2250` | connectMethod=1, convertUTF8=1 | connectMethod=1, convertUTF8=0 | A303 mode 維持 station；SSID 走新版方法 |
| `<= 2250` | connectMethod=0, convertUTF8=1 | connectMethod=0, convertUTF8=0 | 舊版 SSID 方法 |

每個 station attempt 內部仍會先確認／建立 P2P group，再執行 boot，之後才寫入 A201/A301/A302/A303。

## 0.4.19 與基準流程的差異

| 項目 | 0.4.19 | 稽核基準 | 風險 |
|---|---|---|---|
| BLE FW | 未讀 2A26 | boot 前必須讀取並解析 | 無法選定正確分支 |
| boot | 先讀 A101，再寫 A107，逾時後輪詢 A101 | 依 BLE FW 嚴格二選一 | 混合流程沒有可比對基準 |
| waiter | multiplex 已訂閱，但 transaction waiter 與 write 的先後未獨立建模 | write 前建立本次 waiter | 事件可能無法歸屬正確 attempt |
| GATT 節流 | callback 後立即送下一筆 | 約 1.5 秒裝置節流 | 舊 BLE firmware 可能接受 callback 但尚未完成內部處理 |
| 初始化 | 驗證後只啟用必要通知 | 還會讀 BLE FW，並排入時間／版本／長期事件初始化 | device model 不完整 |
| station retry | 單一路徑 | 依 BLE FW 與結果調整參數並重建 group | 失敗恢復行為不同 |

## 實作前的必要工作

1. 新增明確的 `A000ConnectionState`，禁止以多個鬆散 boolean 代表同一狀態。
2. 將 `2A26` 列為 A000 必要 characteristic；解析失敗時停止，不猜版本。
3. 把 read、write、descriptor write、waiter 全部納入同一 transaction queue，並加入可測試的 1500 ms device throttle。
4. 分別實作並單元測試 `NEW_FW_BOOT` 與 `LEGACY_FW_BOOT`；兩者不得共用會改變順序的 fallback。
5. 先以 dry-run trace 測試狀態與 frame，再改實機流程。
6. 每筆診斷記錄統一輸出：transaction ID、state before/after、operation、UUID、payload hex（敏感值遮蔽）、callback type、status、elapsed ms、retry/branch。

## HCI／GATT 動態比對門檻

完成上述忠實流程後，若仍出現「A107 write status=0、readback=`01`，但 A101 不轉 ready」，靜態流程不足以判斷 RE 內部拒絕原因。此時才進行一次可正常連線環境的動態擷取，並逐筆比對：

- GATT connect 到 service discovery 的實際間隔。
- MTU、connection parameter、pairing/bonding 是否發生。
- A105/A106、AE01/AE02、2A26、A101/A107 的精確順序及時間間隔。
- A107 前是否存在目前未納入的 read/write/descriptor operation。
- A101 ready 是 direct notification 還是 AE01/AE02 multiplex event。
- 手機端 P2P group 建立相對於 boot transaction 的時間。
- A201/A301/A302/A303 每片 payload 與間隔。
- A304 與 UDP/7777 哪一路先回報 IP。

動態 trace 必須去除 MAC、SSID、passphrase 等識別或憑證資料後才能納入 issue／文件。

## 發版閘門

下一個連線版本只有在以下條件全部成立後才建立：

- 稽核表中的必要狀態已在程式中明確表示。
- 2A26 已成功讀取，Log 能顯示實際 branch，但不洩漏憑證。
- 單元測試能證明兩個 boot 分支的 operation order。
- queue 測試能證明 callback 前不前進，且符合 device throttle。
- 實機 Log 能逐列對應本文件的 transaction trace。
