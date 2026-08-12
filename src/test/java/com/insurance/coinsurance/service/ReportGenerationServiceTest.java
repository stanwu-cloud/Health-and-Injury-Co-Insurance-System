package com.insurance.coinsurance.service;

import com.insurance.coinsurance.calculator.AllocationCalculator;
import com.insurance.coinsurance.calculator.ClaimCalculator;
import com.insurance.coinsurance.calculator.ManagementFeeCalculator;
import com.insurance.coinsurance.calculator.PremiumCalculator;
import com.insurance.coinsurance.config.AppConfig;
import com.insurance.coinsurance.config.SettingReader;
import com.insurance.coinsurance.constant.ClaimColumn;
import com.insurance.coinsurance.constant.CoInsuranceConstants;
import com.insurance.coinsurance.model.ExecutionRequest;
import com.insurance.coinsurance.model.ExecutionResult;
import com.insurance.coinsurance.reader.ClaimCsvReader;
import com.insurance.coinsurance.reader.PremiumCsvReader;
import com.insurance.coinsurance.support.TestFixtures;
import com.insurance.coinsurance.validator.ClaimValidator;
import com.insurance.coinsurance.validator.PeriodValidator;
import com.insurance.coinsurance.validator.PremiumValidator;
import com.insurance.coinsurance.writer.BackupService;
import com.insurance.coinsurance.writer.ClaimTAccountWriter;
import com.insurance.coinsurance.writer.ReportJsonWriter;
import com.insurance.coinsurance.writer.SummaryWriter;
import com.insurance.coinsurance.writer.TAccountWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TC-E2E 系列：以 `檔案位子範例/` 為輸入之端到端驗證。 */
class ReportGenerationServiceTest {

    private static final String T_ACCOUNT = "共保保費_當月共保月帳單_T字帳報表11505.xlsx";
    private static final String SUMMARY = "共保保費_當月共保月帳單_彙整表11505.xlsx";
    private static final String CLAIM_T_ACCOUNT_113 = "共保理賠_當月賠款月帳單_T字帳報表11505_113年.xlsx";
    private static final String CLAIM_T_ACCOUNT_114 = "共保理賠_當月賠款月帳單_T字帳報表11505_114年.xlsx";

    private static ReportGenerationService serviceFor(AppConfig config) {
        return new ReportGenerationService(config, new SettingReader(config),
                new PremiumCsvReader(), new ClaimCsvReader(),
                new PremiumValidator(), new ClaimValidator(config), new PeriodValidator(),
                new PremiumCalculator(), new ClaimCalculator(),
                new AllocationCalculator(), new ManagementFeeCalculator(),
                new BackupService(config), new TAccountWriter(config), new SummaryWriter(config),
                new ClaimTAccountWriter(config), new ReportJsonWriter(config));
    }

    @Test
    @DisplayName("端到端產出兩張報表，儲存格與公式結果符合文件基準值")
    void endToEnd(@TempDir Path sandbox) throws IOException {
        AppConfig config = TestFixtures.sandboxConfig(sandbox);
        ExecutionResult result = serviceFor(config).execute(ExecutionRequest.useSettingFile());

        assertTrue(result.isSuccess(), result.message());
        assertEquals(0, result.exitCode());
        assertEquals(4, result.outputFiles().size(), "兩張共保報表 + 兩份賠款 T 字帳");

        assertEquals(TestFixtures.TOTAL_PREMIUM, result.calculation().totalPremium());
        assertEquals(TestFixtures.TOTAL_CLAIM, result.calculation().totalClaim());
        assertEquals(TestFixtures.TOTAL_MANAGEMENT_FEE, result.calculation().totalManagementFee());
        assertEquals(TestFixtures.BALANCE_DUE, result.calculation().balanceDue());

        Path outputDir = sandbox.resolve("output").resolve(TestFixtures.YEAR_MONTH);

        // ── T 字帳 ───────────────────────────────────────────
        try (Workbook workbook = open(outputDir.resolve(T_ACCOUNT))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("共保月帳單-T字帳", sheet.getSheetName());

            assertEquals("115 年 05 月", stringAt(sheet, "I3"), "資料統計年月須為單格");
            assertEquals("U/Y:", stringAt(sheet, "O4"), "O4 為樣板既有，不覆寫");
            assertEquals("2026", stringAt(sheet, "P4"), "P4 只寫西元年");

            assertEquals(TestFixtures.TOTAL_CLAIM, numericAt(sheet, "G6"));
            assertEquals(TestFixtures.TOTAL_PREMIUM, numericAt(sheet, "O6"));
            assertEquals(TestFixtures.TOTAL_MANAGEMENT_FEE, numericAt(sheet, "G14"));
            assertEquals(TestFixtures.BALANCE_DUE, numericAt(sheet, "G20"));
            assertEquals(TestFixtures.TOTAL_PREMIUM, numericAt(sheet, "G21"));
            assertEquals(TestFixtures.TOTAL_PREMIUM, numericAt(sheet, "O21"));

            for (String reference : List.of("G6", "O6", "G14", "G20", "G21", "O21")) {
                assertEquals(CoInsuranceConstants.ACCOUNTING_FORMAT,
                        cell(sheet, reference).getCellStyle().getDataFormatString(),
                        reference + " 須套用會計格式");
            }
        }

        // ── 彙整表（公式先求值再比對） ───────────────────────
        try (XSSFWorkbook workbook = openXssf(outputDir.resolve(SUMMARY))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("共保月帳單-彙整表", sheet.getSheetName());
            assertEquals("Ｕ/Y：2026", stringAt(sheet, "A3"));
            assertEquals("資料統計年月：115年05月", stringAt(sheet, "E3"));

            // A 欄不覆寫、F27 不寫入
            assertEquals("台灣人壽", stringAt(sheet, "A7"));
            assertEquals("中央再保", stringAt(sheet, "A22"));
            assertEquals("合計", stringAt(sheet, "A23"));
            assertNull(sheet.getRow(26) == null ? null
                    : sheet.getRow(26).getCell(CellReference.convertColStringToIndex("F")),
                    "F27 不得寫入");

            // 差額法公式須排除自身列
            assertEquals("-1*$B$23-SUM(F7:F21)", cell(sheet, "F22").getCellFormula());
            assertEquals("ROUND($C$23-SUM(G7:G21),0)", cell(sheet, "G22").getCellFormula());

            XSSFFormulaEvaluator.evaluateAllFormulaCells(workbook);

            assertEquals(TestFixtures.TOTAL_PREMIUM, numericAt(sheet, "B15"));
            assertEquals(TestFixtures.TOTAL_CLAIM, numericAt(sheet, "C15"));
            assertEquals(TestFixtures.TOTAL_PREMIUM, numericAt(sheet, "B23"));
            assertEquals(TestFixtures.TOTAL_CLAIM, numericAt(sheet, "C23"));
            assertEquals(100L, Math.round(cell(sheet, "E23").getNumericCellValue() * 100), "E23 須為 100%");

            // F 欄（應分配保費）為負值、G 欄（應攤配賠款）為正值
            assertEquals(-TestFixtures.REINSURER_ALLOCATED_PREMIUM, numericAt(sheet, "F22"));
            assertEquals(TestFixtures.REINSURER_ALLOCATED_CLAIM, numericAt(sheet, "G22"));
            assertEquals(1_471L, numericAt(sheet, "I22"));
            assertEquals(1_260L, numericAt(sheet, "I15"));

            assertEquals(-TestFixtures.TOTAL_PREMIUM, numericAt(sheet, "F23"));
            assertEquals(TestFixtures.TOTAL_CLAIM, numericAt(sheet, "G23"));
            assertEquals(TestFixtures.TOTAL_MANAGEMENT_FEE, numericAt(sheet, "I23"));
            assertEquals(TestFixtures.TOTAL_MANAGEMENT_FEE, numericAt(sheet, "J23"));
        }

        // ── 跨報表一致性（R-CALC-16） ───────────────────────
        try (Workbook tAccount = open(outputDir.resolve(T_ACCOUNT));
             XSSFWorkbook summary = openXssf(outputDir.resolve(SUMMARY))) {
            XSSFFormulaEvaluator.evaluateAllFormulaCells(summary);
            Sheet t = tAccount.getSheetAt(0);
            Sheet s = summary.getSheetAt(0);
            assertEquals(numericAt(t, "G6"), numericAt(s, "C23"));
            assertEquals(numericAt(t, "O6"), numericAt(s, "B23"));
            assertEquals(numericAt(t, "G14"), numericAt(s, "I23"));
        }

        assertTrue(Files.exists(sandbox.resolve("logs").resolve("report.json")));
    }

    @Test
    @DisplayName("TC-E2E-09 / TC-E2E-10 賠款 T 字帳：兩份逐格內容、G20 留空、格式與工作表名")
    void claimTAccountsEndToEnd(@TempDir Path sandbox) throws IOException {
        AppConfig config = TestFixtures.sandboxConfig(sandbox);
        ExecutionResult result = serviceFor(config).execute(ExecutionRequest.useSettingFile());

        assertTrue(result.isSuccess(), result.message());
        assertEquals(0, result.exitCode());
        assertEquals(List.of(114, 113), result.calculation().reportYears());

        Path outputDir = sandbox.resolve("output").resolve(TestFixtures.YEAR_MONTH);
        try (var files = Files.list(outputDir)) {
            assertEquals(4, files.count(), "輸出目錄須有 4 檔");
        }

        assertClaimTAccount(outputDir.resolve(CLAIM_T_ACCOUNT_113), "2024", TestFixtures.CLAIM_YEAR_113);
        assertClaimTAccount(outputDir.resolve(CLAIM_T_ACCOUNT_114), "2025", TestFixtures.CLAIM_YEAR_114);

        // 兩份之 P4 與金額須互不相同——重用 Workbook 會殘留前一年度值（TASK K13）
        try (Workbook w113 = open(outputDir.resolve(CLAIM_T_ACCOUNT_113));
             Workbook w114 = open(outputDir.resolve(CLAIM_T_ACCOUNT_114))) {
            assertNotEquals(stringAt(w113.getSheetAt(0), "P4"), stringAt(w114.getSheetAt(0), "P4"));
            assertNotEquals(numericAt(w113.getSheetAt(0), "G6"), numericAt(w114.getSheetAt(0), "G6"));
        }
    }

    /** 逐格斷言一份賠款 T 字帳（第二階段問題追蹤清單 §5.2）。 */
    private static void assertClaimTAccount(Path path, String expectedAdYear, long expectedClaim)
            throws IOException {
        try (Workbook workbook = open(path)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("共保賠款月帳單-T字帳", sheet.getSheetName(), "工作表名不帶年度");

            assertEquals("115 年 05 月", stringAt(sheet, "I3"), "資料統計年月須為單格");
            assertTrue(isBlank(sheet, "J3"), "J3 不寫入");
            assertEquals("U/Y:", stringAt(sheet, "O4"), "O4 為樣板既有，不覆寫");
            assertEquals(expectedAdYear, stringAt(sheet, "P4"), "P4 之來源為簽單年度");

            assertEquals(expectedClaim, numericAt(sheet, "G6"));
            assertEquals(expectedClaim, numericAt(sheet, "G21"), "Balance Due 寫 G21，且等於該年度賠款");
            assertEquals(expectedClaim, numericAt(sheet, "O20"));
            assertEquals(expectedClaim, numericAt(sheet, "O21"));
            assertEquals(0L, numericAt(sheet, "O6"), "本報表無保費概念");
            assertEquals(0L, numericAt(sheet, "G14"), "本報表無管理費概念");

            // 第一階段以 G20 放 Balance Due；本階段誤寫會整份錯位（TASK K12）
            assertTrue(isBlank(sheet, "G20"), "G20 須留空");

            for (String reference : List.of("G6", "O6", "G14", "G21", "O20", "O21")) {
                assertEquals(CoInsuranceConstants.ACCOUNTING_FORMAT,
                        cell(sheet, reference).getCellStyle().getDataFormatString(),
                        reference + " 須套用會計格式");
            }
        }
    }

    @Test
    @DisplayName("TC-N-22 理賠檔缺檔仍可成功產出，賠款相關金額為 0 且不產賠款 T 字帳")
    void succeedsWithoutClaimFile(@TempDir Path sandbox) throws IOException {
        AppConfig config = TestFixtures.sandboxConfig(sandbox);
        Files.delete(TestFixtures.claimPath(config));

        ExecutionResult result = serviceFor(config).execute(ExecutionRequest.useSettingFile());

        assertTrue(result.isSuccess(), result.message());
        assertEquals(0, result.exitCode());
        assertEquals(0L, result.calculation().totalClaim());
        assertEquals(TestFixtures.TOTAL_PREMIUM, result.calculation().totalPremium());
        assertEquals(TestFixtures.TOTAL_MANAGEMENT_FEE, result.calculation().totalManagementFee());
        assertEquals(TestFixtures.TOTAL_PREMIUM - TestFixtures.TOTAL_MANAGEMENT_FEE,
                result.calculation().balanceDue());

        assertEquals(2, result.outputFiles().size(), "賠款 T 字帳 0 份");
        assertTrue(result.calculation().reportYears().isEmpty());
        assertTrue(result.report().getClaimTAccountMessage().contains("理賠匯入檔不存在"),
                result.report().getClaimTAccountMessage());
    }

    @Test
    @DisplayName("TC-N-21 全部簽單年度皆為設定年：賠款 T 字帳 0 份，仍為成功且原因可區分")
    void succeedsWhenEveryUnderwritingYearIsConfigYear(@TempDir Path sandbox) throws IOException {
        AppConfig config = TestFixtures.sandboxConfig(sandbox);
        rewriteUnderwritingYearsToConfigYear(config);

        ExecutionResult result = serviceFor(config).execute(ExecutionRequest.useSettingFile());

        assertTrue(result.isSuccess(), result.message());
        assertEquals(0, result.exitCode());
        assertEquals(2, result.outputFiles().size(), "賠款 T 字帳 0 份");
        assertTrue(result.calculation().reportYears().isEmpty());
        // 全部列都算進設定年，故 M3 = Σ M9 = 理賠檔全部賠款
        assertEquals(TestFixtures.CLAIM_WITHOUT_YEAR_FILTER, result.calculation().totalClaim());

        String message = result.report().getClaimTAccountMessage();
        assertTrue(message.contains("無非當年度簽單資料"), message);
        assertFalse(message.contains("理賠匯入檔不存在"), "須與缺檔情境可區分：" + message);
    }

    @Test
    @DisplayName("TC-N-23 賠款樣板缺檔且須產出時中止，訊息指出樣板路徑")
    void abortsWhenClaimTemplateMissingAndYearsExist(@TempDir Path sandbox) throws IOException {
        AppConfig config = TestFixtures.sandboxConfig(sandbox);
        Files.delete(sandbox.resolve("templet").resolve("CLAIM_T_ACCOUNT.xlsx"));

        ExecutionResult result = serviceFor(config).execute(ExecutionRequest.useSettingFile());

        assertEquals(ExecutionResult.Status.FATAL, result.status());
        assertEquals(2, result.exitCode());
        assertTrue(result.message().contains("CLAIM_T_ACCOUNT.xlsx"), result.message());
    }

    @Test
    @DisplayName("TC-N-24 賠款樣板缺檔但無須產出時不中止（D14 之延後檢查）")
    void ignoresMissingClaimTemplateWhenNoYearsToReport(@TempDir Path sandbox) throws IOException {
        AppConfig config = TestFixtures.sandboxConfig(sandbox);
        Files.delete(sandbox.resolve("templet").resolve("CLAIM_T_ACCOUNT.xlsx"));
        rewriteUnderwritingYearsToConfigYear(config);

        ExecutionResult result = serviceFor(config).execute(ExecutionRequest.useSettingFile());

        assertTrue(result.isSuccess(), result.message());
        assertEquals(0, result.exitCode());
        assertEquals(2, result.outputFiles().size());
    }

    @Test
    @DisplayName("TC-E2E-11 重複執行時 4 檔全部移入備份目錄，report.json 含 M9 / M10")
    void backsUpPreviousOutput(@TempDir Path sandbox) throws IOException {
        AppConfig config = TestFixtures.sandboxConfig(sandbox);
        ReportGenerationService service = serviceFor(config);

        assertTrue(service.execute(ExecutionRequest.useSettingFile()).isSuccess());
        ExecutionResult second = service.execute(ExecutionRequest.useSettingFile());
        assertTrue(second.isSuccess(), second.message());

        Path backupMonthDir = sandbox.resolve("backup").resolve(TestFixtures.YEAR_MONTH);
        assertTrue(Files.isDirectory(backupMonthDir), "應建立備份目錄");
        assertEquals(4, second.report().getBackupFiles().size(), "4 檔皆須備份");
        try (var stamps = Files.list(backupMonthDir)) {
            Path stampDir = stamps.findFirst().orElseThrow();
            assertTrue(Files.exists(stampDir.resolve(T_ACCOUNT)));
            assertTrue(Files.exists(stampDir.resolve(SUMMARY)));
            assertTrue(Files.exists(stampDir.resolve(CLAIM_T_ACCOUNT_113)));
            assertTrue(Files.exists(stampDir.resolve(CLAIM_T_ACCOUNT_114)));
        }

        assertEquals(4, second.report().getOutputFiles().size());
        var summary = second.report().getSummary();
        assertEquals(List.of(114, 113), summary.reportYears());
        assertEquals(TestFixtures.CLAIM_YEAR_114, summary.claimByUnderwritingYear().get(114));
        assertEquals(TestFixtures.CLAIM_YEAR_113, summary.claimByUnderwritingYear().get(113));
    }

    /** 將理賠檔之簽單年度（索引 5）全部改為設定年，使 M10 為空。 */
    private static void rewriteUnderwritingYearsToConfigYear(AppConfig config) throws IOException {
        Path claimPath = TestFixtures.claimPath(config);
        Charset charset = Charset.forName(CoInsuranceConstants.CSV_CHARSET);
        List<String> lines = Files.readAllLines(claimPath, charset);

        List<String> rewritten = new ArrayList<>(List.of(lines.get(0)));
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split(",", -1);
            fields[ClaimColumn.UNDERWRITING_YEAR.index()] = String.valueOf(TestFixtures.YEAR);
            rewritten.add(String.join(",", fields));
        }
        Files.write(claimPath, rewritten, charset);
    }

    @Test
    @DisplayName("樣板公司名稱查無設定檔對應時中止，訊息含列號與名稱")
    void abortsWhenTemplateNameNotInSetting(@TempDir Path sandbox) throws Exception {
        AppConfig config = TestFixtures.sandboxConfig(sandbox);

        Path settingFile = config.settingFilePath();
        try (InputStream in = Files.newInputStream(settingFile);
             Workbook workbook = WorkbookFactory.create(in)) {
            // 將設定檔之「台灣人壽」改名，使樣板 A7 查無對應
            workbook.getSheet("工作表1").getRow(5).getCell(0).setCellValue("台灣人壽股份有限公司");
            try (OutputStream out = Files.newOutputStream(settingFile)) {
                workbook.write(out);
            }
        }

        ExecutionResult result = serviceFor(config).execute(ExecutionRequest.useSettingFile());

        assertEquals(ExecutionResult.Status.FATAL, result.status());
        assertEquals(2, result.exitCode());
        assertTrue(result.message().contains("R-EXC-06"), result.message());
        assertTrue(result.message().contains("台灣人壽"), result.message());
        assertFalse(Files.exists(sandbox.resolve("output").resolve(TestFixtures.YEAR_MONTH)
                .resolve(SUMMARY)), "中止時不得產出彙整表");
    }

    @Test
    @DisplayName("年月不符時檢核失敗，兩表皆不產出但仍產出 report.json")
    void validationFailureProducesNoReportsButWritesJson(@TempDir Path sandbox) throws IOException {
        AppConfig config = TestFixtures.sandboxConfig(sandbox);
        // 以參數指定 6 月，但匯入目錄僅有 5 月資料 → 找不到 6 月匯入檔
        // 故改為將 5 月資料複製到 6 月目錄，再觸發年月不一致
        Path juneDir = config.inputDirPath("11506");
        TestFixtures.copyDirectory(config.inputDirPath(TestFixtures.YEAR_MONTH), juneDir);
        Files.move(juneDir.resolve("VOLP11505.csv"), juneDir.resolve("VOLP11506.csv"));
        Files.move(juneDir.resolve("VOLC11505.csv"), juneDir.resolve("VOLC11506.csv"));

        ExecutionResult result = serviceFor(config).execute(new ExecutionRequest(115, 6));

        assertEquals(ExecutionResult.Status.VALIDATION_FAILED, result.status());
        assertEquals(1, result.exitCode());
        assertFalse(result.errors().isEmpty());
        assertTrue(result.errors().stream().anyMatch(error -> "R-VAL-03".equals(error.ruleId())));
        assertFalse(Files.exists(sandbox.resolve("output").resolve("11506")), "兩表皆不得產出");
        assertTrue(Files.exists(sandbox.resolve("logs").resolve("report.json")),
                "檢核失敗仍須產出 report.json");
    }

    private static Workbook open(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return WorkbookFactory.create(in);
        }
    }

    private static XSSFWorkbook openXssf(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return new XSSFWorkbook(in);
        }
    }

    private static Cell cell(Sheet sheet, String reference) {
        CellReference ref = new CellReference(reference);
        return sheet.getRow(ref.getRow()).getCell(ref.getCol());
    }

    private static String stringAt(Sheet sheet, String reference) {
        return cell(sheet, reference).getStringCellValue();
    }

    /** 儲存格未被寫入（列不存在、格不存在或為空白）。 */
    private static boolean isBlank(Sheet sheet, String reference) {
        CellReference ref = new CellReference(reference);
        Row row = sheet.getRow(ref.getRow());
        if (row == null) {
            return true;
        }
        Cell target = row.getCell(ref.getCol());
        return target == null || target.getCellType() == CellType.BLANK;
    }

    private static long numericAt(Sheet sheet, String reference) {
        Cell target = cell(sheet, reference);
        CellType type = target.getCellType() == CellType.FORMULA
                ? target.getCachedFormulaResultType() : target.getCellType();
        if (type != CellType.NUMERIC) {
            throw new AssertionError("%s 非數值儲存格（%s）".formatted(reference, type));
        }
        return Math.round(target.getNumericCellValue());
    }
}
