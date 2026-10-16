# Auto Ping - Android 持續 Ping 測試工具

專為 Android 設計的極速網路持續 Ping 監控工具。

## ✨ 特色功能

1. **點開即跑**：開啟 App 即刻自動對指定 IP 啟動持續 Ping，無需每次手動點擊開始。
2. **隨時自訂 IP & 間隔**：
   - 預設目標 `8.8.8.8`（可自訂為任意 IP 或網域名稱，如 `192.168.1.1` 或 `google.com`）。
   - 間隔時間可自由調整（預設 1.0 秒）。
3. **記憶功能**：自動記住您最後設定的 IP 與間隔時間，下次開啟 App 自動載入並開始。
4. **即時統計看板**：
   - 發送次數（Sent）
   - 接收成功次數（Recv）
   - 封包遺失率（Loss %）
   - 即時平均延遲（Avg ms）
5. **防休眠常亮開關**：內建「保持螢幕常亮」開關，避免手機待機螢幕關閉時網路進入節能休眠導致測試中斷。
6. **黑底綠字終端介面**：即時自動滾動輸出標準 Linux Ping 回應，支援一鍵清除與一鍵複製日誌。

---

## 🚀 如何透過 GitHub Actions 免費自動打包 APK

您完全不需要在電腦安裝肥大的 Android Studio 或 Java 環境，只要將此專案推送到您的 GitHub，GitHub 雲端就會在 1 分鐘內自動編譯出安裝檔（`.apk`）：

### 步驟 1：在 GitHub 建立一個新 Repository
1. 前往 [GitHub](https://github.com/new) 建立一個新儲存庫（例如命名為 `auto-ping-app`），設為 Public 或 Private 皆可。

### 步驟 2：將本目錄推送至 GitHub
在專案根目錄執行以下指令（請將 `<你的-github-帳號>` 與 `<你的-repo-名稱>` 換成你的）：

```bash
cd C:\Users\alan0\.gemini\antigravity-ide\scratch\auto-ping-app
git init
git add .
git commit -m "feat: init auto-ping-app with github actions"
git branch -M main
git remote add origin https://github.com/<你的-github-帳號>/<你的-repo-名稱>.git
git push -u origin main
```

### 步驟 3：下載 APK 安裝到手機
1. 推送完成後，打開 GitHub 專案頁面，點擊上方的 **Actions** 頁籤。
2. 您會看到 **Build Android APK** 工作流程正在自動運行（約 1~2 分鐘）。
3. 綠色勾勾完成後，點擊進入該次 Run，在最下方的 **Artifacts** 區塊即可下載 **`AutoPing-v1.0.0-APK`**。
4. 解壓縮後將 `.apk` 傳到手機（或手機直接用瀏覽器下載），點擊安裝即可使用！
