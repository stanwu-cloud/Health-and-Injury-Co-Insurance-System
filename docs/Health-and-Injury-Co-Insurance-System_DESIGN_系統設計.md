# 系統設計書

## 1. 文件資訊

| 項目 | 內容 |
| --- | --- |
| 文件名稱 | 系統設計書 |
| 文件代碼 | Health-and-Injury-Co-Insurance-System_DESIGN_系統設計 |
| 目前版本 | **v1.8** |
| 建立日期 | 2026-08-03 |
| 最後更新 | **2026-08-18** |
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
│   ├── ClaimSummaryCell.java          賠款彙總表欄列常數（★第四張報表，不可與上者共用）
│   └── CoInsuranceConstants.java      N19、6%、會計格式字串等
├── model/
│   ├── Setting.java                   設定年月 + 公司清單
│   ├── CoInsuranceCompany.java        公司名稱/代號/成分
│   ├── PremiumRecord.java             保費檔一列
│   ├── ClaimRecord.java               理賠檔一列
│   ├── CalculationResult.java         M1~M8 計算結果（★二階段追加 M9/M10、★第四張追加 M12）
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
│   │                                  R-CALC-21（★第四張報表：年度 × 公司二維分群 M12）
│   ├── AllocationCalculator.java      R-CALC-05/06（含差額法）
│   ├── ManagementFeeCalculator.java   R-CALC-07/08
│   └── RoundingUtil.java              HALF_UP 統一入口
├── writer/
│   ├── TAccountWriter.java            R-OUT-01
│   ├── ClaimTAccountWriter.java       R-OUT-08/09（★二階段，多份輸出）
│   ├── SummaryWriter.java             R-OUT-02/06
│   ├── ClaimSummaryWriter.java        R-OUT-10（★第四張報表，多份輸出 + J23 零和自驗）
│   ├── SummarySheetPainter.java       兩張彙整/彙總報表共用之公式產生邏輯（★方案 A，見 §4.3）
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
| **calculator** | **M1~M12** 全部金額計算（含二階段之簽單年度分群、應產出年度與**簽單年度 × 公司二維分群**） | R-CALC-01~**24** | `CalculationResult calculate(...)` |
| **writer** | 套樣板、寫值與公式、套格式、改工作表名、備份、產 report.json；**二階段之多份輸出（兩張賠款報表各 N 份）** | R-OUT-01~**10**、R-PATH-04/06/07/**08** | `Path write(...)` / `List<Path> writeAll(...)` |
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
| **D15** | **保費檔之存在性由服務層判斷，`PremiumCsvReader` 之缺檔例外降為防衛性檢查** | 「缺檔之後續處置」是流程決策不是讀檔決策。讀取器一拋例外，服務層就沒有機會決定「前兩張不產、第三張照產」（P-12）。讀取器保留檢查以防其他呼叫端漏判 |
| **D16** | **保費檔缺檔時，備份清單與寫出清單由同一個 `premiumFileExists` 旗標控制** | 兩者若各自判斷，容易出現「備份了卻不重寫」——`BackupService` 是 `Files.move()`，會把前次成果**移走**，承辦人員會以為輸出遺失。旗標單一來源可讓這條不變式在編譯期就看得出來 |
| **D17** | **「全部報表落空」之中止點放在一致性檢查之後、備份之前** | 放在備份之後會留下「有備份、無產出」的空目錄；放在讀檔當下則還不知道 `M10` 是否為空，無法判斷兩張賠款報表能否產出。（第四張報表加入後仍為同一個判斷點——兩張賠款報表共用 `M10`，一起有或一起沒有） |
| **D18** | **`reportYears()` 收「第一階段報表是否產出」為參數，而非在服務層事後補上設定年** | 「排除設定年」與「補回設定年」是同一個決策的兩面，寫在兩個地方遲早會不同步。把條件收進 `ClaimCalculator` 後，M10 的定義單點成立，服務層只負責傳入 `premiumFileExists`（P-13） |
| **D19** | **以「賠款承載完整性」作為執行期不變式，而非只靠測試** | `Σ M9 == （前兩張產出 ? M3 : 0） + Σ M10` 是 P-13 規則的形式化。排除條件寫錯的症狀是**金額憑空消失**——沒有這條檢查，程式會安靜地少產一份報表，帳面上看不出來。放進 `verifyConsistency()` 讓它在正式執行時也會擋 |
| **D20** | **賠款彙總表另立 `ClaimSummaryCell`，但寫入邏輯**重用** `SummaryWriter`** | 與 D10 看似矛盾，實則相反的情況：`ClaimTAccountCell` 之所以必須獨立，是因為**同一格語意不同**（`G20` / `G21` 相反）；賠款彙總表的**每一格語意都與彙整表相同**，只有「餵什麼值」不同。常數獨立是為了檔名／工作表名／年度來源這四項不可共用；公式與版面則沒有任何差異，複製一份反而製造兩處要同步維護的公式字串 |
| **D21** | **`B` 欄餵 0，而非為本報表另寫一套「不含保費」的公式** | 第一階段之 `F` / `I` 與中央再保差額法三組公式皆以 `$B$23` 為輸入，`B` 欄全 0 時它們**自動全為 0**。另寫一套公式等於把「保費恆為 0」這個業務事實硬編進公式字串，日後若業務方改口要帶入保費，改一個資料來源即可 vs 改兩套公式 |
| **D22** | **`J23 == 0` 作為執行期不變式（`verifyConsistency()`）** | 本報表 `B` / `F` / `I` 三欄恆 0，帳面上很難看出算錯：`C` 欄年度取錯、`G` 欄分攤基準取錯、中央再保未用差額法——三種錯誤都會讓 `J23` 偏離 0，但單看報表都「有數字、格式正確」。這是本報表唯一的天然自檢，比照 D19 放進正式執行路徑 |
| **D23** | **兩張賠款報表共用同一份 `M10`，不各自計算** | 份數與年度必須一致（D11 之 BR-24）。若各自呼叫 `reportYears()`，一旦其中一處漏傳 `premiumFileExists`，就會出現「T 字帳 3 份、彙總表 2 份」這種只在特定輸入下才浮現的分歧 |


### 4.2 第二階段新增元件

| 元件 | 職責 | 對應規則 | 對外介面 |
| --- | --- | --- | --- |
| `constant/ClaimTAccountCell` | 賠款 T 字帳之樣板名、工作表名、檔名樣式與**獨立**儲存格常數 | R-PATH-07、R-OUT-05/08 | 常數 |
| `model/ClaimYearSummary` | 單一簽單年度之彙總（年度 + 賠款總額 + 衍生西元年） | — | record |
| `calculator/ClaimCalculator`（擴充） | `claimByUnderwritingYear()` 分群；`reportYears()` **條件式**排除設定年（僅在前兩張報表會產出時排除，D18 / P-13）並降冪 | R-CALC-17/18 | `Map<Integer,Long>` / `List<Integer>` |
| `writer/ClaimTAccountWriter` | 逐年度載入樣板、寫入 6 格、套會計格式、改工作表名、存檔 | R-OUT-08/09、R-CALC-19/20 | `List<Path> writeAll(Setting, CalculationResult, Path outputDir)` |

**`ClaimTAccountWriter.writeAll()` 之處理輪廓**：

```
writeAll(setting, calculation, outputDir):
    years = calculation.reportYears()            // 已降冪；設定年是否排除取決於前兩張是否產出（D18）
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

1. `verifyConsistency()` 追加三條不變式：`M9[configYear] == M3`、`Σ M9 == 理賠檔全部已決賠款合計`、**賠款承載完整性** `Σ M9 == （前兩張產出 ? M3 : 0） + Σ M10`（D19）。該方法需收 `premiumFileExists` 參數。
2. 備份階段（`BackupService`）之目標檔清單改為**動態**：`M10` 推導出的 N 個檔名，加上前兩張——但**前兩張僅在保費檔存在時才列入**（D16）。
3. 產出階段依序呼叫 `TAccountWriter` → `SummaryWriter` → `ClaimTAccountWriter.writeAll()`，將 `List<Path>` 併入 `ExecutionResult.outputFiles()`；**保費檔缺檔時前兩者整組略過**（P-12）。
4. `ExecutionReport` 追加 `claimByUnderwritingYear` 與 `reportYears`；後者為空時附**可區分原因**的訊息（理賠檔缺檔 vs 全部簽單年度皆為設定年）。另追加 `premiumFileMissing` 與 `premiumReportMessage`，使各張報表的產出結果在報告中各自可讀（P-12；第四張報表另加 `claimSummaryMessage`，見 §4.3）。
5. 服務層仍**不得** `System.exit()` 或直接印訊息（D9 不變）。
6. **保費檔缺檔且 `M10` 為空**時**四張**全部落空，於一致性檢查之後、備份之前拋 `FatalException`（D17）；訊息須區分「理賠匯入檔亦不存在」與「理賠匯入檔無任何資料列」。因 P-13 之後保費檔缺檔時設定年不被排除，此分支**僅在理賠檔完全無資料時成立**。
7. `ClaimCalculator.reportYears()` 之簽章改為 `(Map<Integer,Long>, int configYear, boolean premiumReportsProduced)`，由服務層傳入 `premiumFileExists`（D18）。

### 4.3 第四張報表（賠款彙總表）新增元件

| 元件 | 職責 | 對應規則 | 對外介面 |
| --- | --- | --- | --- |
| `constant/ClaimSummaryCell` | 賠款彙總表之樣板名、工作表名、檔名樣式與儲存格常數；**欄索引與明細列常數可直接沿用 `SummaryCell` 之值**，但**不得共用類別**（D20） | R-PATH-08、R-OUT-05/10 | 常數 |
| `calculator/ClaimCalculator`（擴充） | `claimByYearAndCompany()` —— 依「簽單年度 × 公司代號」二維分群，得 `M12` | R-CALC-21 | `Map<Integer, Map<String, Long>>` |
| `model/CalculationResult`（擴充） | 追加 `claimByYearAndCompany` 欄位與 `claimOf(year, code)` 查詢方法（比照既有之 `claimOf(code)`） | — | record |
| `writer/ClaimSummaryWriter` | 逐年度載入樣板、寫表頭與明細值、寫公式、改工作表名、**驗 `J23 == 0`**、存檔 | R-OUT-10、R-CALC-22/23/24 | `List<Path> writeAll(Setting, CalculationResult, Path outputDir)` |

**`ClaimSummaryWriter.writeAll()` 之處理輪廓**（與 `ClaimTAccountWriter.writeAll()` 同構）：

```
writeAll(setting, calculation, outputDir):
    years = calculation.reportYears()            // ★與 T 字帳共用同一份 M10（D23）
    if years.isEmpty():
        log.info("無非當年度簽單資料，未產出賠款彙總表")
        return List.of()                          // ★不拋例外（D13）
    template = templateDir / CLAIM_SUMMARY.xlsx
    if !exists(template): throw FatalException     // ★延後至此才檢查（D14）
    outputs = []
    for y in years:                                // 降冪
        byCompany = calculation.claimByYearAndCompany().get(y)   // M12[y]
        wb = WorkbookFactory.create(template)      // ★每份獨立載入（D11）
        sheet = wb.getSheet("工作表2")
        write A3 = "Ｕ/Y：" + (y + 1911)            // ★來源是 y，非設定年
        write E3 = "資料統計年月：{setting.year}年{MM}月"   // ★仍是設定年月
        names = readCompanyNames(sheet)            // 沿用 SummaryWriter 之查表（A 欄不覆寫）
        for each row:
            B = 0                                  // ★恆 0（D21）
            C = byCompany.getOrDefault(code, 0)
            E = company.share()
            D/F/G/H/I/J = 與 SummaryWriter 逐字相同之公式字串
        writeReinsurerFormulas(...)                // 差額法，SUM 範圍動態排除自身
        writeTotalRow(...)                         // =SUM({欄}7:{欄}22)
        verifyZeroSum(sheet)                       // ★J23 必為 0，否則 FatalException（D22）
        rename sheet -> "共保賠款月帳單-彙總表"
        setForceFormulaRecalculation(true)
        save to outputDir / OUTPUT_FILE_PATTERN.formatted(YYYMM, y)
        outputs.add(path)
    return outputs
```

**公式重用之作法**：`SummaryWriter` 之 `writeDetailRows()` / `writeReinsurerFormulas()` / `writeTotalRow()` / `rangesExcluding()` 目前為私有方法。實作時擇一：

| 方案 | 作法 | 取捨 |
| --- | --- | --- |
| **A（建議）** | 抽出 `writer/SummarySheetPainter`（package-private），由 `SummaryWriter` 與 `ClaimSummaryWriter` 共同呼叫；差異（B 欄來源、C 欄來源、表頭、命名常數）以參數傳入 | 公式字串**單點存在**，改一次兩張都對；符合 D20 之理由 |
| B | `ClaimSummaryWriter` 複製一份公式產生邏輯 | 短期改動小，但公式字串出現兩份，第一階段調率或改算式時極易漏改一邊 |

> **`rangesExcluding()` 已是 package-private 之 `static`**（`SummaryWriter.rangesExcluding`），本身即可直接重用，不需搬移。

**服務層編排之調整（`ReportGenerationService`）**：

1. `verifyConsistency()` 再追加：`Σ M12[y] == M9[y]`（全部年度）、`M12[configYear] == M4`。`J23 == 0` 因需公式重算，改由 `ClaimSummaryWriter` 於寫出前自驗（D22）。
2. 備份階段之目標檔清單追加**彙總表之 N 個檔名**；仍受同一個 `premiumFileExists` 旗標控制——**該旗標不涵蓋兩張賠款報表**（D16 之範圍不變）。
3. 產出階段順序：`TAccountWriter` → `SummaryWriter` → `ClaimTAccountWriter.writeAll()` → **`ClaimSummaryWriter.writeAll()`**；四組 `Path` 併入 `ExecutionResult.outputFiles()`。
4. `ExecutionReport` 追加 **`claimSummaryMessage`**（F13），與 `premiumReportMessage` / `claimTAccountMessage` 並列，使**四張報表**的產出結果各自可讀。
5. 「全部落空」之判定改為**四張**：`!premiumFileExists && reportYears.isEmpty()` —— 條件式本身不變（兩張賠款報表共用 `M10`，一起有或一起沒有），但訊息文字需更新。
6. 進入點（CLI / GUI）之完成訊息追加彙總表份數。

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
   ▼
[3] CsvReader 解析（Big5、跳表頭、表頭驗證、索引取欄）
   │  解碼失敗/表頭不符/欄數不符 ──────► FatalException
   │  理賠檔不存在 → claims = 空集合（不視為錯誤）
   │  保費檔不存在 → premiums = 空集合 + premiumFileMissing 旗標   ★P-12
   │                 （不在此中止；前兩張報表本次不產出）
   ▼
[4] Validator 逐列檢核（收集全部錯誤）
   │  errors.isEmpty() == false ──────► ValidationFailedException
   │                                     → 產 report.json，四張報表皆不產出
   ▼
[5] Calculator
   │  M1 共保保費 / M2 各公司保費
   │  M3 攤付共保賠款(篩簽單年度) / M4 各公司賠款
   │  M5 應分配保費(N19 差額法) / M6,M7 管理費 / M8 Balance Due
   │  M9 簽單年度分群 / M10 應產出年度(降冪)                  ★二階段
   │     └─ 排除設定年 ⇔ premiumFileExists（P-13）
   │        缺檔時不排除，該年賠款改由兩張賠款報表承載
   │  M12 簽單年度 × 公司分群                                ★第四張報表
   ▼
[6] 跨報表一致性檢查（R-CALC-16，含 M9[設定年]==M3、ΣM9==全部賠款、
   │                    賠款承載完整性 ΣM9==(前兩張產出?M3:0)+ΣM10、
   │                    ΣM12[y]==M9[y]、M12[設定年]==M4）
   │  不一致 ──────────────────────────► FatalException（程式缺陷）
   │  premiumFileMissing && M10 為空 ──► FatalException（四張全部落空）★P-12
   │                                     須在備份之前判斷（D17）
   ▼
[7] BackupService：備份既有輸出檔 + 清除逾 3 個月備份
   │  清單只列本次會重寫者；premiumFileMissing 時不列前兩張（D16）
   │  備份失敗 ────────────────────────► FatalException（不覆寫）
   ▼
[8] Writer：載入樣板 → 寫值/公式 → 套格式 → 改工作表名 → 存檔
   │  TAccountWriter  → 1 檔  ┐ premiumFileMissing 時
   │  SummaryWriter   → 1 檔  ┘ 整組略過（0 檔）      ★P-12
   │  ClaimTAccountWriter → N 檔（★二階段，逐年度迴圈；M10 為空則 0 檔）
   │  ClaimSummaryWriter  → N 檔（★第四張報表，共用同一份 M10）
   │        └─ 每份寫出前自驗 J23 == 0 ──► FatalException（D22）
   ▼
[9] ReportJsonWriter：覆寫 ./logs/report.json（產出清單含全部 2+N×2 檔）
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
           │      ├─► 〔T 字帳〕逐年度 y：G6 = G21 = O20 = O21 = M9[y]
           │      │                       O6 = G14 = 0；G20 留空
           │      │                       P4 = y + 1911     ★來源是 y，非設定年
           │      └─► 〔彙總表〕逐年度 y（★共用同一份 M10，D23）：
           │                              A3 = "Ｕ/Y：" + (y + 1911)   ★來源是 y
           │                              E3 = 設定年月                 ★不隨 y 變
           │                              C23 = M9[y]（= Σ M12[y]）
           │                              B/F/I 三欄恆 0
           │                              G 欄 = ROUND(C23 × 成分)，N19 差額法
           └─► M12 簽單年度 × 公司分群 ─► 〔彙總表〕C 欄 = M12[y][code]
                  └─► 不變式：Σ M12[y] == M9[y]、M12[configYear] == M4

  共通不變式：M9[configYear] == M3
              Σ M9 == 理賠檔全部已決賠款合計
              每份彙總表 J23 == 0（= −ΣC + ΣG，而 ΣG ≡ ΣC）
```

**❗禁止（一）**：賠款 T 字帳之 Balance Due **不得**經由 `M8 = M1 − M3 − M7` 求得。該報表之 Balance Due 直接等於 `M9[y]`；誤套 M8 會得 `0 − 38,331 − 0 = −38,331`（符號相反）。

**❗禁止（二）**：賠款彙總表之 `G` 欄（應攤配賠款）分攤基準是**該年度合計** `C23`（= `M9[y]`），**不得**改用各公司自己的 `M12[y][code]`。樣本理賠全部集中於 `N05`，誤用之症狀是**除富邦產險外 15 家全部歸零**，而 `J23` 仍可能碰巧為 0 —— 須由逐家金額斷言（AC-36 / AC-37）把關。

---

## 6. 資料模型

### 6.1 核心模型（Java record）

| 模型 | 主要欄位 |
| --- | --- |
| `Setting` | `int year, int month, List<CoInsuranceCompany> companies` <br>衍生：`String rocYearMonth()`, `int adYear()`, `Map<String,CoInsuranceCompany> byName()` |
| `CoInsuranceCompany` | `String name, String code, BigDecimal share` <br>`boolean isReinsurer()` → `"N19".equals(code)` |
| `PremiumRecord` | 19 欄；計算僅用 `companyCode`、`totalPremium`（long，可負） |
| `ClaimRecord` | 22 欄；計算僅用 `companyCode`、`underwritingYear`、`totalSettledClaim` |
| `CalculationResult` | `long totalPremium, totalClaim, totalManagementFee, balanceDue`<br>`Map<String,Long> premiumByCompany, claimByCompany, allocatedPremium, managementFee`<br>**★二階段追加**：`Map<Integer,Long> claimByUnderwritingYear`（M9）、`List<Integer> reportYears`（M10，降冪）<br>**★第四張報表追加**：`Map<Integer,Map<String,Long>> claimByYearAndCompany`（M12）<br>衍生：`long claimOf(int year, String code)` → 查無回 `0`（比照既有之 `claimOf(code)`） |
| `ValidationError` | `String fileName, int rowNumber, String fieldName, String actualValue, String ruleId, String message` |
| `ClaimYearSummary` | **★二階段**：`int underwritingYear, long totalClaim`<br>衍生：`int adYear()` → `underwritingYear + 1911`、`String fileNameSuffix()` → `"_%d年"` |
| `ExecutionRequest` | `int year, int month, boolean overriddenByArgs` |
| `ExecutionReport` | 見 MAPPING §9（F1 ~ **F13**；F13 `claimSummaryMessage` 為第四張報表新增） |

> **`ClaimYearSummary` 可否重用於彙總表**：可以。該 record 只保存「年度 + 該年賠款總額 + 衍生西元年」，兩張賠款報表都需要這三項；彙總表另需之逐公司明細來自 `M12`，屬 `CalculationResult` 層級，不應塞進本 record。

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

**★第四張報表 `ClaimSummaryCell`（獨立類別，不得與 `SummaryCell` 共用）**：

| 常數 | 值 | `SummaryCell` 同名之差異 |
| --- | --- | --- |
| `TEMPLATE_FILE_NAME` | `CLAIM_SUMMARY.xlsx` | ❗不同樣板 |
| `TEMPLATE_SHEET_NAME` | `工作表2` | 相同 |
| `OUTPUT_SHEET_NAME` | `共保賠款月帳單-彙總表` | ❗不同 |
| `OUTPUT_FILE_PATTERN` | `共保理賠_當月賠款月帳單_彙總表%s_%s年.xlsx` | ❗兩個佔位符（YYYMM、簽單年度） |
| `UY` | `A3` | 相同儲存格，**值之來源為簽單年度** |
| `PERIOD` | `E3` | 相同儲存格，**來源仍為設定檔年月** |
| `DETAIL_FIRST_ROW` | `7` | 相同 |
| `COL_*`（B ~ J 共 9 個欄索引） | 同 `SummaryCell` | 相同 |
| `MGMT_FEE_RATE` | `0.06` | 相同（雖然本報表 `I` 欄恆 0，公式字串仍需此值） |
| — | **不定義 `FORBIDDEN_CELL`** | ❗`SummaryCell.FORBIDDEN_CELL` = `F27`，係第一階段**範例檔**之殘值防護；`CLAIM_SUMMARY.xlsx` 實測無此殘值 |

> 類別 Javadoc 須明確標註：**四處語意差異**（`A3` 年度來源、`C` 欄年度來源、`B` 欄恆 0、工作表名與檔名），並註明依據為第二階段問題追蹤清單之 P-15 ~ P-21。
>
> **常數值大量相同不代表可以共用類別**（D20）：共用會讓「這張報表的 `A3` 該填哪一年」變成呼叫端的責任，而呼叫端寫錯不會編譯失敗。反之，**公式字串應該共用**（見 §4.3 之方案 A）——那才是一改就得兩邊同時對的東西。

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
| `FatalException` | 前置資源或環境問題（缺檔、格式錯誤、成分不符、備份失敗）。**保費檔缺檔不必然屬此類**——僅在**四張**報表全部落空時才升級為 `FatalException`（P-12）。**另含賠款彙總表之 `J23 ≠ 0`**（視為程式缺陷，D22） | CLI：印訊息、exit code 2；GUI：彈出錯誤對話框 |
| `ValidationFailedException` | 匯入檔資料檢核失敗（攜帶 `List<ValidationError>`） | CLI：印全部錯誤、exit code 1；GUI：於表格列出全部錯誤 |
| 其他 `RuntimeException` | 未預期錯誤 | 記錄堆疊、exit code 3 |

**共通**：任一例外皆**不產出任何報表**（**四張同進退**——含第二階段之賠款 T 字帳與賠款彙總表，P-11 / P-22），但**仍產出 `report.json`** 以保留錯誤明細。<br>**例外**：保費檔缺檔不屬此類（R-EXC-03 / P-12），該情境為部分產出而非全不產出。

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
| X4 | 新增報表 | writer 層新增 `XxxWriter`，服務層加入編排；reader/validator/calculator 可重用。**第二階段之賠款 T 字帳與賠款彙總表皆依此擴充點實作，reader 與 validator 完全重用、未改一行**（見 §4.2 / §4.3） |
| **X8** | **報表份數隨資料變動（一報表多檔）** | `ClaimTAccountWriter.writeAll()` 與 **`ClaimSummaryWriter.writeAll()`** 皆回傳 `List<Path>`；備份與 `report.json` 之產出清單皆改為動態組成。**兩者共用同一份 `M10`**（D23），日後若彙整表也需分年，可沿用同一模式 |
| **X9** | **報表版面相同但資料來源不同** | 公式產生邏輯抽為共用元件（§4.3 方案 A 之 `SummarySheetPainter`），資料來源與命名常數以參數注入。**賠款彙總表即為此模式之首例** —— 與 X4「整張新報表」的差別在於：版面完全相同時，複製公式字串才是主要風險，而非重寫寫入器 |
| X5 | 匯入檔格式改版（欄位增減） | 欄位索引集中於 enum 與表頭常數，改動範圍受限 |
| X6 | 匯入檔改為 UTF-8 | 編碼抽為常數，可改設定；現階段固定 Big5 |
| X7 | 保留執行歷史 | 目前不實作（NF-07）；日後可將 `report.json` 改為帶時間戳並保留 N 份 |

---

## 11. 測試策略

| 層級 | 範圍 | 重點 |
| --- | --- | --- |
| 單元測試 | calculator、validator、util | 金額計算之精確值、捨入方向、差額法、日期與長度檢核、遮蔽格式 |
| 整合測試 | service 全流程 | 以 `檔案位子範例/` 為基準輸入，比對產出檔之儲存格值與公式 |
| 反向測試 | 例外路徑 | 缺檔、成分 ≠ 100%、表頭錯置、出生日期 6 碼、年月不符、名稱查無對應；**二階段：全部簽單年度皆為設定年（產 0 份仍成功）、以 M8 算 Balance Due 得負值**；**P-12：保費檔缺檔（只產兩張賠款報表）、雙檔皆缺（中止）、保費檔缺檔且無可產出年度（中止）、保費檔僅有表頭（四張照產、Balance Due 為負）**；**P-13：保費檔缺檔時設定年不排除（產 3 年度 × 2 張）、保費檔存在時設定年須排除之對照組**；**第四張報表：`G22` 以 7% 直算（得 2,272 / 2,683，須失敗）、`G` 欄誤用逐家 `M12` 而非年度合計（15 家歸零但 `J23` 仍為 0，須由逐家金額斷言擋下）、`C` 欄誤取設定年（三份內容雷同）、`A3` 誤取設定年（與 T 字帳 `P4` 打架）** |
| 手動驗收 | GUI 與部署 | fat jar 雙擊、畫面錯誤清單、備份行為 |

**測試資料基準**：`docs/規格來源/第一階段-共保月帳單/檔案位子範例/`（R-04 已同步，可直接使用；**`templet/` 下四份樣板齊全**，`CLAIM_SUMMARY.xlsx` 已於 2026-08-18 補入）。
**驗收數值基準**：本文件系列所載之計算結果（350,123 / 126,931 / **21,009** / **202,183** / **−24,512** / **+8,882**）；**第二階段為 113 年 32,460、114 年 38,331**，且 `32,460 + 38,331 + 126,931 = 197,722`；**第四張報表另加逐家分攤與 `J23` = 0**（中央再保 113 → 2,267、114 → 2,680，逐家金額見 MAPPING §11.4）。
**禁止事項**：**不得以任何 `產出範例/` 之金額作為比對基準**——四階段之範例雖已於 2026-08-18 全數以實跑輸出取代（R-06 / T-22 / T-29 / T-38），但範例是程式的**產物**而非事實來源；以它反推，程式改壞時範例會跟著壞而測不出來。基準一律取自本文件系列所載之數值。

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
| **D-07** | **兩張賠款報表**之年度組合逐月可能不同，前次多出之年度檔會留在 `./output/{YYYMM}/`（兩張都會）。是否需在重跑時清空該月目錄，**待業務方確認**（同 REQ §11） | 低 |
| **D-08** | ~~第四張報表「賠款彙總表」尚未設計~~ **✔ 已解除（2026-08-18）**：P-14 ~ P-23 全數結案，設計見 **§4.3**（`ClaimSummaryCell` / `ClaimSummaryWriter` / `M12`）與 D20 ~ D23 | ~~高~~ **已結案** |
| **D-09** | 【設計選擇】§4.3 之公式重用採**方案 A（抽出 `SummarySheetPainter`）**或方案 B（複製一份）。建議 A，但實作時若發現兩者差異比預期大（例如表頭寫法無法乾淨參數化），可改採 B 並於本表更新理由 | 中 |
| **D-10** | 【推論】`J23 == 0` 之驗證需 POI 公式重算（`FormulaEvaluator`）。若重算成本或相容性有問題，替代方案是**在寫入前以 Java 端算式自驗**（`−ΣC + ΣG == 0`），效果相同但少一層 Excel 語意保證 | 中 |

---

## 13. 版本紀錄

| 版本 | 日期 | 內容 |
| --- | --- | --- |
| **v1.8** | **2026-08-18** | 配合**規格來源目錄歸納**與**產出範例取代**同步：① 全文之規格來源路徑引用改指向歸納後之新位置（第一階段新增 `規格書/`、`匯入檔範例/`；第二階段新增 `規格書/`、`範本/`、`業務回覆/`）；② 「產出範例不可作驗收基準」之敘述改寫——**範例已於 2026-08-18 以實跑輸出取代**（R-06 / T-22 / T-29 / T-38），金額現已正確，**但仍不得以範例反推基準**（範例是產物、不是事實來源）。 §11 測試策略之禁止事項改寫為「範例是產物而非事實來源」之理由說明 |
| **v1.7** | **2026-08-18** | **M10（設計部分）完成——納入第二階段·第四張報表（賠款彙總表）之設計**（P-14 ~ P-23 已結案，**D-08 解除**）。新增 **§4.3 第四張報表新增元件**：`ClaimSummaryCell` / `ClaimCalculator.claimByYearAndCompany()`（M12）/ `CalculationResult` 擴充 / `ClaimSummaryWriter`，含 `writeAll()` 處理輪廓、**公式重用之兩方案取捨（建議方案 A：抽出 `SummarySheetPainter`）**、服務層 6 點調整。新增關鍵設計決策 **D20 ~ D23**：常數獨立但寫入邏輯重用（與 D10 之對比理由）、**`B` 欄餵 0 而非另寫公式**、**`J23 == 0` 作為執行期不變式**、兩張賠款報表共用同一份 `M10`。§3.2 套件結構新增 `ClaimSummaryCell` / `ClaimSummaryWriter` / `SummarySheetPainter`；§4 模組職責擴充至 R-CALC-01~24、R-OUT-01~10；§5.1 主流程圖新增 M12、彙總表寫出與 `J23` 自驗；**§5.2 相依圖擴充並新增「禁止（二）」**——`G` 欄分攤基準是年度合計而非逐家 `M12`，**誤用時 15 家歸零但 `J23` 仍為 0**，須由逐家金額斷言把關；§6.1 / §6.3 新增 `M12` 與 `ClaimSummaryCell` 常數對照；§8.1 之「三張」改為「四張」並補列 `J23 ≠ 0`；§10 新增擴充點 **X9**（版面相同但資料來源不同）；§11 測試策略補列四項第四張報表之反向測試；§12 **D-08 結案**，新增 **D-09**（公式重用方案）與 **D-10**（`J23` 驗證手法） |
| **v1.6** | **2026-08-17** | **登錄第二階段·第四張報表（賠款彙總表）之待確認狀態，未新增任何設計**：§12 新增 **D-08**（等級**高**、阻斷實作），載明屆時預期新增 `ClaimSummaryCell`（**不得共用 `SummaryCell`**）與 `ClaimSummaryWriter`，並可沿用 `M10`、成分查表、中央再保差額法與擴充點 X8。詳見 `..._LOG_第二階段問題追蹤清單.md` §4（P-14 ~ P-23） |
| **v1.5** | **2026-08-14** | **全文一致性校正（無設計變更）**：① §5.1 流程圖與 §8.1 之「檢核失敗 → **兩表**皆不產出」更正為「**三張**報表同進退」（P-11 定案賠款 T 字帳共用同一組檢核），並註明保費檔缺檔不屬此類；② §4.2 元件表與 `writeAll()` 虛擬碼註解之「`reportYears()` **已排除設定年**」改為**條件式**敘述（D18 / P-13）；③ §11 反向測試補列 P-13 之兩項；④ 文件資訊之目前版本／最後更新補正（原停留 v1.2 / 2026-08-10） |
| **v1.4** | **2026-08-13** | **P-13：`M10` 之「排除設定年」改為條件式**。新增設計決策 **D18**（條件收進 `ClaimCalculator.reportYears()`，不在服務層事後補設定年）與 **D19**（以「賠款承載完整性」作為執行期不變式，因排除條件寫錯的症狀是金額憑空消失、帳面看不出來）；§4.2 服務層編排第 1 / 6 點改寫並新增第 7 點；§5.1 主流程圖之 [5] / [6] 更新 |
| **v1.3** | **2026-08-13** | **P-12：保費檔缺檔不再阻擋賠款 T 字帳**。新增關鍵設計決策 **D15 ~ D17**（存在性判斷上移至服務層、備份與寫出由同一旗標控制、中止點置於一致性檢查之後備份之前）；§4.2「服務層編排之調整」第 2 / 3 / 4 點改寫並新增第 6 點；§5.1 主流程圖之 [2] / [3] / [6] / [7] / [8] 更新；§8.2 `FatalException` 適用範圍加註；§11 反向測試補列四種組合 |
| **v1.2** | **2026-08-10** | **納入第二階段（賠款 T 字帳）設計**：§3.2 套件結構新增 `ClaimTAccountCell` / `ClaimYearSummary` / `ClaimTAccountWriter`；§4 模組職責擴充至 R-CALC-01~20、R-OUT-01~09；新增 **§4.2 第二階段新增元件**（元件表、`writeAll()` 處理輪廓、服務層編排 5 點調整）；新增關鍵設計決策 **D10 ~ D14**（另立常數類別、逐份獨立載入樣板、寫值不寫公式、空清單不拋例外、樣板檢查延後）；§5.1 主流程與 §5.2 相依順序補上二階段分支與「禁止套用 M8」警示；§6.1 `CalculationResult` 追加 M9 / M10、新增 `ClaimYearSummary`；§6.3 新增 `ClaimTAccountCell` 常數對照表並標出與 `TAccountCell` 之差異；§10 新增擴充點 X8；§11 測試策略與驗收基準補列二階段；§12 新增 D-06 / D-07 |
| v1.1 | 2026-08-10 | 依 2026-08-10 業務調整同步：管理費率 5% → **6%**（`CoInsuranceConstants.MANAGEMENT_FEE_RATE` / `SummaryCell.MGMT_FEE_RATE` = `0.06`）；`run.bat` 更名為 `拜託執行我.bat` 並併入 `mvnw.cmd clean package -DskipTests`；驗收數值基準更新為 21,009 / 202,183 / −24,512 / +8,882 |
| v1.0 | 2026-08-03 | 初版；定義分層架構、套件結構、7 大模組、9 項關鍵設計決策、主流程與計算相依順序、資料模型、設定檔、錯誤處理與 exit code、日誌與個資遮蔽、7 項擴充點、測試策略 |
