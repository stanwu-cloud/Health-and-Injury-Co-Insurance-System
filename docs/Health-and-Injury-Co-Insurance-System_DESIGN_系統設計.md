# 系統設計書

## 1. 文件資訊

| 項目 | 內容 |
| --- | --- |
| 文件名稱 | 系統設計書 |
| 文件代碼 | Health-and-Injury-Co-Insurance-System_DESIGN_系統設計 |
| 目前版本 | **v1.2** |
| 建立日期 | 2026-08-03 |
| 最後更新 | 2026-08-10 |
| 作者 | AI 分析 |
| 狀態 | Draft |

> **文件維護原則**：單一常駐文件，改版直接更新本檔，版本歷程見 §13。

**依據**：`..._REQ_需求規格.md`（v1.0）、`..._RULE_規則定義.md`（v3.2）、`..._MAPPING_欄位對照.md`（v3.2）；架構參考 `D:\project\retained-premium-report-transformer` 與 `D:\project\excel-report-integration-engine`。

---

## 2. 系統總覽

### 2.1 定位

單機批次工具。讀取共保系統匯出之 CSV，套用既有 Excel 樣板，產出兩張月帳單報表。無資料庫、無網路服務、無使用者認證。

### 2.2 技術堆疊

| 項目 | 選型 | 依據 |
| --- | --- | --- |
| 語言 | Java 17 | 兩個參考專案一致 |
| 應用框架 | Spring Boot 3.5.0 | `retained-premium-report-transformer` 同版 |
| Excel 處理 | Apache POI 5.3.0（`poi` + `poi-ooxml`） | 兩個參考專案一致 |
| GUI | **JavaFX 21**（LTS，相容 Java 17） | 業務回覆 A-09(v0.3) |
| JSON | Jackson（Spring Boot 內建） | 產出 `report.json` |
| 日誌 | Logback（`logback-spring.xml`） | 參考專案慣例 |
| 建置 | Maven（`mvnw.cmd` wrapper） | 本機無 `mvn`，需 wrapper |
| 打包 | fat jar，雙擊可執行 | 業務回覆 A-09(v0.3) |

### 2.3 執行模式

| 模式 | 進入點 | 觸發 |
| --- | --- | --- |
| CLI 批次 | `java -jar xxx.jar --mode=cli [--year=115 --month=5]` 或 `拜託執行我.bat`（含編譯） | 承辦人員或排程 |
| GUI | 雙擊 jar，或 `java -jar xxx.jar`（預設） | 承辦人員 |

兩模式**共用同一核心服務層**，差異僅在輸入取得與結果呈現。

---

## 3. 架構設計

### 3.1 分層架構

```
┌──────────────────────────────────────────────────────┐
│  進入點層 (entry)                                     │
│  ┌────────────────────┐  ┌────────────────────────┐  │
│  │ CliRunner          │  │ FxApplication          │  │
│  │ (CommandLineRunner)│  │ (JavaFX Application)   │  │
│  └─────────┬──────────┘  └───────────┬────────────┘  │
└────────────┼─────────────────────────┼───────────────┘
             └───────────┬─────────────┘
                         ▼
┌──────────────────────────────────────────────────────┐
│  服務編排層 (service)                                 │
│  ReportGenerationService  ← 唯一對外入口              │
│    execute(ExecutionRequest) → ExecutionResult        │
└───┬────────┬─────────┬─────────┬─────────┬───────────┘
    ▼        ▼         ▼         ▼         ▼
┌───────┐┌────────┐┌────────┐┌────────┐┌────────────┐
│config ││reader  ││validator││calculator││writer     │
│設定讀取││CSV讀取 ││欄位檢核 ││金額計算  ││Excel 產出 │
└───────┘└────────┘└────────┘└────────┘└────────────┘
    │        │         │         │         │
    └────────┴─────────┴─────────┴─────────┘
                       ▼
          ┌─────────────────────────┐
          │ model / constant / util │
          │ exception               │
          └─────────────────────────┘
```

### 3.2 套件結構

```
com.insurance.coinsurance
├── CoInsuranceApplication.java        Spring Boot 進入點
├── entry/
│   ├── CliRunner.java                 CLI 模式 (CommandLineRunner)
│   ├── FxLauncher.java                fat jar 主類別 (不繼承 Application)
│   ├── FxApplication.java             JavaFX Application
│   └── MainController.java            JavaFX 畫面控制器
├── config/
│   ├── AppConfig.java                 路徑等外部設定
│   └── SettingReader.java             讀取 config/application.xlsx
├── constant/
│   ├── PremiumColumn.java             保費檔欄位索引 enum
│   ├── ClaimColumn.java               理賠檔欄位索引 enum
│   ├── TAccountCell.java              T 字帳儲存格常數
│   ├── ClaimTAccountCell.java         賠款 T 字帳儲存格常數（★二階段，不可與上者共用）
│   ├── SummaryCell.java               彙整表欄列常數
│   └── CoInsuranceConstants.java      N19、6%、會計格式字串等
├── model/
│   ├── Setting.java                   設定年月 + 公司清單
│   ├── CoInsuranceCompany.java        公司名稱/代號/成分
│   ├── PremiumRecord.java             保費檔一列
│   ├── ClaimRecord.java               理賠檔一列
│   ├── CalculationResult.java         M1~M8 計算結果
│   ├── ClaimYearSummary.java          單一簽單年度之賠款彙總（★二階段）
│   ├── ValidationError.java           檢核錯誤（五要素）
│   ├── ExecutionRequest.java          年月 + 是否參數覆寫
│   └── ExecutionReport.java           report.json 模型
├── reader/
│   ├── CsvReader.java                 Big5 解碼、表頭驗證、依索引取欄
│   ├── PremiumCsvReader.java
│   └── ClaimCsvReader.java
├── validator/
│   ├── PremiumValidator.java          R-VAL-01
│   ├── ClaimValidator.java            R-VAL-02
│   ├── PeriodValidator.java           R-VAL-03 年月一致性
│   └── FieldRules.java                長度/日期/數值等共用檢核
├── calculator/
│   ├── PremiumCalculator.java         R-CALC-01/02
│   ├── ClaimCalculator.java           R-CALC-03/04、R-CALC-17/18（★二階段分群與年度清單）
│   ├── AllocationCalculator.java      R-CALC-05/06（含差額法）
│   ├── ManagementFeeCalculator.java   R-CALC-07/08
│   └── RoundingUtil.java              HALF_UP 統一入口
├── writer/
│   ├── TAccountWriter.java            R-OUT-01
│   ├── ClaimTAccountWriter.java       R-OUT-08/09（★二階段，多份輸出）
│   ├── SummaryWriter.java             R-OUT-02/06
│   ├── ExcelStyleHelper.java          會計格式套用 R-OUT-03
│   ├── BackupService.java             R-PATH-06 備份與清除
│   └── ReportJsonWriter.java          R-OUT-07
├── exception/
│   ├── FatalException.java            中止型例外
│   └── ValidationFailedException.java 檢核失敗
└── util/
    ├── RocDateUtil.java               民國年/西元年、yyymmdd 驗證
    └── MaskUtil.java                  個資遮蔽 R-RUN-06
```

### 3.3 目錄結構（執行環境）

```
專案根目錄/
├── config/application.xlsx        設定檔（使用者維護）
├── templet/
│   ├── PREMIUM_T_ACCOUNT.xlsx
│   └── PREMIUM_SUMMARY.xlsx
├── input/{YYYMM}/
│   ├── VOLP{YYYMM}.csv
│   └── VOLC{YYYMM}.csv            （可缺）
├── output/{YYYMM}/                產出報表
├── backup/{YYYMM}/{時間戳}/       舊報表備份（保留 3 個月）
├── logs/report.json               執行報告（只留最後一次）
├── xxx.jar
├── build.bat                      僅編譯
└── 拜託執行我.bat                 編譯 + 執行（原 run.bat）
```

---

## 4. 模組切分

| 模組 | 職責 | 對應規則 | 對外介面 |
| --- | --- | --- | --- |
| **config** | 讀設定檔、組 `YYYMM`、成分合計檢核、建立「名稱→代號/成分」對照 | R-PATH-01/02 | `Setting load()` |
| **reader** | Big5 解碼、表頭驗證、依索引解析為 record | R-VAL-00 | `List<PremiumRecord> read(Path)` |
| **validator** | 逐列逐欄檢核，**收集全部錯誤不中斷** | R-VAL-01/02/03/04 | `List<ValidationError> validate(...)` |
| **calculator** | M1~M11 全部金額計算（含二階段之簽單年度分群與應產出年度） | R-CALC-01~20 | `CalculationResult calculate(...)` |
| **writer** | 套樣板、寫值與公式、套格式、改工作表名、備份、產 report.json；**二階段之多份輸出** | R-OUT-01~09、R-PATH-04/06/07 | `Path write(...)` / `List<Path> writeAll(...)` |
| **entry** | CLI 與 GUI 進入點、參數解析、結果呈現 | R-RUN-01~06 | — |
| **service** | 流程編排、跨報表一致性檢查 | R-CALC-16 | `ExecutionResult execute(ExecutionRequest)` |

### 4.1 關鍵設計決策

| # | 決策 | 理由 |
| --- | --- | --- |
| D1 | 欄位索引以 **enum** 定義（`PremiumColumn.TOTAL_PREMIUM(17)`） | 兩檔皆有重複欄名「日額」，禁止依名稱取值；enum 讓索引集中且可讀 |
| D2 | 所有金額以 **`long`** 保存，計算過程用 `BigDecimal` | 金額皆為整數元；`BigDecimal` 僅用於乘以成分與 6% 時控制捨入 |
| D3 | 捨入統一走 `RoundingUtil.round(BigDecimal)`，內部固定 `HALF_UP` | 避免各處誤用 `BigDecimal` 預設之 HALF_EVEN |
| D4 | 中央再保以常數 `N19` 判定，非依列號 | 設定檔列順序可能變動 |
| D5 | 彙整表 A 欄**不覆寫**，改以樣板 A 欄名稱查設定檔 | 列順序以樣板為準（B04），且可偵測名稱不一致（R-EXC-06） |
| D6 | 計算欄位寫入 **公式字串**而非數值 | 可稽核（NF-12）；中央再保列之 SUM 範圍依其實際列號動態產生 |
| D7 | 檢核採「收集後判定」而非「遇錯即拋」 | 需一次列出全部錯誤（A-18-2） |
| D8 | fat jar 主類別 `FxLauncher` **不繼承** `javafx.application.Application` | JavaFX 打包進 fat jar 時，主類別若直接繼承 `Application` 會因缺少模組路徑而啟動失敗 |
| D9 | GUI 與 CLI 共用 `ReportGenerationService`，該服務**不得** `System.exit()` 或直接印訊息 | 中止行為由進入點決定，服務層只回傳 `ExecutionResult` |
| **D10** | **賠款 T 字帳另立 `ClaimTAccountCell`，不重用 `TAccountCell`** | 兩者 `G20` / `G21` / `O20` 語意相反或不存在（P-03 / P-04）。若以參數化方式共用一組常數，將把「哪一格放 Balance Due」變成執行期分支，錯了不會編譯失敗、只會輸出到錯的格子 |
| **D11** | **`ClaimTAccountWriter.writeAll()` 逐年度獨立載入樣板** | POI 之 `Workbook` 帶有狀態；重複使用同一實例寫多檔會殘留前一年度的值與樣式。每份重新 `WorkbookFactory.create(template)` 成本極低（樣板 12 KB） |
| **D12** | **賠款 T 字帳一律寫入數值，不寫公式** | 該報表四個金額格皆為同一個來源值，無跨格運算；寫公式反而讓稽核者需要重算才看得到值。R-OUT-06 之「寫公式」僅適用彙整表 |
| **D13** | **`M10` 為空時回傳空清單而非拋例外** | 「本月沒有非當年度簽單資料」是正常業務狀態（P-06）；例外會讓整體 exit code 變成非 0，與 R-OUT-09 相衝 |
| **D14** | **賠款 T 字帳樣板之存在性檢查延後至確定要產出時** | 若 `M10` 為空卻因樣板缺檔而中止，會讓第一階段兩張報表也產不出來，與 R-OUT-09 相衝（對應 REQ 之 E16） |


### 4.2 第二階段新增元件

| 元件 | 職責 | 對應規則 | 對外介面 |
| --- | --- | --- | --- |
| `constant/ClaimTAccountCell` | 賠款 T 字帳之樣板名、工作表名、檔名樣式與**獨立**儲存格常數 | R-PATH-07、R-OUT-05/08 | 常數 |
| `model/ClaimYearSummary` | 單一簽單年度之彙總（年度 + 賠款總額 + 衍生西元年） | — | record |
| `calculator/ClaimCalculator`（擴充） | `claimByUnderwritingYear()` 分群；`reportYears()` 排除設定年並降冪 | R-CALC-17/18 | `Map<Integer,Long>` / `List<Integer>` |
| `writer/ClaimTAccountWriter` | 逐年度載入樣板、寫入 6 格、套會計格式、改工作表名、存檔 | R-OUT-08/09、R-CALC-19/20 | `List<Path> writeAll(Setting, CalculationResult, Path outputDir)` |

**`ClaimTAccountWriter.writeAll()` 之處理輪廓**：

```
writeAll(setting, calculation, outputDir):
    years = calculation.reportYears()            // 已排除設定年、已降冪
    if years.isEmpty():
        log.info("無非當年度簽單資料，未產出賠款月帳單")
        return List.of()                          // ★不拋例外（D13）
    template = templateDir / CLAIM_T_ACCOUNT.xlsx
    if !exists(template): throw FatalException     // ★延後至此才檢查（D14）
    outputs = []
    for y in years:                                // 降冪
        claim = calculation.claimByUnderwritingYear().get(y)
        wb = WorkbookFactory.create(template)      // ★每份獨立載入（D11）
        sheet = wb.getSheet("工作表1")
        write I3 = "{setting.year} 年 {MM} 月"      // J3 不動
        write P4 = String.valueOf(y + 1911)        // ★來源是 y
        writeMoney G6, G21, O20, O21 = claim       // ★G20 完全不碰
        writeMoney O6, G14 = 0
        rename sheet -> "共保賠款月帳單-T字帳"
        save to outputDir / OUTPUT_FILE_PATTERN.formatted(YYYMM, y)
        outputs.add(path)
    return outputs
```

**服務層編排之調整（`ReportGenerationService`）**：

1. `verifyConsistency()` 追加兩條不變式：`M9[configYear] == M3`、`Σ M9 == 理賠檔全部已決賠款合計`。
2. 備份階段（`BackupService`）之目標檔清單改為**動態**：固定兩檔 + `M10` 推導出的 N 個檔名。
3. 產出階段依序呼叫 `TAccountWriter` → `SummaryWriter` → `ClaimTAccountWriter.writeAll()`，將 `List<Path>` 併入 `ExecutionResult.outputFiles()`。
4. `ExecutionReport` 追加 `claimByUnderwritingYear` 與 `reportYears`；後者為空時附**可區分原因**的訊息（理賠檔缺檔 vs 全部簽單年度皆為設定年）。
5. 服務層仍**不得** `System.exit()` 或直接印訊息（D9 不變）。

---

## 5. 資料流設計

### 5.1 主流程

```
[進入點] CLI 參數 / GUI 輸入
   │  ExecutionRequest(year, month, overridden)
   ▼
[1] SettingReader.load()
   │  ├─ 檔案不存在/格式錯誤 ─────────► FatalException
   │  ├─ 名稱或代號重複 ──────────────► FatalException
   │  └─ 成分合計 ≠ 100% ────────────► FatalException
   │  Setting(year, month, companies[16])
   ▼
[2] 組 YYYMM（月份補零）；解析輸入/輸出/備份路徑
   │  保費檔不存在 ────────────────────► FatalException
   ▼
[3] CsvReader 解析（Big5、跳表頭、表頭驗證、索引取欄）
   │  解碼失敗/表頭不符/欄數不符 ──────► FatalException
   │  理賠檔不存在 → claims = 空集合（不視為錯誤）
   ▼
[4] Validator 逐列檢核（收集全部錯誤）
   │  errors.isEmpty() == false ──────► ValidationFailedException
   │                                     → 產 report.json，兩表皆不產出
   ▼
[5] Calculator
   │  M1 共保保費 / M2 各公司保費
   │  M3 攤付共保賠款(篩簽單年度) / M4 各公司賠款
   │  M5 應分配保費(N19 差額法) / M6,M7 管理費 / M8 Balance Due
   │  M9 簽單年度分群 / M10 應產出年度(排除設定年、降冪)      ★二階段
   ▼
[6] 跨報表一致性檢查（R-CALC-16，含 M9[設定年]==M3、ΣM9==全部賠款）
   │  不一致 ──────────────────────────► FatalException（程式缺陷）
   ▼
[7] BackupService：備份既有輸出檔 + 清除逾 3 個月備份
   │  備份失敗 ────────────────────────► FatalException（不覆寫）
   ▼
[8] Writer：載入樣板 → 寫值/公式 → 套格式 → 改工作表名 → 存檔
   │  TAccountWriter  → 1 檔
   │  SummaryWriter   → 1 檔
   │  ClaimTAccountWriter → N 檔（★二階段，逐年度迴圈；M10 為空則 0 檔）
   ▼
[9] ReportJsonWriter：覆寫 ./logs/report.json（產出清單含全部 2+N 檔）
   ▼
[進入點] CLI 印摘要並設定 exit code / GUI 顯示結果與錯誤表格
```

### 5.2 計算相依順序（重要）

```
M1 共保保費 ─────┬─► 彙整表 B23 ──┐
                 │                 ├─► F 欄公式基準 ($B$23)
M3 攤付共保賠款 ─┴─► 彙整表 C23 ──┘   G 欄公式基準 ($C$23)
                                   
M5 各公司分攤保費（正值，N19 用差額法）
        └─► M6 各公司管理費 = ROUND(M5 × 6%)
                └─► M7 管理費總額 = Σ M6
                        └─► M8 Balance Due = M1 − M3 − M7
```

**約束**：合計列 B23/C23 必須先算出，F/G 欄公式才有基準；M5 必須先逐家四捨五入，M6 才能正確計算。

**第二階段之相依（獨立於上圖，不共用中間結果）**：

```
理賠檔 ─► M9 簽單年度分群 (全年度，不篩選)
           ├─► M10 = M9.keys 排除 configYear，降冪
           │      └─► 逐年度 y：G6 = G21 = O20 = O21 = M9[y]
           │                    O6 = G14 = 0；G20 留空
           │                    P4 = y + 1911            ★來源是 y，非設定年
           └─► 不變式：M9[configYear] == M3
                       Σ M9 == 理賠檔全部已決賠款合計
```

**❗禁止**：賠款 T 字帳之 Balance Due **不得**經由 `M8 = M1 − M3 − M7` 求得。該報表之 Balance Due 直接等於 `M9[y]`；誤套 M8 會得 `0 − 38,331 − 0 = −38,331`（符號相反）。

---

## 6. 資料模型

### 6.1 核心模型（Java record）

| 模型 | 主要欄位 |
| --- | --- |
| `Setting` | `int year, int month, List<CoInsuranceCompany> companies` <br>衍生：`String rocYearMonth()`, `int adYear()`, `Map<String,CoInsuranceCompany> byName()` |
| `CoInsuranceCompany` | `String name, String code, BigDecimal share` <br>`boolean isReinsurer()` → `"N19".equals(code)` |
| `PremiumRecord` | 19 欄；計算僅用 `companyCode`、`totalPremium`（long，可負） |
| `ClaimRecord` | 22 欄；計算僅用 `companyCode`、`underwritingYear`、`totalSettledClaim` |
| `CalculationResult` | `long totalPremium, totalClaim, totalManagementFee, balanceDue`<br>`Map<String,Long> premiumByCompany, claimByCompany, allocatedPremium, managementFee`<br>**★二階段追加**：`Map<Integer,Long> claimByUnderwritingYear`（M9）、`List<Integer> reportYears`（M10，降冪） |
| `ValidationError` | `String fileName, int rowNumber, String fieldName, String actualValue, String ruleId, String message` |
| `ClaimYearSummary` | **★二階段**：`int underwritingYear, long totalClaim`<br>衍生：`int adYear()` → `underwritingYear + 1911`、`String fileNameSuffix()` → `"_%d年"` |
| `ExecutionRequest` | `int year, int month, boolean overriddenByArgs` |
| `ExecutionReport` | 見 MAPPING §9（F1~F10） |

### 6.2 欄位索引常數

```
PremiumColumn : BILL_YEAR(0) … TOTAL_PREMIUM(17), REMARK(18)          共 19
ClaimColumn   : BILL_YEAR(0) … UNDERWRITING_YEAR(5) …
                TOTAL_SETTLED_CLAIM(21)                                共 22
```

每個 enum 常數同時保存**預期表頭字串**，供 R-VAL-00 表頭驗證使用。

### 6.3 儲存格常數

| 常數 | 值 |
| --- | --- |
| `TAccountCell.PERIOD` | `I3` |
| `TAccountCell.UY_YEAR` | `P4` |
| `TAccountCell.CLAIM` / `PREMIUM` / `MGMT_FEE` | `G6` / `O6` / `G14` |
| `TAccountCell.BALANCE_DUE` / `LEFT_TOTAL` / `RIGHT_TOTAL` | `G20` / `G21` / `O21` |
| `SummaryCell.UY` / `PERIOD` | `A3` / `E3` |
| `SummaryCell.DETAIL_FIRST_ROW` | `7` |
| `SummaryCell.MGMT_FEE_RATE` | `0.06` |

**合計列列號 = 明細最末列 + 1**，依設定檔家數動態決定（現為 23）。

**★二階段 `ClaimTAccountCell`（獨立類別，不得與 `TAccountCell` 共用）**：

| 常數 | 值 | `TAccountCell` 同名/同格之差異 |
| --- | --- | --- |
| `TEMPLATE_FILE_NAME` | `CLAIM_T_ACCOUNT.xlsx` | 不同樣板 |
| `OUTPUT_SHEET_NAME` | `共保賠款月帳單-T字帳` | 不同 |
| `OUTPUT_FILE_PATTERN` | `共保理賠_當月賠款月帳單_T字帳報表%s_%s年.xlsx` | 兩個佔位符（YYYMM、簽單年度） |
| `PERIOD` | `I3` | 相同 |
| `UY_YEAR` | `P4` | 相同儲存格，**值之來源為簽單年度** |
| `CLAIM` | `G6` | 相同儲存格，值為該年度賠款 |
| `PREMIUM` / `MGMT_FEE` | `O6` / `G14` | 相同儲存格，**固定寫 0** |
| `BALANCE_DUE` | **`G21`** | ❗`TAccountCell.BALANCE_DUE` = `G20` |
| `RIGHT_TOTAL_UPPER` | **`O20`** | ❗`TAccountCell` **無此常數** |
| `RIGHT_TOTAL_LOWER` | `O21` | 對應 `TAccountCell.RIGHT_TOTAL` |
| — | **`G20` 不定義常數** | ❗`TAccountCell` 用它放 Balance Due |

> 類別 Javadoc 須明確標註：**本類別之 `G21` / `O20` 與 `TAccountCell` 語意不同，混用會產生錯位輸出**；並註明依據為第二階段問題追蹤清單之 P-03 / P-04。

---

## 7. 設定檔設計

### 7.1 `config/application.xlsx`（使用者維護）

| 儲存格 | 內容 | 備註 |
| --- | --- | --- |
| B1 / B2 | 設定年 / 設定月 | 可被 CLI 參數覆寫 |
| A6 起 | 公司名稱 / 代號 / 認受成分 | 動態讀至第一個空白列 |

**啟動檢核**：名稱不可空白且不重複、代號長度 3 且不重複、成分 > 0、**合計 = 100%**。

### 7.2 `config/application.yml`（程式設定，選用）

參考 `retained-premium-report-transformer` 之作法，以外部 yml 提供路徑等技術性設定，使用者通常無需修改：

```yaml
app:
  setting-file: ./config/application.xlsx
  template-dir: ./templet
  input-dir: ./input
  output-dir: ./output
  backup-dir: ./backup
  log-dir: ./logs
  backup-retention-months: 3
  mask-personal-data: true
```

【推論】`backup-retention-months` 與 `mask-personal-data` 抽為設定，便於日後調整（對應 R-02/R-03 之決策若變更可不改程式）。

---

## 8. 錯誤處理

### 8.1 例外分類

| 類別 | 語意 | 進入點行為 |
| --- | --- | --- |
| `FatalException` | 前置資源或環境問題（缺檔、格式錯誤、成分不符、備份失敗） | CLI：印訊息、exit code 2；GUI：彈出錯誤對話框 |
| `ValidationFailedException` | 匯入檔資料檢核失敗（攜帶 `List<ValidationError>`） | CLI：印全部錯誤、exit code 1；GUI：於表格列出全部錯誤 |
| 其他 `RuntimeException` | 未預期錯誤 | 記錄堆疊、exit code 3 |

**共通**：任一例外皆**不產出任何報表**（兩表同進退），但**仍產出 `report.json`** 以保留錯誤明細。

### 8.2 錯誤訊息格式

```
[規則代碼] 檔名 第 {行號} 列，欄位「{欄位名稱}」值「{實際值}」不符規則：{說明}
```

範例：

```
[R-VAL-02-11] VOLC11505.csv 第 3 列，欄位「被保險人出生日期」值「671116」不符規則：須為 yyymmdd 共 7 碼
```

個資欄位之 `{實際值}` 須先經 `MaskUtil` 遮蔽。

### 8.3 exit code

| 值 | 意義 |
| --- | --- |
| 0 | 成功產出兩張報表 |
| 1 | 匯入檔檢核失敗 |
| 2 | 前置資源／環境錯誤 |
| 3 | 未預期錯誤 |

---

## 9. 日誌設計

### 9.1 日誌檔

| 項目 | 設計 |
| --- | --- |
| 框架 | Logback（`logback-spring.xml`） |
| 位置 | `./logs/application.log` |
| 保留 | **只留最後一次執行** → 採 `append=false`，每次啟動覆寫 |
| 等級 | 預設 INFO；`com.insurance.coinsurance` 可調 DEBUG |
| 編碼 | UTF-8 |

### 9.2 關鍵日誌點

| 階段 | 內容 |
| --- | --- |
| 啟動 | 執行模式、處理年月、是否參數覆寫 |
| 設定檔 | 讀入公司家數、成分合計 |
| 讀檔 | 各檔案路徑、資料列數；理賠檔缺檔時記 WARN |
| 檢核 | 錯誤總數；逐筆錯誤（含五要素） |
| 計算 | M1/M3/M7/M8 四項摘要 |
| 一致性 | 三組跨報表比對結果 |
| 備份 | 備份來源與目的、清除之逾期目錄 |
| 產出 | 兩張報表路徑 |

### 9.3 個資遮蔽

`MaskUtil` 於**寫入任何輸出通道前**套用：

| 欄位 | 遮蔽規則 | 範例 |
| --- | --- | --- |
| 身分證號 | 保留前 3 碼與後 3 碼 | `A123456789` → `A12****789` |
| 被保險人姓名 | 保留首字，其餘以 `＊` 取代 | `王小明` → `王＊＊` |
| 出生日期 | 保留年、月日遮蔽 | `0671116` → `067****` |

---

## 10. 擴充性設計

| # | 可能變動 | 設計對應 |
| --- | --- | --- |
| X1 | 共保公司增減或成分調整 | 由 `config/application.xlsx` 動態讀取；程式不寫死清單。**惟樣板 A 欄需同步**，否則觸發 R-EXC-06 中止 |
| X2 | 中央再保換公司 | `N19` 抽為常數；若改為由設定檔標記「是否為差額承受者」可再降低耦合【推論】 |
| X3 | 管理費費率調整（**2026-08-10 由 5% 調為 6%**） | 抽為常數 `SummaryCell.MGMT_FEE_RATE`；若需逐年不同可移至設定檔 |
| X4 | 新增報表 | writer 層新增 `XxxWriter`，服務層加入編排；reader/validator/calculator 可重用。**第二階段之賠款 T 字帳即依此擴充點實作，reader 與 validator 完全重用、未改一行**（見 §4.2） |
| **X8** | **報表份數隨資料變動（一報表多檔）** | `ClaimTAccountWriter.writeAll()` 回傳 `List<Path>`；備份與 `report.json` 之產出清單皆改為動態組成。日後若彙整表也需分年，可沿用同一模式 |
| X5 | 匯入檔格式改版（欄位增減） | 欄位索引集中於 enum 與表頭常數，改動範圍受限 |
| X6 | 匯入檔改為 UTF-8 | 編碼抽為常數，可改設定；現階段固定 Big5 |
| X7 | 保留執行歷史 | 目前不實作（NF-07）；日後可將 `report.json` 改為帶時間戳並保留 N 份 |

---

## 11. 測試策略

| 層級 | 範圍 | 重點 |
| --- | --- | --- |
| 單元測試 | calculator、validator、util | 金額計算之精確值、捨入方向、差額法、日期與長度檢核、遮蔽格式 |
| 整合測試 | service 全流程 | 以 `檔案位子範例/` 為基準輸入，比對產出檔之儲存格值與公式 |
| 反向測試 | 例外路徑 | 缺檔、成分 ≠ 100%、表頭錯置、出生日期 6 碼、年月不符、名稱查無對應；**二階段：全部簽單年度皆為設定年（產 0 份仍成功）、以 M8 算 Balance Due 得負值** |
| 手動驗收 | GUI 與部署 | fat jar 雙擊、畫面錯誤清單、備份行為 |

**測試資料基準**：`docs/規格來源/第一階段-共保月帳單/檔案位子範例/`（R-04 已同步，可直接使用）。
**驗收數值基準**：本文件系列所載之計算結果（350,123 / 126,931 / **21,009** / **202,183** / **−24,512** / **+8,882**）；**第二階段為 113 年 32,460、114 年 38,331**，且 `32,460 + 38,331 + 126,931 = 197,722`。
**禁止事項**：**不得以 `docs/規格來源/第一階段-共保月帳單/產出範例/` 之金額作為比對基準**（R-06）。

完整測試案例見 `..._TEST_測試驗收.md`。

---

## 12. 待確認事項

| 編號 | 內容 | 等級 |
| --- | --- | --- |
| D-01 | 【推論】`config/application.yml` 之技術性設定項目（路徑、備份保留月數、遮蔽開關）為設計提案，未經業務方確認 | 低 |
| D-02 | 【推論】exit code 定義（0/1/2/3）為設計提案 | 低 |
| D-03 | 【推論】姓名與出生日期之遮蔽格式（業務僅明確指定身分證號 `A12****789`） | 低 |
| D-04 | 【推論】JavaFX 版本選 21 LTS；若部署環境限制需調整 | 低 |
| D-05 | GUI 畫面之具體版面（欄位配置、按鈕文字）尚未定稿，僅定義功能範圍 | 中 |
| **D-06** | 【推論】`ClaimYearSummary` 為設計提案；若實作時發現 `Map<Integer,Long>` + `List<Integer>` 已足夠，可不建立此 record | 低 |
| **D-07** | 賠款 T 字帳之年度組合逐月可能不同，前次多出之年度檔會留在 `./output/{YYYMM}/`。是否需在重跑時清空該月目錄，**待業務方確認**（同 REQ §11） | 低 |

---

## 13. 版本紀錄

| 版本 | 日期 | 內容 |
| --- | --- | --- |
| **v1.2** | **2026-08-10** | **納入第二階段（賠款 T 字帳）設計**：§3.2 套件結構新增 `ClaimTAccountCell` / `ClaimYearSummary` / `ClaimTAccountWriter`；§4 模組職責擴充至 R-CALC-01~20、R-OUT-01~09；新增 **§4.2 第二階段新增元件**（元件表、`writeAll()` 處理輪廓、服務層編排 5 點調整）；新增關鍵設計決策 **D10 ~ D14**（另立常數類別、逐份獨立載入樣板、寫值不寫公式、空清單不拋例外、樣板檢查延後）；§5.1 主流程與 §5.2 相依順序補上二階段分支與「禁止套用 M8」警示；§6.1 `CalculationResult` 追加 M9 / M10、新增 `ClaimYearSummary`；§6.3 新增 `ClaimTAccountCell` 常數對照表並標出與 `TAccountCell` 之差異；§10 新增擴充點 X8；§11 測試策略與驗收基準補列二階段；§12 新增 D-06 / D-07 |
| v1.1 | 2026-08-10 | 依 2026-08-10 業務調整同步：管理費率 5% → **6%**（`CoInsuranceConstants.MANAGEMENT_FEE_RATE` / `SummaryCell.MGMT_FEE_RATE` = `0.06`）；`run.bat` 更名為 `拜託執行我.bat` 並併入 `mvnw.cmd clean package -DskipTests`；驗收數值基準更新為 21,009 / 202,183 / −24,512 / +8,882 |
| v1.0 | 2026-08-03 | 初版；定義分層架構、套件結構、7 大模組、9 項關鍵設計決策、主流程與計算相依順序、資料模型、設定檔、錯誤處理與 exit code、日誌與個資遮蔽、7 項擴充點、測試策略 |
