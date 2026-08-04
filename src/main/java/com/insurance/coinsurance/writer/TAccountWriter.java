package com.insurance.coinsurance.writer;

import com.insurance.coinsurance.config.AppConfig;
import com.insurance.coinsurance.constant.TAccountCell;
import com.insurance.coinsurance.exception.FatalException;
import com.insurance.coinsurance.model.CalculationResult;
import com.insurance.coinsurance.model.Setting;
import org.apache.poi.ss.usermodel.Cell;
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

/** T 字帳寫入（R-OUT-01）。 */
@Component
public class TAccountWriter {

    private static final Logger log = LoggerFactory.getLogger(TAccountWriter.class);

    private final AppConfig appConfig;

    public TAccountWriter(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    /**
     * 依樣板產出 T 字帳。
     *
     * @return 產出檔路徑
     */
    public Path write(Setting setting, CalculationResult result, Path outputDir) {
        Path template = appConfig.templateDirPath().resolve(TAccountCell.TEMPLATE_FILE_NAME);
        if (!Files.exists(template)) {
            throw new FatalException("[R-PATH-05] 找不到 T 字帳樣板：%s".formatted(template.toAbsolutePath()));
        }

        Path output = outputDir.resolve(
                TAccountCell.OUTPUT_FILE_PATTERN.formatted(setting.rocYearMonth()));

        try (InputStream in = Files.newInputStream(template);
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = workbook.getSheet(TAccountCell.TEMPLATE_SHEET_NAME);
            if (sheet == null) {
                throw new FatalException("[R-PATH-05] T 字帳樣板缺少工作表「%s」"
                        .formatted(TAccountCell.TEMPLATE_SHEET_NAME));
            }
            ExcelStyleHelper styleHelper = new ExcelStyleHelper(workbook);

            // 資料統計年月：單格寫入，月份補零
            SheetWriteUtil.cellAt(sheet, TAccountCell.PERIOD)
                    .setCellValue("%d 年 %s 月".formatted(setting.year(), setting.monthOfTwoDigits()));

            // U/Y 年：僅寫西元年；O4 之「U/Y:」為樣板既有，不覆寫
            SheetWriteUtil.cellAt(sheet, TAccountCell.UY_YEAR)
                    .setCellValue(String.valueOf(setting.adYear()));

            writeAmount(sheet, styleHelper, TAccountCell.CLAIM, result.totalClaim());
            writeAmount(sheet, styleHelper, TAccountCell.PREMIUM, result.totalPremium());
            writeAmount(sheet, styleHelper, TAccountCell.MGMT_FEE, result.totalManagementFee());
            // Balance Due 為負時填負數，不得為空
            writeAmount(sheet, styleHelper, TAccountCell.BALANCE_DUE, result.balanceDue());
            // G21 / O21 依規格書 v1.2 之文字定義直接等於共保保費，非欄位加總
            writeAmount(sheet, styleHelper, TAccountCell.LEFT_TOTAL, result.totalPremium());
            writeAmount(sheet, styleHelper, TAccountCell.RIGHT_TOTAL, result.totalPremium());

            workbook.setSheetName(workbook.getSheetIndex(sheet), TAccountCell.OUTPUT_SHEET_NAME);

            Files.createDirectories(outputDir);
            try (OutputStream out = Files.newOutputStream(output)) {
                workbook.write(out);
            }
            log.info("T 字帳產出完成：{}", output.toAbsolutePath());
            return output;

        } catch (FatalException e) {
            throw e;
        } catch (Exception e) {
            throw new FatalException("[R-OUT-01] T 字帳產出失敗：%s（%s）"
                    .formatted(output.toAbsolutePath(), e.getMessage()), e);
        }
    }

    private void writeAmount(Sheet sheet, ExcelStyleHelper styleHelper, String reference, long value) {
        Cell cell = SheetWriteUtil.cellAt(sheet, reference);
        cell.setCellValue(value);
        // 樣板原為 General，須由程式設定會計格式
        styleHelper.applyAccounting(cell);
    }
}
