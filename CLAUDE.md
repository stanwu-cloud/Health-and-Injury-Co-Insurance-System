# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 專案狀態

規格先行的專案：需求分析、REQ / DESIGN / TASK / TEST 皆已完成，**實作已完成並通過驗收**（TASK 之 T-01 ~ T-21；T-22 取代產出範例檔待業務方確認）。

交付物：一支讀取共保系統匯出的 CSV、套用 Excel 樣板、產出兩張月帳單報表（T 字帳、彙整表）的程式，位於 `src/main/java/com/insurance/coinsurance/`。

- **GUI 已移出（2026-08-22，TASK T-40）**：`entry/FxLauncher`、`entry/FxApplication`、`entry/MainController` 與 `pom.xml` 之三個 JavaFX 依賴都在 **`feature/gui`**，本分支沒有。fat jar 主類別是 **`entry/CliLauncher`**，`java -jar` 直接進批次；`--mode=cli` **刻意保留相容**（業務方的 `.bat` 與排程都還帶著它），由 `CliRunner.parse` 忽略。報表邏輯兩分支同源——**`feature/gui` 只接收本分支的合併，不要在那邊改 `calculator/` 或 `writer/`**。文件不分家：八份常駐文件在文件資訊後各加一段「分支歸屬」提示，GUI 專屬條目（R-RUN-05、NF-06/07、T-18、TC-M-02/03、D8、K8、D-04/D-05）**保留不刪但標為本分支不適用**——刪掉的話日後回查業務回覆（B15 / B16 / N-09 / A-09）會對不上。

## 常用指令

```
build.bat                          建置 fat jar（內含指定 JDK 17 之 JAVA_HOME）
拜託執行我.bat [--year=115 --month=5]  編譯 + CLI 批次執行（原 run.bat，已併入 package）
mvnw.cmd -o test                   執行 38 項測試（-o 離線，相依已在本機倉庫）
mvnw.cmd clean package -DskipTests
```

`JAVA_HOME` 於本機為 `C:\Program Files\Amazon Corretto\jdk17.0.18_9`（PATH 上的預設 java 是 JDK 25）。
`拜託執行我.bat` 內另有一組寫死的 `JAVA_EXE`（`C:\Users\user\.jdks\corretto-17.0.18\bin\java.exe`），是**業務方機器**的路徑，在本機不存在——要跑批次請直接用 `mvnw.cmd` 或帶 `JAVA_HOME` 執行 jar。
執行環境目錄 `config/` `templet/` `input/` `output/` `backup/` `logs/` 位於專案根目錄；`input/`、`output/`、`backup/`、`logs/` 未納入版控。

## 程式架構

分層：`entry`（CLI；GUI 在 `feature/gui`）→ `service`（`ReportGenerationService`，唯一對外入口）→ `config` / `reader` / `validator` / `calculator` / `writer`，共用 `model` / `constant` / `util` / `exception`。

改動計算或輸出邏輯前，務必先讀下方「領域地雷」——每一條都有對應的測試在把關（`StaticGuardTest` 甚至會掃描原始碼）。

- **`CliLauncher` 是 fat jar 的主類別**（原為 `FxLauncher`，GUI 移出後改此）。`feature/gui` 那邊的 `FxLauncher` **刻意不繼承 `javafx.application.Application`**，直接繼承會讓 JavaFX 以「runtime components are missing」啟動失敗——改那個分支時別忘了。
- **`CliRunner` 刻意不是 `CommandLineRunner`**：原因是 CLI 與 GUI 曾共用同一個 Spring 容器，自動執行會讓 GUI 一開就跑批次。**GUI 移出後仍不要改回去**——批次的觸發時機屬於進入點，容器建立本身不該有副作用，測試也才能單獨組裝容器而不觸發整批產出。
- **`ReportGenerationService` 不得 `System.exit()` 或直接印訊息**，只回傳 `ExecutionResult`；中止行為由進入點決定。
- **捨入只能經 `calculator/RoundingUtil`**（固定 HALF_UP）。

## 文件體系

`docs/` 下四份 **常駐文件**，每個主題只有一份檔案，改版時**直接更新該檔**並在文件內的「版本紀錄」加一列。**不要建立同名不同版的檔案**（如 `_v2.0_20260803.md`）——這是使用者明確要求過的。

| 檔案 | 內容 |
| --- | --- |
| `..._LOG_現況分析與輸入輸出盤點.md` | 實測事實基線、樣板/設定檔逐格內容、驗算結果、34 項已定案業務決策（B01–B34） |
| `..._MAPPING_欄位對照.md` | CSV 欄位索引、中間模型、兩張報表的儲存格對照、report.json 規格 |
| `..._RULE_規則定義.md` | 可實作規則（`R-PATH-` / `R-VAL-` / `R-CALC-` / `R-OUT-` / `R-EXC-` / `R-RUN-`）與執行順序 |
| `..._LOG_問題追蹤清單.md` | Q-01~Q-26、N-01~N-11 結案狀態，以及 R-01~R-09 殘留事項 |

其他來源：`當月共保月帳單-*規格書v1.2.md`（規格書，`.docx` 為原稿）、`簡易回覆爭議v0.2.txt` / `v0.3.txt`（業務方逐題回覆）、`規格書/檔案位子範例/`（設定檔、樣板、輸入輸出目錄結構範例）。

**衝突時的優先順序**：業務回覆（v0.3 > v0.2）> 實體檔案實測 > 規格書文字 > 產出範例檔。規格書與範例檔都還殘留舊值，見下方「地雷」。

`.docx` 規格書與 `.md` 目前不同步（`.md` 已更新成分表，`.docx` 未更新）——見問題追蹤清單 R-05。

## 領域地雷（實際踩過，改動計算邏輯前務必確認）

- **CSV 是 Big5 / CP950，含表頭列**。保費檔 19 欄、理賠檔 22 欄。兩檔都有**兩個欄位叫「日額」**，理賠檔的「已決賠款 合計」中間有半形空白 → **必須依位置索引讀取，不可依欄名**。
- **理賠檔第 6 欄「簽單年度」是核心篩選欄位**，v1.0 規格書漏列過。「攤付共保賠款」＝簽單年度 == 設定年 的已決賠款合計，**只篩年、不篩月**。不篩選會得到 197,722（錯），正確是 126,931。
- **中央再保（代號 `N19`）採差額法**：分攤金額 = 總額 − 其餘公司加總，**不是** 總額 × 成分。判定要**依公司代號**，不可依「最後一列」。
- **設定檔共保成分合計必須等於 100%，程式要檢核，不符即中止**。曾經出現過 105%（「全球人壽 L64」誤植為 10%），那是資料錯誤、不是刻意設計。中央再保的差額法只用來吸收其餘公司**四捨五入的尾差**（本月 3 元），不是用來吸收百分比缺口。
- **現行成分是壽險 8 家各 7.5%、中央再保 7%、產險 7 家 33%**（2026-08-10 由 55%/12%/33% 調整）。`docs/規格書/檔案位子範例/config/application.xlsx`（測試基準）已同步為此版。
- **共保管理費率是 6%**（2026-08-10 由 5% 調整）。**四捨五入順序不可調換**：各公司分攤保費先四捨五入 → 再 ×6% → 再四捨五入 → 最後加總。先加總再乘會得到不同結果。全案一律 **HALF_UP**，Java 不可用 `BigDecimal` 預設的 HALF_EVEN。
- **彙整表列順序以樣板 `PREMIUM_SUMMARY.xlsx` 的 A7:A22 為準**（A 欄不覆寫），程式用**公司名稱**去設定檔查代號與成分。樣板 16 家與設定檔 16 家名稱目前 100% 對應。
- **保費合計含負值列**（批單沖銷，樣本 100 列），加總時不可過濾。也**不去重、不檢核重複**保單號／賠案號。
- **`docs/規格書/產出範例/` 的成分現已與設定檔相同（7.5% / 7%），但管理費率仍是 5%，且 D/G 欄公式為舊版**，**其金額不可作驗收基準**——G14（管理費）、G20（Balance Due）、I22 都不同。這兩份範例應以實跑輸出取代（TASK T-22，待業務方確認）。
- 樣板 `PREMIUM_SUMMARY.xlsx` 的 **E7:E22 是空的**（只有格式），成分由程式在執行時依設定檔填入 —— 別把樣板和產出範例搞混。樣板 A6:J6 是**計算說明列**（`(4) = (2) - (3)`、`(9)=(6)x6%`），程式不覆寫，但改公式時要記得一起改樣板文字。
- **彙整表 F 欄（應分配保費）是負值、G 欄（應攤配賠款）是正值**，D 欄是 `=B{n}-C{n}`。G 欄的正負與 D 欄的加減都在 2026-08-10 翻過一次，`ReportGenerationServiceTest` 有斷言把關。

### 驗收基準數字（115 年 5 月樣本）

以 `docs/規格書/VOLP11505.csv` + `VOLC11505.csv` + `檔案位子範例/config/application.xlsx`（16 家、合計 100%）計算：

| 項目 | 值 | 產出範例檔（**5% 版，勿用**） |
| --- | --- | --- |
| 共保保費 | 350,123 | 350,123 |
| 攤付共保賠款（簽單年度 115） | 126,931 | 126,931 |
| 共保管理費（費率 6%） | **21,009** | 17,505 |
| Balance Due | **202,183** | 205,687 |
| 中央再保應分配保費 | **−24,512** | −24,512（相同） |
| 中央再保應攤配賠款 | **+8,882** | −8,882（符號相反） |
| 中央再保應繳管理會費 I22 | **1,471** | 1,226 |
| 富邦產險應繳管理會費 I15 | **1,260** | 1,050 |

T 字帳寫入位置（規格書 v1.0 曾整體偏一列，已修正）：`I3` 單格 `115 年 05 月`、`P4` 只寫 `2026`（`O4` 的 `U/Y:` 是樣板既有、不覆寫）、`G6` / `O6` / `G14` / `G20` / `G21` / `O21`。

## 本機工具鏈

- **`python` / `python3` 在 PATH 上是 Windows Store 的壞存根（exit 49）**。要讀 xlsx 或 Big5 CSV 請用絕對路徑：`/c/Users/astra/anaconda3/python.exe`（已含 openpyxl 3.1.5、pandas 2.2.3）。
- 讀 xlsx 需分別以 `data_only=False`（取公式）和 `True`（取快取值）各載入一次，否則看不到公式。
- **Git Bash 沒有 `iconv`**。Big5 解碼用 Python 的 `encoding='cp950'`，或 PowerShell 的 `[System.Text.Encoding]::GetEncoding(950)`。
- JDK 25 與 JDK 17 已安裝；**`mvn` 不在 PATH**，用專案內的 `mvnw.cmd`。

## 技術堆疊

Java 17 + Spring Boot 3.5.0 + Apache POI 5.3.0（**JavaFX 21.0.5 僅 `feature/gui`**），打包成 fat jar；**本分支只有 CLI 批次進入點**，兩分支共用同一核心服務層。

分層架構沿用 `D:\project\retained-premium-report-transformer`（`config` / `model` / `reader` / `writer` / `service` / `constant` / `exception`）與 `build.bat` / `run.bat` 慣例（本專案之 `run.bat` 已更名為 `拜託執行我.bat` 並併入編譯）；`logs/report.json` 執行報告作法比照 `D:\project\excel-report-integration-engine`。

## 溝通與文件慣例

- 回覆與文件一律使用**繁體中文**。
- 文件命名：`Health-and-Injury-Co-Insurance-System_[TYPE]_[主題].md`，TYPE 為 `REQ` / `DESIGN` / `MAPPING` / `RULE` / `TEST` / `TASK` / `GUIDE` / `LOG` / `SPEC`。
- 陳述須標示證據等級：【文件明載】/【原始碼可證】/【業務回覆】/【合理推論】/【待確認】。**不得把推論當成事實**；資訊不足時先產出待確認問題清單，不要逕行產出設計與程式碼。
- 每份文件需含：文件資訊、背景與目的、已確認事項、推論事項、待確認事項、可測試的驗收標準、版本紀錄。
