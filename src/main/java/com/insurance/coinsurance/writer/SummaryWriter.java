package com.insurance.coinsurance.writer;

import com.insurance.coinsurance.config.AppConfig;
import com.insurance.coinsurance.constant.SummaryCell;
import com.insurance.coinsurance.exception.FatalException;
import com.insurance.coinsurance.model.CalculationResult;
import com.insurance.coinsurance.model.Setting;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 彙整表寫入（R-OUT-02 / R-OUT-06）。
 *
 * <p>列順序<b>以樣板 A 欄為準</b>（A 欄不覆寫），逐列以公司名稱查設定檔取得代號與成分；
 * 查無對應即中止並回報列號與名稱（R-EXC-06）。
 * 計算欄位一律寫入<b>公式字串</b>而非數值，以利稽核（R-OUT-06）。
 *
 * <p>明細與合計列之繪製委由 {@link SummarySheetPainter}，與第二階段之
 * {@link ClaimSummaryWriter} 共用同一組公式字串（DESIGN §4.3、D20）。
 */
@Component
public class SummaryWriter {

    private static final Logger log = LoggerFactory.getLogger(SummaryWriter.class);

    private static final String REPORT_NAME = "彙整表";
    private static final String RULE_ID = "R-OUT-02";

    private final AppConfig appConfig;

    public SummaryWriter(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public Path write(Setting setting, CalculationResult result, Path outputDir) {
        Path template = appConfig.templateDirPath().resolve(SummaryCell.TEMPLATE_FILE_NAME);
        if (!Files.exists(template)) {
            throw new FatalException("[R-PATH-05] 找不到彙整表樣板：%s".formatted(template.toAbsolutePath()));
        }

        Path output = outputDir.resolve(
                SummaryCell.OUTPUT_FILE_PATTERN.formatted(setting.rocYearMonth()));

        try (InputStream in = Files.newInputStream(template);
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = workbook.getSheet(SummaryCell.TEMPLATE_SHEET_NAME);
            if (sheet == null) {
                throw new FatalException("[R-PATH-05] 彙整表樣板缺少工作表「%s」"
                        .formatted(SummaryCell.TEMPLATE_SHEET_NAME));
            }
            ExcelStyleHelper styleHelper = new ExcelStyleHelper(workbook);

            writeHeader(sheet, setting);

            SummarySheetPainter.Painted painted = SummarySheetPainter.paint(
                    sheet, styleHelper, setting, new DetailValues(result), REPORT_NAME, RULE_ID);

            workbook.setSheetName(workbook.getSheetIndex(sheet), SummaryCell.OUTPUT_SHEET_NAME);
            // 開檔時重算全部公式
            workbook.setForceFormulaRecalculation(true);

            Files.createDirectories(outputDir);
            try (OutputStream out = Files.newOutputStream(output)) {
                workbook.write(out);
            }
            log.info("彙整表產出完成：{}（明細 {} 列，合計列第 {} 列）",
                    output.toAbsolutePath(), painted.companyCount(), painted.totalRow());
            return output;

        } catch (FatalException e) {
            throw e;
        } catch (Exception e) {
            throw new FatalException("[R-OUT-02] 彙整表產出失敗：%s（%s）"
                    .formatted(output.toAbsolutePath(), e.getMessage()), e);
        }
    }

    private void writeHeader(Sheet sheet, Setting setting) {
        SheetWriteUtil.cellAt(sheet, SummaryCell.UY)
                .setCellValue("Ｕ/Y：%d".formatted(setting.adYear()));
        SheetWriteUtil.cellAt(sheet, SummaryCell.PERIOD)
                .setCellValue("資料統計年月：%d年%s月".formatted(setting.year(), setting.monthOfTwoDigits()));
    }

    /** B / C 兩欄取本月之保費與賠款（賠款已篩簽單年度 = 設定年）。 */
    private record DetailValues(CalculationResult result) implements SummarySheetPainter.DetailValues {

        @Override
        public long premiumOf(String companyCode) {
            return result.premiumOf(companyCode);
        }

        @Override
        public long claimOf(String companyCode) {
            return result.claimOf(companyCode);
        }
    }
}
