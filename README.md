# 傷害及健康保險共保月帳單報表產生系統

讀取共保系統匯出的兩支 CSV，套用既有 Excel 樣板，產出當月共保月帳單的兩張報表（T 字帳、彙總表）。

- 執行環境：Windows + JDK 17
- 提供 **GUI**（雙擊 jar）與 **CLI 批次**（`run.bat`）兩種模式，共用同一核心服務層
- 規格文件見 `docs/`；架構與規則以 `..._DESIGN_系統設計.md`、`..._RULE_規則定義.md` 為準

---

## 快速開始

### 1. 建置

```
build.bat
```

產出 `target\health-and-injury-co-insurance-system.jar`（fat jar，含 JavaFX）。
本機無 `mvn`，一律使用專案內的 `mvnw.cmd`。

### 2. 準備資料

```
config/application.xlsx        設定年月（B1/B2）與共保公司清單（A6 起）
templet/PREMIUM_T_ACCOUNT.xlsx T 字帳樣板
templet/PREMIUM_SUMMARY.xlsx   彙總表樣板
input/{YYYMM}/VOLP{YYYMM}.csv  保費匯入檔（必要）
input/{YYYMM}/VOLC{YYYMM}.csv  理賠匯入檔（可缺，缺檔時賠款以 0 計）
```

`{YYYMM}` 為民國年 + 補零 2 位的月份，例如 115 年 5 月為 `11505`。
兩支 CSV 皆為 **Big5 / CP950** 編碼並含表頭列。

### 3. 執行

```
run.bat                          CLI 批次，年月取自設定檔
run.bat --year=115 --month=5     CLI 批次，年月由參數覆寫（優先於設定檔）
雙擊 target\...jar               開啟 GUI
```

產出位於 `output/{YYYMM}/`：

```
共保保費_當月共保月帳單_T字帳報表{YYYMM}.xlsx
共保保費_當月共保月帳單_彙總表{YYYMM}.xlsx
```

每次執行都會覆寫 `logs/report.json`（只留最後一次），內含匯入檔資訊、全部檢核錯誤、產出與備份清單、金額摘要。

---

## 執行結果代碼

| exit code | 意義 | 產出 |
| --- | --- | --- |
| 0 | 成功 | 兩張報表 + report.json |
| 1 | 匯入檔檢核失敗 | 僅 report.json（兩表皆不產出） |
| 2 | 前置資源／環境錯誤（缺檔、成分合計 ≠ 100%、備份失敗等） | 僅 report.json |
| 3 | 未預期錯誤 | 僅 report.json |

---

## 目錄結構

```
config/     設定檔（application.xlsx 由使用者維護；application.yml 為技術性設定）
templet/    Excel 樣板
input/      匯入檔（含個資，未納入版控）
output/     產出報表
backup/     舊報表備份，依 backup/{YYYMM}/{時間戳}/ 分層，保留 3 個月
logs/       application.log 與 report.json，皆只留最後一次執行
src/        原始碼（com.insurance.coinsurance）
docs/       規格與分析文件
```

---

## 幾個容易踩到的地方

程式已針對下列各點實作防護，維護時請勿繞過：

- **CSV 依位置索引取欄，不可依欄名**：兩支檔案各有兩個欄位叫「日額」，理賠檔的「已決賠款 合計」欄名中間還有半形空白。欄位定義集中在 `constant/PremiumColumn`、`ClaimColumn`，並於讀檔時驗證表頭。
- **攤付共保賠款須先篩「簽單年度 = 設定年」**（只篩年、不篩月）。漏篩會由正確的 126,931 變成 197,722。
- **中央再保（`N19`）採差額法**：分攤金額 = 總額 − 其餘公司加總，用來吸收其餘公司四捨五入的尾差，**不是**總額 × 12%。判定依公司代號，不依列號——設定檔中 N19 位於第 14 列而非最末列。
- **設定檔成分合計必須等於 100%**，不符即中止，不產出任何檔案。
- **捨入一律 HALF_UP**，且只能經 `calculator/RoundingUtil`。`BigDecimal` 預設是 HALF_EVEN，誤用會產生 ±1 元帳差；`StaticGuardTest` 會掃描原始碼把關。
- **共保管理費的捨入順序不可調換**：逐家分攤保費四捨五入 → ×5% → 再四捨五入 → 最後加總。先加總再乘會得到 17,506 而非正確的 17,504。
- **彙總表 A 欄不覆寫**，列順序以樣板為準，程式用公司名稱回查設定檔取得代號與成分；查無對應即中止。
- **`docs/規格書/產出範例/` 的金額是舊版成分算出來的，不可作為驗收基準**（R-06）。驗收數值以文件所載為準，`StaticGuardTest` 會檢查測試碼未引用那些舊值。

---

## 驗收基準（115 年 5 月樣本）

以 `docs/規格書/檔案位子範例/` 為輸入：

| 項目 | 值 |
| --- | --- |
| 共保保費 | 350,123 |
| 攤付共保賠款（簽單年度 115） | 126,931 |
| 共保管理費 | 17,504 |
| Balance Due | 205,688 |
| 中央再保應分配保費 | −42,018 |
| 中央再保應攤配賠款 | −15,228 |

執行 `mvnw.cmd test` 會以上述數值逐項斷言，並實際求值產出檔中的 Excel 公式。

---

## 開發

```
mvnw.cmd test                     執行全部測試
mvnw.cmd clean package            建置 fat jar（含測試）
mvnw.cmd clean package -DskipTests
```

`build.bat` / `run.bat` 內建指向本機 JDK 17 的路徑，換機器時請調整其中的 `JAVA_HOME`。
