# 正式發布與版本防護（v1.0.3 開發中）

## 分支規則
- `main` 是唯一可發布正式簽章 APK 的分支。
- `feature/*` 只供開發與測試；不可把測試 APK 當作正式更新檔。
- 功能完成後先檢查編譯、資料備份與升級相容，再合併回 `main`。
- 合併後可刪除已完成的功能分支；Git 提交歷史仍保留。

## 發布前檢查
1. `applicationId` 必須維持 `tw.costkeeper.restaurant`。
2. 新版 `versionCode` 必須大於「使用者已安裝的正式版」，不只大於 Git 內的前一版。
3. `versionName` 必須與 APK 實際 metadata 相符。
4. 必須使用同一組原正式簽章金鑰；不能把新產生的測試金鑰當作原金鑰。
5. 在 GitHub Actions Secrets 設定 `COSTKEEPER_CERT_SHA256`，內容必須是**已安裝正式版 APK 的簽章憑證 SHA-256 指紋**。工作流程會比較簽出來的 APK 指紋；不相符就拒絕發布。
6. 僅在 `main` 手動執行 `workflow_dispatch`，完成驗證後取得 `CostKeeper-verified-release`。
7. 檢查 `RELEASE_INFO.txt` 與 `SHA256SUMS.txt`，保留正式 APK、commit、版本、指紋與雜湊紀錄。
8. 實機測試覆蓋安裝及資料保留後，才提供朋友安裝。

## 注意：資料備份
- v1.0.3 新增 `monthlyExpenses` 按月營運費用資料。
- 加密 `.costbackup` 會保存整份 JSON 資料；目前 Excel 備份**尚未包含**新增營運費用。此限制未解除前，不可宣稱 Excel 可完整換機還原 v1.0.3 資料。
- 刪除 App 通常會刪除私有資料，不能以解除安裝當作更新方法。
- 所有 Secrets 僅存 GitHub Secrets，不可提交金鑰或密碼。

## 尚未完成
- 將營運費用納入 Excel 雙向備份與還原，並驗證舊版格式匯入。
- GitHub Actions 實際執行結果與 Samsung Galaxy Tab A9+ 實機測試。
- 取得已安裝舊 APK 的真實憑證指紋並配置發布 Secrets。
