# YouTube Live 第三階段

文件狀態：已依 RE Lens `0.6.1` 核對；控制面已實作，媒體 relay 尚未實作。

YouTube Live 放在「串流」頁，與 RE 即時預覽並列，避免新增第六個主要導覽項目。此階段拆成控制面與媒體面，兩者狀態不得混為一談。

## 已完成：控制面

- 以 Google Authorization API 取得 YouTube scope 授權，access token 僅保存在記憶體。
- 建立 `liveBroadcast` 與 RTMP `liveStream`，再綁定兩者。
- 顯示觀看網址與遮罩後的 RTMP endpoint。
- 確認 YouTube stream 狀態後切換 testing、live 與 complete。
- 日誌不輸出 access token 或 stream key。

## 待完成：媒體面

RE 提供的是 RTSP 預覽來源，而 YouTube ingest 使用 RTMP。仍需獨立的媒體 relay 將 RE 視訊讀取、必要時重新封裝或轉碼，再穩定推送至 YouTube。建立 broadcast/stream 並不代表影音已開始上傳，UI 必須維持此提示。

媒體面預定介面：`MediaSource` 讀取 RE、`MediaRelay` 處理時間戳／重連／緩衝與 RTMP 傳送、`StreamSession` 整合控制面與 relay 狀態。

## Android 新版適配

- 使用目前的 Google Play services authorization 流程，不依賴舊式登入 Activity。
- 授權結果經 `IntentSender` 回傳；使用者可重新授權。
- YouTube API 使用 HTTPS，與 RE 區域網路的 cleartext HTTP 例外分離。
- 背景推流需要獨立 foreground service、持續通知、網路綁定、喚醒策略與斷線恢復；媒體面完成前不宣稱支援背景直播。

## 相容性參考與授權界線

功能流程參考 [HTC-RE-YouTube-Live-Android](https://github.com/JALsnipe/HTC-RE-YouTube-Live-Android) 所呈現的使用情境。該專案未提供可辨識的授權條款，因此本專案未匯入其程式碼、資源或原生函式庫；目前功能以獨立實作及 Google 官方 API 建立。
