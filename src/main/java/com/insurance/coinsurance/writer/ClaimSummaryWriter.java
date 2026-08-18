package com.insurance.coinsurance.writer;

import com.insurance.coinsurance.config.AppConfig;
import com.insurance.coinsurance.constant.ClaimSummaryCell;
import com.insurance.coinsurance.constant.SummaryCell;
import com.insurance.coinsurance.exception.FatalException;
import com.insurance.coinsurance.model.CalculationResult;
import com.insurance.coinsurance.model.ClaimYearSummary;
import com.insurance.coinsurance.model.Setting;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 賠款彙總表寫入（R-OUT-10，第二階段·第四張報表）——<b>一報表多檔</b>。
 *
 * <p>依 {@code M10} 之每一簽單年度各產出一份，年度降冪；<b>與 {@link ClaimTAccountWriter}
 * 共用同一份 {@code M10}</b>，兩張報表之份數與年度永遠一致（DESIGN D23）。
 *
 * <p>版面與第一階段彙整表完全相同，故明細與合計列委由 {@link SummarySheetPainter} 繪製，
 * <b>公式字串一字不改</b>；與 {@link SummaryWriter} 之差異只有四處（DESIGN D20）：
 * <ul>
 *   <li>{@code A3} 之年份來源為<b>簽單年度</b>，非設定年（R-CALC-23）</li>
 *   <li>{@code C} 欄取<b>該份之簽單年度</b>之逐家賠款（M12），非設定年</li>
 *   <li>{@code B} 欄<b>恆餵 0</b>——連帶使 {@code F} / {@code I} 兩欄與中央再保之差額法
 *       自動全為 0，無須另寫算式（DESIGN D21）</li>
 *   <li>工作表名與檔名不同，且檔名多帶簽單年度後綴</li>
 * </ul>
 *
 * <p>每份寫出前自驗合計列 {@code J23 == 0}（R-CALC-24 / DESIGN D22）——本報表 {@code B} / {@code F}
 * / {@code I} 三欄恆 0，算錯時帳面上仍是「有數字、格式正確」，零和是唯一的天然自檢。
 */
@Component
public class ClaimSummaryWriter {

    private static final Logger log = LoggerFactory.getLogger(ClaimSummaryWriter.class);

    private static final String REPORT_NAME = "賠款彙總表";
    private static final String RULE_ID = "R-OUT-10";

    /** 金額皆為整數元，公式重算之浮點誤差遠小於此。 */
    private static final double ZERO_SUM_TOLERANCE = 0.5d;

    private final AppConfig appConfig;

    public ClaimSummaryWriter(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    /**
     * 逐簽單年度產出賠款彙總表。
     *
     * @return 產出檔路徑（年度降冪）；無可產出年度時為<b>空清單</b>，不拋例外（DESIGN D13）
     */
    public List<Path> writeAll(Setting setting, CalculationResult result, Path outputDir) {
        List<ClaimYearSummary> summaries = result.claimYearSummaries();
        if (summaries.isEmpty()) {
            // R-OUT-09：正常業務狀態，不得中止亦不得回傳非 0 exit code
            log.info("無非當年度簽單資料，未產出賠款彙總表");
            return List.of();
        }

        Path template = appConfig.templateDirPath().resolve(ClaimSummaryCell.TEMPLATE_FILE_NAME);
        // 樣板檢查延後至確定要產出時，以免不需產出時拖垮其他報表（D14）
        if (!Files.exists(template)) {
            throw new FatalException("[R-PATH-05] 找不到賠款彙總表樣板：%s".formatted(template.toAbsolutePath()));
        }

        List<Path> outputs = new ArrayList<>();
        for (ClaimYearSummary summary : summaries) {
            outputs.add(writeOne(setting, result, summary, template, outputDir));
        }
        log.info("賠款彙總表產出完成，共 {} 份（簽單年度 {}）", outputs.size(), result.reportYears());
        return outputs;
    }

    private Path writeOne(Setting setting, CalculationResult result, ClaimYearSummary summary,
                          Path template, Path outputDir) {
        Path output = outputDir.resolve(ClaimSummaryCell.OUTPUT_FILE_PATTERN
                .formatted(setting.rocYearMonth(), String.valueOf(summary.underwritingYear())));

        // 每份重新載入樣板：POI 之 Workbook 帶有狀態，重用會殘留前一年度之值與樣式（D11）
        try (InputStream in = Files.newInputStream(template);
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = workbook.getSheet(ClaimSummaryCell.TEMPLATE_SHEET_NAME);
            if (sheet == null) {
                throw new FatalException("[R-PATH-05] 賠款彙總表樣板缺少工作表「%s」"
                        .formatted(ClaimSummaryCell.TEMPLATE_SHEET_NAME));
            }
            ExcelStyleHelper styleHelper = new ExcelStyleHelper(workbook);

            writeHeader(sheet, setting, summary);

            SummarySheetPainter.Painted painted = SummarySheetPainter.paint(
                    sheet, styleHelper, setting,
                    new DetailValues(result, summary.underwritingYear()), REPORT_NAME, RULE_ID);

            verifyZeroSum(workbook, sheet, painted, summary);

            workbook.setSheetName(workbook.getSheetIndex(sheet), ClaimSummaryCell.OUTPUT_SHEET_NAME);
            // 開檔時重算全部公式
            workbook.setForceFormulaRecalculation(true);

            Files.createDirectories(outputDir);
            try (OutputStream out = Files.newOutputStream(output)) {
                workbook.write(out);
            }
            log.info("賠款彙總表（簽單年度 {}）產出完成：{}（明細 {} 列，合計列第 {} 列）",
                    summary.underwritingYear(), output.toAbsolutePath(),
                    painted.companyCount(), painted.totalRow());
            return output;

        } catch (FatalException e) {
            throw e;
        } catch (Exception e) {
            throw new FatalException("[R-OUT-10] 賠款彙總表產出失敗：%s（%s）"
                    .formatted(output.toAbsolutePath(), e.getMessage()), e);
        }
    }

    /**
     * A3 取<b>簽單年度</b>之西元年（R-CALC-23）；E3 仍取<b>設定檔年月</b>。
     *
     * <p>A3 若誤取設定年，同一批報表中同年度之賠款 T 字帳 {@code P4} 與本表會給出兩個年份。
     */
    private void writeHeader(Sheet sheet, Setting setting, ClaimYearSummary summary) {
        SheetWriteUtil.cellAt(sheet, ClaimSummaryCell.UY)
                .setCellValue("Ｕ/Y：%d".formatted(summary.adYear()));
        SheetWriteUtil.cellAt(sheet, ClaimSummaryCell.PERIOD)
                .setCellValue("資料統計年月：%d年%s月".formatted(setting.year(), setting.monthOfTwoDigits()));
    }

    /**
     * 零和不變式：{@code ΣJ = ΣD + ΣH + ΣI = (−ΣC) + ΣG + 0}，而中央再保之差額法保證
     * {@code ΣG ≡ ΣC}，故合計列 {@code J23} 恆為 0（R-CALC-24 / D22）。
     *
     * <p>不成立即視為<b>程式缺陷</b>並中止——比照 {@code verifyConsistency()} 之賠款承載完整性。
     */
    private void verifyZeroSum(Workbook workbook, Sheet sheet,
                               SummarySheetPainter.Painted painted, ClaimYearSummary summary) {
        Cell netPremiumTotal = SheetWriteUtil.cellAt(
                sheet, painted.totalRow() - 1, SummaryCell.COL_NET_PREMIUM);
        String reference = new CellReference(
                painted.totalRow() - 1, SummaryCell.COL_NET_PREMIUM).formatAsString(false);

        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        CellValue evaluated = evaluator.evaluate(netPremiumTotal);
        double netPremium = evaluated.getNumberValue();

        if (Math.abs(netPremium) > ZERO_SUM_TOLERANCE) {
            throw new FatalException(
                    ("[R-CALC-24] 賠款彙總表（簽單年度 %d 年）之淨收付共保費合計 %s 應為 0，實得 %.0f。"
                            + "可能原因：C 欄取錯年度、G 欄之分攤基準誤用逐家賠款而非年度合計、"
                            + "或中央再保未採差額法")
                            .formatted(summary.underwritingYear(), reference, netPremium));
        }
    }

    /**
     * B 欄恆 0（本報表不讀保費匯入檔）；C 欄取該份簽單年度之逐家賠款（M12）。
     *
     * <p><b>B 欄餵 0 之後，{@code F} / {@code I} 兩欄與中央再保之 {@code F} 全部自動歸 0</b>——
     * 三者之公式皆以 {@code $B$23} 為輸入，故公式層一字不必改寫（D21）。
     */
    private record DetailValues(CalculationResult result, int underwritingYear)
            implements SummarySheetPainter.DetailValues {

        @Override
        public long premiumOf(String companyCode) {
            return ClaimSummaryCell.PREMIUM_VALUE;
        }

        @Override
        public long claimOf(String companyCode) {
            return result.claimOf(underwritingYear, companyCode);
        }
    }
}
