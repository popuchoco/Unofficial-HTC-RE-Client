# 連線操作指南

文件狀態：適用 RE Lens `0.6.1`。

本指南說明 RE Lens 如何在 Android 手機與 HTC RE 之間建立 BLE 控制通道、Wi‑Fi Direct 網路及 GC1 socket 通道。一般使用者不需要手動輸入 Wi‑Fi Direct 的 SSID 或密碼。

## 連線前準備

- 將 RE 開機並放在手機附近；手機的藍牙與 Wi‑Fi 都必須開啟。
- 首次執行時允許「附近裝置」及系統要求的定位權限。App 的 target SDK 維持 32，因此 Android 12+ 使用 `BLUETOOTH_SCAN` 與 `BLUETOOTH_CONNECT` 路徑。
- 在「連線」頁輸入目前的 RE 密碼。恢復原廠設定後，使用「首次設定／重設後設定新密碼」流程。

## 日常連線

1. 在最左側的「連線」頁確認藍牙與 Wi‑Fi 都顯示已開啟。
2. 按「掃描 RE」，清單只會接受符合 RE 服務識別條件的裝置；找到後按「連線」。
3. App 會依序完成 GATT 連線、密碼驗證、通知訂閱、BLE 韌體版本讀取及 boot 狀態確認。請勿快速重複點擊按鈕。
4. App 建立 2.4 GHz Wi‑Fi Direct owner group，再透過單工 GATT 佇列依序送出 group SSID、passphrase 與 station config。每筆寫入都必須等 characteristic callback，且操作間至少節流約 1.5 秒。
5. RE 加入群組後會回報 IPv4 位址。App 隨後按需建立 GC1 的 `9000`–`9004` socket，完成 `501` 握手後才能拍照、錄影、讀取裝置資訊或操作相簿。

連線成功時，頁面會分別顯示 BLE、Wi‑Fi Direct、裝置 IP 與 GC1 狀態。頂端連線狀態位於裝置 IP 下方，避免窄螢幕截斷訊息。

## 首次設定或重設密碼

1. RE 恢復原廠設定後，先掃描並建立 BLE 連線。
2. 選擇「首次設定／重設後設定新密碼」，輸入新的 RE 密碼。
3. App 先驗證出廠狀態，再寫入並重新驗證新密碼；完成前不要離開連線頁或關閉 RE。
4. 新密碼只儲存在手機的 App 設定中；匯出的操作 Log 會遮蔽密碼與 passphrase。

## 連線異常

若 Android 偶發拒絕 GATT 操作、通知沒有完成訂閱，或 Wi‑Fi bootstrap 沒有繼續：

1. 保持 RE 開機且靠近手機，在「連線」頁再執行一次 Wi‑Fi Direct 連線，等待流程完成。
2. 若仍失敗，斷線後等待數秒再重新掃描；必要時切換一次手機藍牙或重新啟動 RE，以清除系統殘留的 GATT session。
3. 到「裝置」頁開啟偵錯並匯出操作 Log，在 [GitHub Issues](https://github.com/popuchoco/Unofficial-HTC-RE-Client/issues/new) 說明手機型號、Android 版本及重現步驟。請先確認 Log 不含個人資料。

## 已完成實機驗證

- BLE 認證、Wi‑Fi Direct bootstrap、RE IP 回報與 GC1 `501` 握手。
- 拍照、開始／停止錄影及裝置版本顯示。
- microSD 可用／總容量查詢。
- 照片與影片分段下載到 Android MediaStore。
- 經二次確認後刪除 RE 上的媒體原檔。

MediaStore 每次下載建立新的 pending 項目；失敗時會移除未完成項目，重新操作會由頭下載。目前不宣稱跨工作階段斷點續傳。
