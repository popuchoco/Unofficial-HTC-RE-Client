# 文件索引

文件基準：RE Lens `0.6.2`。本目錄已於 2026-09-30 全面巡檢；各文件開頭均標示適用版本或性質。未涉及本次 boot 競態的文件仍會保留其最近核對版本。

## 使用者文件

- [連線操作指南](CONNECTION_GUIDE.md)：首次設定、日常連線、重試與 Issue 回報。
- [產品與功能規格](SPECIFICATION.md)：五頁導覽、功能狀態及範圍界線。
- [Android 相容性](ANDROID_COMPATIBILITY.md)：SDK、權限、Receiver、前景服務及 Wi‑Fi Direct 差異。
- [YouTube Live](YOUTUBE_LIVE.md)：已完成的控制面與尚未完成的媒體 relay。

## 開發與維護文件

- [BLE 與 Wi‑Fi Direct 連線引導](CONNECTION_BOOTSTRAP.md)：現行 bootstrap 順序及安全限制。
- [A000 連線流程稽核](A000_CONNECTION_FLOW_AUDIT.md)：目前狀態機基準及保留的 0.4.x 歷史差異。
- [裝置通訊介面](PROTOCOL_NOTES.md)：GC1 command、分段下載與 RTSP 狀態。
- [軟體設計文件](SOFTWARE_DESIGN.md)：現行元件與規劃拆分的明確界線。
- [架構決策紀錄](DECISIONS.md)：ADR-001 至 ADR-010。
- [測試計畫](TEST_PLAN.md)：自動測試、實機矩陣與發布門檻。
- [Code Review 處理紀錄](CODE_REVIEW_RESPONSE.md)：外部審查項目的採納、保留與理由。

## 狀態用語

- **已完成實機驗證**：最新提供的硬體 Log 已證明該路徑成功。
- **已實作／待矩陣驗證**：程式與自動測試存在，但尚未覆蓋所有 Android／手機組合。
- **規劃／未來功能**：不應在 README 或 UI 宣稱可用。
- **歷史稽核**：只保留決策脈絡，不代表目前版本仍有該缺陷。
