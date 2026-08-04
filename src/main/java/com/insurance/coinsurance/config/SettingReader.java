package com.insurance.coinsurance.config;

import com.insurance.coinsurance.constant.CoInsuranceConstants;
import com.insurance.coinsurance.exception.FatalException;
import com.insurance.coinsurance.model.CoInsuranceCompany;
import com.insurance.coinsurance.model.ExecutionRequest;
import com.insurance.coinsurance.model.Setting;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 讀取 {@code config/application.xlsx} 並執行 R-PATH-01 之全部檢核。
 *
 * <p>公司清單自 A6 起<b>動態讀至第一個空白列</b>，家數不寫死。
 */
@Component
public class SettingReader {

    private static final Logger log = LoggerFactory.getLogger(SettingReader.class);

    private final AppConfig appConfig;

    public SettingReader(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    /**
     * 讀取設定檔；{@code request} 之年月若有值則覆寫設定檔之 B1/B2。
     *
     * @throws FatalException 檔案不存在、格式錯誤、名稱／代號重複、成分 &le; 0、合計 ≠ 100%
     */
    public Setting load(ExecutionRequest request) {
        Path path = appConfig.settingFilePath();
        if (!Files.exists(path)) {
            throw new FatalException("[R-PATH-01] 找不到設定檔：%s".formatted(path.toAbsolutePath()));
        }

        try (InputStream in = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = workbook.getSheet(CoInsuranceConstants.SETTING_SHEET_NAME);
            if (sheet == null) {
                throw new FatalException("[R-PATH-01] 設定檔缺少工作表「%s」：%s"
                        .formatted(CoInsuranceConstants.SETTING_SHEET_NAME, path.toAbsolutePath()));
            }

            int year = request.year() != null ? request.year() : readIntCell(sheet, 0, 1, "設定年 (B1)");
            int month = request.month() != null ? request.month() : readIntCell(sheet, 1, 1, "設定月 (B2)");
            validatePeriod(year, month);

            List<CoInsuranceCompany> companies = readCompanies(sheet);
            validateCompanies(companies);

            log.info("設定檔載入完成：{} 年 {} 月，共保公司 {} 家，成分合計 {}",
                    year, month, companies.size(), formatPercent(shareTotal(companies)));
            return new Setting(year, month, companies);

        } catch (FatalException e) {
            throw e;
        } catch (Exception e) {
            throw new FatalException("[R-PATH-01] 設定檔讀取失敗：%s（%s）"
                    .formatted(path.toAbsolutePath(), e.getMessage()), e);
        }
    }

    private List<CoInsuranceCompany> readCompanies(Sheet sheet) {
        List<CoInsuranceCompany> companies = new ArrayList<>();
        int rowIndex = CoInsuranceConstants.SETTING_COMPANY_FIRST_ROW - 1;

        while (true) {
            Row row = sheet.getRow(rowIndex);
            String name = stringValue(cell(row, CoInsuranceConstants.SETTING_NAME_COLUMN - 1));
            String code = stringValue(cell(row, CoInsuranceConstants.SETTING_CODE_COLUMN - 1));
            Cell shareCell = cell(row, CoInsuranceConstants.SETTING_SHARE_COLUMN - 1);

            // 動態讀至第一個空白列
            if (name.isEmpty() && code.isEmpty() && isBlank(shareCell)) {
                break;
            }

            int excelRow = rowIndex + 1;
            if (name.isEmpty()) {
                throw new FatalException("[R-PATH-01] 設定檔第 %d 列之公司名稱為空白".formatted(excelRow));
            }
            if (code.isEmpty()) {
                throw new FatalException("[R-PATH-01] 設定檔第 %d 列（%s）之公司代號為空白".formatted(excelRow, name));
            }
            if (code.length() != CoInsuranceConstants.COMPANY_CODE_LENGTH) {
                throw new FatalException("[R-PATH-01] 設定檔第 %d 列（%s）之公司代號「%s」長度須為 %d"
                        .formatted(excelRow, name, code, CoInsuranceConstants.COMPANY_CODE_LENGTH));
            }
            if (isBlank(shareCell) || shareCell.getCellType() != CellType.NUMERIC) {
                throw new FatalException("[R-PATH-01] 設定檔第 %d 列（%s）之認受成分非數值".formatted(excelRow, name));
            }

            BigDecimal share = BigDecimal.valueOf(shareCell.getNumericCellValue());
            if (share.signum() <= 0) {
                throw new FatalException("[R-PATH-01] 設定檔第 %d 列（%s）之認受成分「%s」須大於 0"
                        .formatted(excelRow, name, formatPercent(share)));
            }

            companies.add(new CoInsuranceCompany(name, code, share));
            rowIndex++;
        }

        if (companies.isEmpty()) {
            throw new FatalException("[R-PATH-01] 設定檔自第 %d 列起無任何共保公司資料"
                    .formatted(CoInsuranceConstants.SETTING_COMPANY_FIRST_ROW));
        }
        return companies;
    }

    private void validateCompanies(List<CoInsuranceCompany> companies) {
        Set<String> names = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (CoInsuranceCompany company : companies) {
            if (!names.add(company.name())) {
                throw new FatalException("[R-PATH-01] 設定檔之公司名稱重複：%s".formatted(company.name()));
            }
            if (!codes.add(company.code())) {
                throw new FatalException("[R-PATH-01] 設定檔之公司代號重複：%s".formatted(company.code()));
            }
        }

        // 成分合計必須等於 100%（業務決策 B02）；差額法僅吸收四捨五入尾差，不吸收百分比缺口
        BigDecimal total = shareTotal(companies);
        if (total.compareTo(CoInsuranceConstants.REQUIRED_SHARE_TOTAL) != 0) {
            throw new FatalException("[R-PATH-01] 設定檔之共保成分合計為 %s，須等於 100%%，請修正後重新執行"
                    .formatted(formatPercent(total)));
        }

        if (codes.stream().noneMatch(CoInsuranceConstants.REINSURER_CODE::equals)) {
            throw new FatalException("[R-PATH-01] 設定檔缺少中央再保（代號 %s）"
                    .formatted(CoInsuranceConstants.REINSURER_CODE));
        }
    }

    private void validatePeriod(int year, int month) {
        if (year < CoInsuranceConstants.MIN_ROC_YEAR) {
            throw new FatalException("[R-PATH-01] 設定年「%d」須大於等於 %d"
                    .formatted(year, CoInsuranceConstants.MIN_ROC_YEAR));
        }
        if (month < 1 || month > 12) {
            throw new FatalException("[R-PATH-01] 設定月「%d」須介於 1 至 12".formatted(month));
        }
    }

    private static BigDecimal shareTotal(List<CoInsuranceCompany> companies) {
        return companies.stream()
                .map(CoInsuranceCompany::share)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int readIntCell(Sheet sheet, int rowIndex, int columnIndex, String label) {
        Cell target = cell(sheet.getRow(rowIndex), columnIndex);
        if (isBlank(target)) {
            throw new FatalException("[R-PATH-01] 設定檔之%s為空白".formatted(label));
        }
        if (target.getCellType() == CellType.NUMERIC) {
            return (int) target.getNumericCellValue();
        }
        String text = stringValue(target);
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new FatalException("[R-PATH-01] 設定檔之%s「%s」非數值".formatted(label, text));
        }
    }

    private static Cell cell(Row row, int columnIndex) {
        return row == null ? null : row.getCell(columnIndex);
    }

    private static boolean isBlank(Cell cell) {
        return cell == null || cell.getCellType() == CellType.BLANK;
    }

    private static String stringValue(Cell cell) {
        if (isBlank(cell)) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    /** 成分之百分比顯示，供錯誤訊息使用。 */
    private static String formatPercent(BigDecimal share) {
        return share.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }
}
