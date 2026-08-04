package com.insurance.coinsurance.validator;

import com.insurance.coinsurance.constant.ClaimColumn;
import com.insurance.coinsurance.constant.PremiumColumn;
import com.insurance.coinsurance.model.ClaimRecord;
import com.insurance.coinsurance.model.PremiumRecord;
import com.insurance.coinsurance.model.Setting;
import com.insurance.coinsurance.model.ValidationError;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 匯入檔年月一致性檢核（R-VAL-03）。
 *
 * <p><b>以數值比對</b>，不比對字串長度或前導零——匯入檔月份為 1 碼 {@code 5}，
 * 若以字串比對 {@code 05} 會全數失敗（TASK 風險欄）。
 */
@Component
public class PeriodValidator {

    public List<ValidationError> validatePremium(String fileName, List<PremiumRecord> records, Setting setting) {
        ErrorCollector collector = new ErrorCollector(fileName);
        for (PremiumRecord record : records) {
            check(collector, record.rowNumber(),
                    PremiumColumn.BILL_YEAR.label(), record.billYear(), setting.year(), "設定年");
            check(collector, record.rowNumber(),
                    PremiumColumn.BILL_MONTH.label(), record.billMonth(), setting.month(), "設定月");
        }
        return collector.errors();
    }

    public List<ValidationError> validateClaim(String fileName, List<ClaimRecord> records, Setting setting) {
        ErrorCollector collector = new ErrorCollector(fileName);
        for (ClaimRecord record : records) {
            check(collector, record.rowNumber(),
                    ClaimColumn.BILL_YEAR.label(), record.billYear(), setting.year(), "設定年");
            check(collector, record.rowNumber(),
                    ClaimColumn.BILL_MONTH.label(), record.billMonth(), setting.month(), "設定月");
        }
        return collector.errors();
    }

    private void check(ErrorCollector collector, int row, String fieldName,
                       String value, int expected, String expectedLabel) {
        Long actual = FieldRules.parseLongOrNull(value);
        if (actual == null) {
            // 格式問題已由 R-VAL-01/02 記錄，此處不重複記錄
            return;
        }
        collector.addIf(actual != expected, row, fieldName, value, "R-VAL-03",
                "須等於%s %d".formatted(expectedLabel, expected));
    }
}
