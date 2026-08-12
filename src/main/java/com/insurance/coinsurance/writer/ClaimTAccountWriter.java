package com.insurance.coinsurance.writer;

import com.insurance.coinsurance.config.AppConfig;
import com.insurance.coinsurance.constant.ClaimTAccountCell;
import com.insurance.coinsurance.exception.FatalException;
import com.insurance.coinsurance.model.CalculationResult;
import com.insurance.coinsurance.model.ClaimYearSummary;
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
import java.util.ArrayList;
import java.util.List;

/**
 * 賠款 T 字帳寫入（R-OUT-08 / R-OUT-09，第二階段）——<b>一報表多檔</b>。
 *
 * <p>依 {@code M10} 之每一簽單年度各產出一份，年度降冪。三項與第一階段不同之處：
 * <ul>
 *   <li>儲存格常數走 {@link ClaimTAccountCell}，<b>不得共用</b> {@code TAccountCell}（DESIGN D10）</li>
 *   <li>每份<b>獨立載入樣板</b>，避免重用 {@link Workbook} 殘留前一年度之值（DESIGN D11）</li>
 *   <li>樣板存在性檢查<b>延後</b>至確定要產出時，以免不需產出時拖垮前兩張報表（DESIGN D14）</li>
 * </ul>
 */
@Component
public class ClaimTAccountWriter {

    private static final Logger log = LoggerFactory.getLogger(ClaimTAccountWriter.class);

    private final AppConfig appConfig;

    public ClaimTAccountWriter(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    /**
     * 逐簽單年度產出賠款 T 字帳。
     *
     * @return 產出檔路徑（年度降冪）；無可產出年度時為<b>空清單</b>，不拋例外（DESIGN D13）
     */
    public List<Path> writeAll(Setting setting, CalculationResult result, Path outputDir) {
        List<ClaimYearSummary> summaries = result.claimYearSummaries();
        if (summaries.isEmpty()) {
            // R-OUT-09：正常業務狀態，不得中止亦不得回傳非 0 exit code
            log.info("無非當年度簽單資料，未產出賠款月帳單");
            return List.of();
        }

        Path template = appConfig.templateDirPath().resolve(ClaimTAccountCell.TEMPLATE_FILE_NAME);
        if (!Files.exists(template)) {
            throw new FatalException("[R-PATH-05] 找不到賠款 T 字帳樣板：%s".formatted(template.toAbsolutePath()));
        }

        List<Path> outputs = new ArrayList<>();
        for (ClaimYearSummary summary : summaries) {
            outputs.add(writeOne(setting, summary, template, outputDir));
        }
        log.info("賠款 T 字帳產出完成，共 {} 份（簽單年度 {}）", outputs.size(), result.reportYears());
        return outputs;
    }

    private Path writeOne(Setting setting, ClaimYearSummary summary, Path template, Path outputDir) {
        Path output = outputDir.resolve(ClaimTAccountCell.OUTPUT_FILE_PATTERN
                .formatted(setting.rocYearMonth(), String.valueOf(summary.underwritingYear())));

        // 每份重新載入樣板：POI 之 Workbook 帶有狀態，重用會殘留前一年度之值與樣式（D11）
        try (InputStream in = Files.newInputStream(template);
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = workbook.getSheet(ClaimTAccountCell.TEMPLATE_SHEET_NAME);
            if (sheet == null) {
                throw new FatalException("[R-PATH-05] 賠款 T 字帳樣板缺少工作表「%s」"
                        .formatted(ClaimTAccountCell.TEMPLATE_SHEET_NAME));
            }
            ExcelStyleHelper styleHelper = new ExcelStyleHelper(workbook);

            // 資料統計年月：單格寫入、月份補零；J3 不寫入（P-01）
            SheetWriteUtil.cellAt(sheet, ClaimTAccountCell.PERIOD)
                    .setCellValue("%d 年 %s 月".formatted(setting.year(), setting.monthOfTwoDigits()));

            // U/Y 年：來源為簽單年度（R-CALC-20）；O4 之「U/Y:」為樣板既有，不覆寫
            SheetWriteUtil.cellAt(sheet, ClaimTAccountCell.UY_YEAR)
                    .setCellValue(String.valueOf(summary.adYear()));

            // Balance Due 直接等於該年度賠款總和，不套用 M8；G20 完全不碰（P-03 / P-04）
            long claim = summary.claim();
            writeAmount(sheet, styleHelper, ClaimTAccountCell.CLAIM, claim);
            writeAmount(sheet, styleHelper, ClaimTAccountCell.BALANCE_DUE, claim);
            writeAmount(sheet, styleHelper, ClaimTAccountCell.RIGHT_TOTAL_UPPER, claim);
            writeAmount(sheet, styleHelper, ClaimTAccountCell.RIGHT_TOTAL_LOWER, claim);
            // 本報表無保費概念，固定寫 0；會計格式使畫面顯示「-」（P-05）
            writeAmount(sheet, styleHelper, ClaimTAccountCell.PREMIUM, 0L);
            writeAmount(sheet, styleHelper, ClaimTAccountCell.MGMT_FEE, 0L);

            workbook.setSheetName(workbook.getSheetIndex(sheet), ClaimTAccountCell.OUTPUT_SHEET_NAME);

            Files.createDirectories(outputDir);
            try (OutputStream out = Files.newOutputStream(output)) {
                workbook.write(out);
            }
            log.info("賠款 T 字帳（簽單年度 {}）產出完成：{}", summary.underwritingYear(), output.toAbsolutePath());
            return output;

        } catch (FatalException e) {
            throw e;
        } catch (Exception e) {
            throw new FatalException("[R-OUT-08] 賠款 T 字帳產出失敗：%s（%s）"
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
