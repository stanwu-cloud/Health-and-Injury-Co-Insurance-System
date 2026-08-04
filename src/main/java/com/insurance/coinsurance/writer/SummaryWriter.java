package com.insurance.coinsurance.writer;

import com.insurance.coinsurance.config.AppConfig;
import com.insurance.coinsurance.constant.CoInsuranceConstants;
import com.insurance.coinsurance.constant.SummaryCell;
import com.insurance.coinsurance.exception.FatalException;
import com.insurance.coinsurance.model.CalculationResult;
import com.insurance.coinsurance.model.CoInsuranceCompany;
import com.insurance.coinsurance.model.Setting;
import org.apache.poi.ss.usermodel.Cell;
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
import java.util.Map;

/**
 * 彙總表寫入（R-OUT-02 / R-OUT-06）。
 *
 * <p>列順序<b>以樣板 A 欄為準</b>（A 欄不覆寫），逐列以公司名稱查設定檔取得代號與成分；
 * 查無對應即中止並回報列號與名稱（R-EXC-06）。
 * 計算欄位一律寫入<b>公式字串</b>而非數值，以利稽核（R-OUT-06）。
 */
@Component
public class SummaryWriter {

    private static final Logger log = LoggerFactory.getLogger(SummaryWriter.class);

    private final AppConfig appConfig;

    public SummaryWriter(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public Path write(Setting setting, CalculationResult result, Path outputDir) {
        Path template = appConfig.templateDirPath().resolve(SummaryCell.TEMPLATE_FILE_NAME);
        if (!Files.exists(template)) {
            throw new FatalException("[R-PATH-05] 找不到彙總表樣板：%s".formatted(template.toAbsolutePath()));
        }

        Path output = outputDir.resolve(
                SummaryCell.OUTPUT_FILE_PATTERN.formatted(setting.rocYearMonth()));

        try (InputStream in = Files.newInputStream(template);
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = workbook.getSheet(SummaryCell.TEMPLATE_SHEET_NAME);
            if (sheet == null) {
                throw new FatalException("[R-PATH-05] 彙總表樣板缺少工作表「%s」"
                        .formatted(SummaryCell.TEMPLATE_SHEET_NAME));
            }
            ExcelStyleHelper styleHelper = new ExcelStyleHelper(workbook);

            writeHeader(sheet, setting);

            List<String> names = readCompanyNames(sheet);
            int firstRow = SummaryCell.DETAIL_FIRST_ROW;
            int lastRow = firstRow + names.size() - 1;
            int totalRow = lastRow + 1;

            Map<String, CoInsuranceCompany> byName = setting.byName();
            int reinsurerRow = writeDetailRows(sheet, styleHelper, names, byName, result, firstRow, totalRow);
            writeReinsurerFormulas(sheet, reinsurerRow, firstRow, lastRow, totalRow);
            writeTotalRow(sheet, styleHelper, firstRow, lastRow, totalRow);

            workbook.setSheetName(workbook.getSheetIndex(sheet), SummaryCell.OUTPUT_SHEET_NAME);
            // 開檔時重算全部公式
            workbook.setForceFormulaRecalculation(true);

            Files.createDirectories(outputDir);
            try (OutputStream out = Files.newOutputStream(output)) {
                workbook.write(out);
            }
            log.info("彙總表產出完成：{}（明細 {} 列，合計列第 {} 列）",
                    output.toAbsolutePath(), names.size(), totalRow);
            return output;

        } catch (FatalException e) {
            throw e;
        } catch (Exception e) {
            throw new FatalException("[R-OUT-02] 彙總表產出失敗：%s（%s）"
                    .formatted(output.toAbsolutePath(), e.getMessage()), e);
        }
    }

    private void writeHeader(Sheet sheet, Setting setting) {
        SheetWriteUtil.cellAt(sheet, SummaryCell.UY)
                .setCellValue("Ｕ/Y：%d".formatted(setting.adYear()));
        SheetWriteUtil.cellAt(sheet, SummaryCell.PERIOD)
                .setCellValue("資料統計年月：%d年%s月".formatted(setting.year(), setting.monthOfTwoDigits()));
    }

    /** 自明細起始列往下讀 A 欄公司名稱，至空白列或「合計」列為止。 */
    private List<String> readCompanyNames(Sheet sheet) {
        List<String> names = new ArrayList<>();
        int rowIndex = SummaryCell.DETAIL_FIRST_ROW - 1;
        while (true) {
            String name = SheetWriteUtil.stringAt(sheet, rowIndex, SummaryCell.COL_COMPANY_NAME);
            if (name.isEmpty() || "合計".equals(name)) {
                break;
            }
            names.add(name);
            rowIndex++;
        }
        if (names.isEmpty()) {
            throw new FatalException("[R-OUT-02] 彙總表樣板自第 %d 列起之 A 欄無任何公司名稱"
                    .formatted(SummaryCell.DETAIL_FIRST_ROW));
        }
        return names;
    }

    /**
     * 寫入明細列之 B/C/E 值與 D/F/G/H/I/J 公式。中央再保列之 F/G 稍後另行處理。
     *
     * @return 中央再保之實際列號（1-based）
     */
    private int writeDetailRows(Sheet sheet, ExcelStyleHelper styleHelper, List<String> names,
                                Map<String, CoInsuranceCompany> byName, CalculationResult result,
                                int firstRow, int totalRow) {
        int reinsurerRow = -1;

        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            int row = firstRow + i;
            CoInsuranceCompany company = byName.get(name);
            if (company == null) {
                throw new FatalException(
                        "[R-EXC-06] 彙總表樣板第 %d 列之公司名稱「%s」查無設定檔對應，請確認 config/application.xlsx"
                                .formatted(row, name));
            }

            // B/C：數值；E：成分小數（樣板該欄格式為 0%）
            setNumeric(sheet, styleHelper, row, SummaryCell.COL_PREMIUM, result.premiumOf(company.code()));
            setNumeric(sheet, styleHelper, row, SummaryCell.COL_CLAIM, result.claimOf(company.code()));
            SheetWriteUtil.cellAt(sheet, row - 1, SummaryCell.COL_SHARE)
                    .setCellValue(company.share().doubleValue());

            setFormula(sheet, row, SummaryCell.COL_NET_RECEIVABLE, "C%d+B%d".formatted(row, row));
            if (company.isReinsurer()) {
                reinsurerRow = row;
            } else {
                setFormula(sheet, row, SummaryCell.COL_ALLOCATED_PREMIUM,
                        "ROUND(($B$%d*E%d*-1),0)".formatted(totalRow, row));
                setFormula(sheet, row, SummaryCell.COL_ALLOCATED_CLAIM,
                        "ROUND(($C$%d*E%d*-1),0)".formatted(totalRow, row));
            }
            setFormula(sheet, row, SummaryCell.COL_NET_ALLOCATED, "F%d+G%d".formatted(row, row));
            setFormula(sheet, row, SummaryCell.COL_MGMT_FEE,
                    "ROUND((F%d*%s*-1),0)".formatted(row, SummaryCell.MGMT_FEE_RATE));
            setFormula(sheet, row, SummaryCell.COL_NET_PREMIUM,
                    "D%d+H%d+I%d".formatted(row, row, row));
        }

        if (reinsurerRow < 0) {
            throw new FatalException("[R-EXC-06] 彙總表樣板之公司清單缺少中央再保（代號 %s）"
                    .formatted(CoInsuranceConstants.REINSURER_CODE));
        }
        return reinsurerRow;
    }

    /**
     * 中央再保列之 F/G 採差額法：總額 − 其餘公司加總。
     * SUM 範圍<b>依其實際列號動態產生並排除自身</b>——列號變動時會自動改為不連續區間。
     */
    private void writeReinsurerFormulas(Sheet sheet, int reinsurerRow,
                                        int firstRow, int lastRow, int totalRow) {
        String premiumRanges = rangesExcluding("F", firstRow, lastRow, reinsurerRow);
        String claimRanges = rangesExcluding("G", firstRow, lastRow, reinsurerRow);
        setFormula(sheet, reinsurerRow, SummaryCell.COL_ALLOCATED_PREMIUM,
                "-1*$B$%d-SUM(%s)".formatted(totalRow, premiumRanges));
        setFormula(sheet, reinsurerRow, SummaryCell.COL_ALLOCATED_CLAIM,
                "-1*$C$%d-SUM(%s)".formatted(totalRow, claimRanges));
    }

    /** 產生排除指定列之區間字串，例 {@code F7:F21} 或 {@code F7:F9,F11:F22}。 */
    static String rangesExcluding(String column, int firstRow, int lastRow, int excludedRow) {
        List<String> parts = new ArrayList<>();
        if (excludedRow > firstRow) {
            parts.add(range(column, firstRow, excludedRow - 1));
        }
        if (excludedRow < lastRow) {
            parts.add(range(column, excludedRow + 1, lastRow));
        }
        if (parts.isEmpty()) {
            throw new FatalException("[R-OUT-02] 彙總表僅有中央再保一列，無法以差額法計算");
        }
        return String.join(",", parts);
    }

    private static String range(String column, int from, int to) {
        return from == to
                ? "%s%d".formatted(column, from)
                : "%s%d:%s%d".formatted(column, from, column, to);
    }

    private void writeTotalRow(Sheet sheet, ExcelStyleHelper styleHelper,
                               int firstRow, int lastRow, int totalRow) {
        for (int column = SummaryCell.COL_PREMIUM; column <= SummaryCell.COL_LAST; column++) {
            String letter = CellReference.convertNumToColString(column);
            setFormula(sheet, totalRow, column,
                    "SUM(%s%d:%s%d)".formatted(letter, firstRow, letter, lastRow));
        }
        // E 欄之合計為成分百分比，沿用樣板之 0% 格式，不套會計格式
        for (int column : new int[]{SummaryCell.COL_PREMIUM, SummaryCell.COL_CLAIM,
                SummaryCell.COL_NET_RECEIVABLE, SummaryCell.COL_ALLOCATED_PREMIUM,
                SummaryCell.COL_ALLOCATED_CLAIM, SummaryCell.COL_NET_ALLOCATED,
                SummaryCell.COL_MGMT_FEE, SummaryCell.COL_NET_PREMIUM}) {
            styleHelper.applyAccounting(SheetWriteUtil.cellAt(sheet, totalRow - 1, column));
        }
    }

    private void setNumeric(Sheet sheet, ExcelStyleHelper styleHelper, int row, int column, long value) {
        Cell cell = SheetWriteUtil.cellAt(sheet, row - 1, column);
        cell.setCellValue(value);
        styleHelper.applyAccounting(cell);
    }

    private void setFormula(Sheet sheet, int row, int column, String formula) {
        SheetWriteUtil.cellAt(sheet, row - 1, column).setCellFormula(formula);
    }
}
