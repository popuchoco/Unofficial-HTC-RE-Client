# CI 與 Android 發行流程

## CI

`.github/workflows/android-ci.yml` 在推送到 `main`、針對 `main` 開啟／更新 Pull Request，或手動執行時會：

1. 使用 JDK 17、Android SDK 35 與 Gradle Wrapper 建置。
2. 執行 `testDebugUnitTest`、`lintDebug` 及 `assembleDebug`。
3. 將 Debug APK 保存為 14 天的 GitHub Actions artifact。

## Android 發行簽章

正式 APK 由 `.github/workflows/android-release.yml` 建置。Release workflow 只接受 `vX.Y.Z` 標籤，且標籤版本必須與 `app/build.gradle` 的 `versionName` 完全一致。發布前需在 GitHub 專案的 **Settings → Secrets and variables → Actions** 設定以下 Repository secrets：

| Secret | 內容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Java keystore 檔案的單行 Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密碼 |
| `ANDROID_KEY_ALIAS` | 簽章 key 的 alias |
| `ANDROID_KEY_PASSWORD` | 簽章 key 密碼 |

產生新的發行金鑰範例（在安全、非版本控制目錄執行；請將密碼管理在密碼管理器並保留離線 keystore 備份）：

```bash
keytool -genkeypair -v \
  -keystore re-lens-release.jks \
  -alias re-lens-release \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 re-lens-release.jks
```

Windows PowerShell 可使用以下指令輸出 keystore 的單行 Base64：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('re-lens-release.jks'))
```

將 keytool 詢問的 keystore 密碼與 key 密碼分別存入對應的 GitHub secrets。`ANDROID_KEYSTORE_BASE64` 的值是上個命令輸出的整行字串。不要把 keystore、密碼或 Base64 內容提交到 Git，也不要把它們放進公開 release。簽章私鑰遺失後，既有安裝的使用者將無法透過相同套件識別碼安裝後續更新；請妥善保存備份與密碼。

## 發布新版本

1. 更新 `app/build.gradle` 的 `versionName` 與遞增 `versionCode`，並同步更新 README 的版本與變更說明。
2. 確認 CI 通過且四個 signing secrets 已設定。
3. 將版本標籤推送至 GitHub，例如：

   ```bash
   git tag v0.6.3
   git push origin v0.6.3
   ```

4. Android Release workflow 會驗證版本標籤、建置並驗證簽章，接著建立 GitHub Release，附上 `RE-Lens-vX.Y.Z.apk` 與 `.sha256` 檔。

本次原始版本為 `0.6.3`（`versionCode` 47），第一個正式版標籤因此使用 `v0.6.3`。發布後可從 GitHub 專案的 **Releases** 頁下載 APK；安裝限制與 HTC RE 實機相容性現況仍以 README 及連線操作指南為準。
