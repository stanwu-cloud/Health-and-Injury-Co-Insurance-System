package com.insurance.coinsurance.writer;

import com.insurance.coinsurance.constant.CoInsuranceConstants;
import com.insurance.coinsurance.constant.SummaryCell;
import com.insurance.coinsurance.exception.FatalException;
import com.insurance.coinsurance.model.CoInsuranceCompany;
import com.insurance.coinsurance.model.Setting;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 彙整表版面之明細與合計列繪製（R-OUT-02 / R-OUT-06 / R-OUT-10）。
 *
 * <p>第一階段彙整表（{@link SummaryWriter}）與第二階段賠款彙總表
 * （{@link ClaimSummaryWriter}）之版面、公式與中央再保差額法<b>完全相同</b>，
 * 僅 B / C 兩欄之值來源不同。本類別把公式字串收攏於單一處，
 * 兩張報表共用；差異由 {@link DetailValues} 注入（DESIGN §4.3 方案 A、D20）。
 *
 * <p><b>公式一律以合計列為基準</b>：{@code F} / {@code I} 欄與中央再保之差額法
 * 皆取 {@code $B$23}，故賠款彙總表只要把 B 欄餵 0，該三組公式即自動全為 0，
 * 無須另寫一套算式（D21）。
 */
final class SummarySheetPainter {

    private SummarySheetPainter() {
    }

    /** 明細列之 B / C 兩欄值來源。列順序與成分查表由本類別負責，呼叫端只提供金額。 */
    interface DetailValues {

        /** 該公司之保費合計；賠款彙總表恆回 {@code 0}。 */
        long premiumOf(String companyCode);

        /** 該公司之已決賠款合計。 */
        long claimOf(String companyCode);
    }

    /** 繪製結果，供呼叫端記錄日誌或做後續驗證。 */
    record Painted(int firstRow, int lastRow, int totalRow, int companyCount) {
    }

    /**
     * 依樣板 A 欄之公司名稱逐列寫入 B / C / E 值與 D / F / G / H / I / J 公式，
     * 並寫出合計列。<b>A 欄不覆寫</b>；查無設定檔對應即中止（R-EXC-06）。
     *
     * @param reportName 錯誤訊息中之報表名稱（「彙整表」／「賠款彙總表」）
     * @param ruleId     錯誤訊息中之規則代碼（{@code R-OUT-02} / {@code R-OUT-10}）
     */
    static Painted paint(Sheet sheet, ExcelStyleHelper styleHelper, Setting setting,
                         DetailValues values, String reportName, String ruleId) {
        List<String> names = readCompanyNames(sheet, reportName, ruleId);
        int firstRow = SummaryCell.DETAIL_FIRST_ROW;
        int lastRow = firstRow + names.size() - 1;
        int totalRow = lastRow + 1;

        Map<String, CoInsuranceCompany> byName = setting.byName();
        int reinsurerRow = writeDetailRows(sheet, styleHelper, names, byName, values,
                firstRow, totalRow, reportName);
        writeReinsurerFormulas(sheet, reinsurerRow, firstRow, lastRow, totalRow, ruleId);
        writeTotalRow(sheet, styleHelper, firstRow, lastRow, totalRow);

        return new Painted(firstRow, lastRow, totalRow, names.size());
    }

    /** 自明細起始列往下讀 A 欄公司名稱，至空白列或「合計」列為止。 */
    private static List<String> readCompanyNames(Sheet sheet, String reportName, String ruleId) {
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
            throw new FatalException("[%s] %s樣板自第 %d 列起之 A 欄無任何公司名稱"
                    .formatted(ruleId, reportName, SummaryCell.DETAIL_FIRST_ROW));
        }
        return names;
    }

    /**
     * 寫入明細列之 B/C/E 值與 D/F/G/H/I/J 公式。中央再保列之 F/G 稍後另行處理。
     *
     * @return 中央再保之實際列號（1-based）
     */
    private static int writeDetailRows(Sheet sheet, ExcelStyleHelper styleHelper, List<String> names,
                                       Map<String, CoInsuranceCompany> byName, DetailValues values,
                                       int firstRow, int totalRow, String reportName) {
        int reinsurerRow = -1;

        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            int row = firstRow + i;
            CoInsuranceCompany company = byName.get(name);
            if (company == null) {
                throw new FatalException(
                        "[R-EXC-06] %s樣板第 %d 列之公司名稱「%s」查無設定檔對應，請確認 config/application.xlsx"
                                .formatted(reportName, row, name));
            }

            // B/C：數值；E：成分小數（樣板該欄格式為 0%）
            setNumeric(sheet, styleHelper, row, SummaryCell.COL_PREMIUM, values.premiumOf(company.code()));
            setNumeric(sheet, styleHelper, row, SummaryCell.COL_CLAIM, values.claimOf(company.code()));
            SheetWriteUtil.cellAt(sheet, row - 1, SummaryCell.COL_SHARE)
                    .setCellValue(company.share().doubleValue());

            setFormula(sheet, row, SummaryCell.COL_NET_RECEIVABLE, "B%d-C%d".formatted(row, row));
            if (company.isReinsurer()) {
                reinsurerRow = row;
            } else {
                setFormula(sheet, row, SummaryCell.COL_ALLOCATED_PREMIUM,
                        "ROUND(($B$%d*E%d*-1),0)".formatted(totalRow, row));
                setFormula(sheet, row, SummaryCell.COL_ALLOCATED_CLAIM,
                        "ROUND($C$%d*E%d,0)".formatted(totalRow, row));
            }
            setFormula(sheet, row, SummaryCell.COL_NET_ALLOCATED, "F%d+G%d".formatted(row, row));
            setFormula(sheet, row, SummaryCell.COL_MGMT_FEE,
                    "ROUND((F%d*%s*-1),0)".formatted(row, SummaryCell.MGMT_FEE_RATE));
            setFormula(sheet, row, SummaryCell.COL_NET_PREMIUM,
                    "D%d+H%d+I%d".formatted(row, row, row));
        }

        if (reinsurerRow < 0) {
            throw new FatalException("[R-EXC-06] %s樣板之公司清單缺少中央再保（代號 %s）"
                    .formatted(reportName, CoInsuranceConstants.REINSURER_CODE));
        }
        return reinsurerRow;
    }

    /**
     * 中央再保列之 F/G 採差額法：總額 − 其餘公司加總。
     * SUM 範圍<b>依其實際列號動態產生並排除自身</b>——列號變動時會自動改為不連續區間。
     */
    private static void writeReinsurerFormulas(Sheet sheet, int reinsurerRow,
                                               int firstRow, int lastRow, int totalRow, String ruleId) {
        String premiumRanges = rangesExcluding("F", firstRow, lastRow, reinsurerRow, ruleId);
        String claimRanges = rangesExcluding("G", firstRow, lastRow, reinsurerRow, ruleId);
        setFormula(sheet, reinsurerRow, SummaryCell.COL_ALLOCATED_PREMIUM,
                "-1*$B$%d-SUM(%s)".formatted(totalRow, premiumRanges));
        setFormula(sheet, reinsurerRow, SummaryCell.COL_ALLOCATED_CLAIM,
                "ROUND($C$%d-SUM(%s),0)".formatted(totalRow, claimRanges));
    }

    /** 產生排除指定列之區間字串，例 {@code F7:F21} 或 {@code F7:F9,F11:F22}。 */
    static String rangesExcluding(String column, int firstRow, int lastRow, int excludedRow, String ruleId) {
        List<String> parts = new ArrayList<>();
        if (excludedRow > firstRow) {
            parts.add(range(column, firstRow, excludedRow - 1));
        }
        if (excludedRow < lastRow) {
            parts.add(range(column, excludedRow + 1, lastRow));
        }
        if (parts.isEmpty()) {
            throw new FatalException("[%s] 報表僅有中央再保一列，無法以差額法計算".formatted(ruleId));
        }
        return String.join(",", parts);
    }

    private static String range(String column, int from, int to) {
        return from == to
                ? "%s%d".formatted(column, from)
                : "%s%d:%s%d".formatted(column, from, column, to);
    }

    private static void writeTotalRow(Sheet sheet, ExcelStyleHelper styleHelper,
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

    private static void setNumeric(Sheet sheet, ExcelStyleHelper styleHelper, int row, int column, long value) {
        Cell cell = SheetWriteUtil.cellAt(sheet, row - 1, column);
        cell.setCellValue(value);
        styleHelper.applyAccounting(cell);
    }

    private static void setFormula(Sheet sheet, int row, int column, String formula) {
        SheetWriteUtil.cellAt(sheet, row - 1, column).setCellFormula(formula);
    }
}
