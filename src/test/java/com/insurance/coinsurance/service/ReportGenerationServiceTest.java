package com.insurance.coinsurance.service;

import com.insurance.coinsurance.calculator.AllocationCalculator;
import com.insurance.coinsurance.calculator.ClaimCalculator;
import com.insurance.coinsurance.calculator.ManagementFeeCalculator;
import com.insurance.coinsurance.calculator.PremiumCalculator;
import com.insurance.coinsurance.config.AppConfig;
import com.insurance.coinsurance.config.SettingReader;
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
import com.insurance.coinsurance.writer.ReportJsonWriter;
import com.insurance.coinsurance.writer.SummaryWriter;
import com.insurance.coinsurance.writer.TAccountWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TC-E2E 系列：以 `檔案位子範例/` 為輸入之端到端驗證。 */
class ReportGenerationServiceTest {

    private static final String T_ACCOUNT = "共保保費_當月共保月帳單_T字帳報表11505.xlsx";
    private static final String SUMMARY = "共保保費_當月共保月帳單_彙整表11505.xlsx";

    private static ReportGenerationService serviceFor(AppConfig config) {
        return new ReportGenerationService(config, new SettingReader(config),
                new PremiumCsvReader(), new ClaimCsvReader(),
                new PremiumValidator(), new ClaimValidator(config), new PeriodValidator(),
                new PremiumCalculator(), new ClaimCalculator(),
                new AllocationCalculator(), new ManagementFeeCalculator(),
                new BackupService(config), new TAccountWriter(config), new SummaryWriter(config),
                new ReportJsonWriter(config));
    }

    @Test
    @DisplayName("端到端產出兩張報表，儲存格與公式結果符合文件基準值")
    void endToEnd(@TempDir Path sandbox) throws IOException {
        AppConfig config = TestFixtures.sandboxConfig(sandbox);
        ExecutionResult result = serviceFor(config).execute(ExecutionRequest.useSettingFile());

        assertTrue(result.isSuccess(), result.message());
        assertEquals(0, result.exitCode());
        assertEquals(2, result.outputFiles().size());

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
            assertEquals("-1*$C$23-SUM(G7:G21)", cell(sheet, "G22").getCellFormula());

            XSSFFormulaEvaluator.evaluateAllFormulaCells(workbook);

            assertEquals(TestFixtures.TOTAL_PREMIUM, numericAt(sheet, "B15"));
            assertEquals(TestFixtures.TOTAL_CLAIM, numericAt(sheet, "C15"));
            assertEquals(TestFixtures.TOTAL_PREMIUM, numericAt(sheet, "B23"));
            assertEquals(TestFixtures.TOTAL_CLAIM, numericAt(sheet, "C23"));
            assertEquals(100L, Math.round(cell(sheet, "E23").getNumericCellValue() * 100), "E23 須為 100%");

            assertEquals(-TestFixtures.REINSURER_ALLOCATED_PREMIUM, numericAt(sheet, "F22"));
            assertEquals(-TestFixtures.REINSURER_ALLOCATED_CLAIM, numericAt(sheet, "G22"));
            assertEquals(2_101L, numericAt(sheet, "I22"));
            assertEquals(1_050L, numericAt(sheet, "I15"));

            assertEquals(-TestFixtures.TOTAL_PREMIUM, numericAt(sheet, "F23"));
            assertEquals(-TestFixtures.TOTAL_CLAIM, numericAt(sheet, "G23"));
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
    @DisplayName("理賠檔缺檔仍可成功產出，賠款相關金額為 0")
    void succeedsWithoutClaimFile(@TempDir Path sandbox) throws IOException {
        AppConfig config = TestFixtures.sandboxConfig(sandbox);
        Files.delete(TestFixtures.claimPath(config));

        ExecutionResult result = serviceFor(config).execute(ExecutionRequest.useSettingFile());

        assertTrue(result.isSuccess(), result.message());
        assertEquals(0L, result.calculation().totalClaim());
        assertEquals(TestFixtures.TOTAL_PREMIUM, result.calculation().totalPremium());
        assertEquals(TestFixtures.TOTAL_MANAGEMENT_FEE, result.calculation().totalManagementFee());
        assertEquals(TestFixtures.TOTAL_PREMIUM - TestFixtures.TOTAL_MANAGEMENT_FEE,
                result.calculation().balanceDue());
    }

    @Test
    @DisplayName("重複執行時前次輸出被移入備份目錄")
    void backsUpPreviousOutput(@TempDir Path sandbox) throws IOException {
        AppConfig config = TestFixtures.sandboxConfig(sandbox);
        ReportGenerationService service = serviceFor(config);

        assertTrue(service.execute(ExecutionRequest.useSettingFile()).isSuccess());
        ExecutionResult second = service.execute(ExecutionRequest.useSettingFile());
        assertTrue(second.isSuccess(), second.message());

        Path backupMonthDir = sandbox.resolve("backup").resolve(TestFixtures.YEAR_MONTH);
        assertTrue(Files.isDirectory(backupMonthDir), "應建立備份目錄");
        assertEquals(2, second.report().getBackupFiles().size(), "兩張報表皆須備份");
        try (var stamps = Files.list(backupMonthDir)) {
            Path stampDir = stamps.findFirst().orElseThrow();
            assertTrue(Files.exists(stampDir.resolve(T_ACCOUNT)));
            assertTrue(Files.exists(stampDir.resolve(SUMMARY)));
        }
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
