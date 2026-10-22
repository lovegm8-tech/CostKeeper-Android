# 固定簽章及正式發行

## v1.0.1 發行驗證（2026-10-23）

- 套件 ID：`tw.costkeeper.restaurant`
- 版本：`1.0.1`；`versionCode=2`
- 使用 `CostKeeper_v1_signing_PRIVATE.zip` 中原本的 `costkeeper-release.p12`，**沒有產生新的私鑰**。
- SHA-256 憑證指紋：`1E:3A:EB:D3:CB:EA:FA:FE:B2:82:29:72:22:90:17:DB:F3:45:93:D5:69:87:D3:A7:BF:80:1F:D1:94:35:D5:C2`。
- APK 使用 Android APK Signature Scheme v2、v3，並透過 Android 官方 `apksigner verify --verbose --print-certs` 通過簽章驗證。
- 公開 GitHub Actions 只執行編譯並提供未簽名 Release APK。正式私鑰與密碼只在受控本機簽署，不會放進公開儲存庫或公開 Artifact。
- GitHub 亦保留以 repository secrets 執行 signed-release 的工作流程，但未設定 Secrets 時，不能直接產生已簽名 Release。這不是發行 APK 的必要途徑。

## 後續更新必須遵守

1. 不更動 `applicationId`。
2. 每次遞增 `versionCode`，且不得重用較低版號。
3. 一律沿用相同的正式簽章金鑰和簽章憑證。
4. **永遠不要**將 `*.p12`、keystore base64、密碼、商店實際成本資料加入程式碼。
5. APK 簽章後用 `apksigner verify` 檢查 v2/v3 與簽章指紋，並執行實機覆蓋更新測試。
6. 升級前先匯出資料；解除安裝可能清除本機資料。

> 注意：簽章與編譯通過不等於通過手機端功能測試；Excel 匯入、備份還原與資料保存仍須在實機驗證。
