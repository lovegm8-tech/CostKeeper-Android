# 餐飲成本管家 v1.0.1（Android 原生離線版）

本專案以使用者提供的兩張 Google Sheets 截圖為需求參考，將食材／包材進價、配方、渠道毛利、價格紀錄及月報拆開管理。

## 已實作

- 食材與包材 CRUD、搜尋、分類與供應商、進貨批量與價格
- 單位成本：公斤／克、公升／毫升，以及顆／片／個／包／份等（同類單位換算）
- 配方：每份多食材用量；修改食材進價時自動更新所有餐點的目前估計成本
- 店內／外帶、Uber 與 Foodpanda 獨立售價及抽成；顯示每份估算毛利
- 銷售登記（日期、份數、通路）與月度營收／原料成本／抽成／估算毛利
- 銷售成本「快照」：歷史月報不會因為今天調價而回頭變動
- 食材進價／進貨規格變更紀錄
- Android Keystore 本機 AES-256-GCM 加密；手動以密碼匯出／匯入加密備份
- UTF-8 CSV 食材匯入（新增模式）
- **v1.0.1：完整 Excel（.xlsx）雙向備份／還原**：以原生 Java 實作，不需網路或第三方套件。食材、餐點、配方明細、進價紀錄及銷售紀錄分工作表保存；匯入前驗證欄位、資料型別與配方關聯，顯示筆數並由店主確認覆蓋。
- 不要求 INTERNET 權限、無帳號、無雲端、停用系統 App 資料備份

**請注意：** 售價、毛利分析仍是估算。未包含租金、人事、水電、稅金、耗損、折價券、平台其他服務費等。為求月報準確，必須逐筆或按日登記實際銷售數量；本版本不會自動讀取 POS。

## 保密設計

程式碼不包含來自截圖的真實成本數字。首次安裝為空白資料庫，保護營業資料。真實資料在手機手動輸入或從私有 CSV 匯入。
加密金鑰儲存在 Android Keystore；加密資料儲存在私有 App 目錄，且 `allowBackup=false`。
沒有網路傳輸的程式碼或 INTERNET 權限。使用者透過 Android 檔案選擇器自行匯出明文 CSV 或加密備份時，應自行保管檔案。

## 編譯方式（Android SDK 和網路依賴可用的電腦或 GitHub Actions）

- JDK 17 / Gradle 8.11.1 / Android Gradle Plugin 8.9.2 / Android SDK 35
- `gradle :app:assembleDebug` 只用於編譯驗證，不可拿 Debug APK 取代正式簽章版。
- 正式簽章版：環境變數 `COSTKEEPER_KEYSTORE_PATH`、`COSTKEEPER_STORE_PASSWORD`、`COSTKEEPER_KEY_PASSWORD`、`COSTKEEPER_KEY_ALIAS` 全部提供，再執行 `gradle :app:assembleRelease`。
- GitHub Actions 的 `workflow_dispatch` 手動觸發後使用四組 GitHub Secrets：`COSTKEEPER_KEYSTORE_B64`、`COSTKEEPER_STORE_PASSWORD`、`COSTKEEPER_KEY_PASSWORD`、`COSTKEEPER_KEY_ALIAS`；完成後在 Artifact 下載正式簽章 APK。
- **正式簽章 `*.jks / *.p12` 和密碼絕對不能提交到 GitHub。** 請妥善保存提供的私有簽章資料包，在下一版繼續使用同一組簽章。Github Secrets 必須配置在你另外建立的新專案。

## 固定版本資訊

- Application ID: `tw.costkeeper.restaurant`
- v1.0.0：`versionCode = 1`、`versionName = 1.0.0`（開發基線）
- v1.0.1：`versionCode = 2`、`versionName = 1.0.1`（新增 Excel 完整備份、還原）
- 下一版需繼續遞增 `versionCode`，但 **Application ID 與簽章不可變動**。
- 請直接安裝新版 APK 覆蓋舊版，**不要解除安裝**（解除安裝通常會清除離線資料）。
- 更新前先在 App 匯出密碼加密備份。

## Excel 完整備份與換機（v1.0.1）

在 App **備份** 頁面按「↓ 匯出完整 Excel 試算表」，使用 Android 的檔案儲存介面，選擇「下載」或私人目錄，產生 `.xlsx`。新手機安裝相同 App 後，按「↑ 從完整 Excel 還原（換手機）」選檔、確認筆數、覆蓋還原。**不必有舊手機 App 金鑰或網路**。

檔案內有六個工作表：**使用說明、食材、餐點、配方明細、進價紀錄、銷售紀錄**。其中 `ID`、配方引用 `餐點ID/食材ID` 是資料關聯鍵，請勿修改；單位成本、配方成本、店內毛利等「參考」欄可檢視，但還原後會以食材／配方重新計算。店主可編輯食材進價、每份用量、售價後再匯入。

注意：**完整還原只支援本 App 匯出的標準 .xlsx 格式，不支援任意 Excel 自動還原整庫**；一般試算表可先使用食材 CSV 匯入。Excel 為明文，未加密，包含成本、售價與毛利。建議同時保留具密碼的 `.costbackup` 作為保密備份。詳細操作見 `docs/EXCEL_TRANSFER.md`。

## 食材 CSV 格式

以 UTF-8 保存、第一列標頭：

```csv
食材名稱,分類,進貨數量,單位,進貨總價,供應商
示範白米,米飯,20,公斤,1200,示範供應商
示範雞蛋,蛋奶,30,顆,180,示範供應商
```

「進貨總價」必須與「進貨數量」的批次相對應；若是整箱價格，數量必須換算成整箱實際總量，不能只填單罐／單包規格。這在截圖中無法全數核實，因此沒有把每一列自動塞入資料庫。

## 已知第一版範圍與風險

- 同一種食材每批的平均進貨價格以最新價格為準；未實作批次庫存、先進先出、盤點、產品分量損耗率。
- 月報數字僅根據 App 內登記的銷售計算；沒有自動同步電商與外送平台。
- 每個餐點可以設定渠道售價與抽成；不會自動把促銷券、平台固定費／廣告費分攤進毛利。
- 備份檔採 AES-GCM＋PBKDF2，忘記備份密碼無法還原。因為停用系統備份，手機毀損時若沒有手動備份無法救回資料。
- 首次 Android 裝置實機測試仍需驗證 UI 排版、輸入法、加密備份／還原、Excel 檔案選擇及覆蓋升級流程。
- `.xlsx` 格式已在純 JVM 測試中通過資料來回轉換與修改進價後重新匯入，**不代表已經完成 Android APK 編譯或手機實測**。

## 重要檔案

- `app/src/main/java/tw/costkeeper/restaurant/MainActivity.java`：所有單機操作畫面
- `app/src/main/java/tw/costkeeper/restaurant/CostMath.java`：計價與配方計算
- `app/src/main/java/tw/costkeeper/restaurant/SecureStore.java`：本機 AES-GCM 與密碼備份
- `app/src/main/java/tw/costkeeper/restaurant/ExcelTransfer.java`：離線 Excel 匯出、匯入與完整資料驗證
- `.github/workflows/android.yml`：Android Build / 固定簽章正式 APK 編譯

`docs/TEST_CHECKLIST.md` 為 S26 Ultra／朋友 Android 手機的實機測試清單。
