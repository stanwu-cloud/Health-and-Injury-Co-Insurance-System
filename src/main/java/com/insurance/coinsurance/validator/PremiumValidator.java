package com.insurance.coinsurance.validator;

import com.insurance.coinsurance.constant.CoInsuranceConstants;
import com.insurance.coinsurance.constant.PremiumColumn;
import com.insurance.coinsurance.model.PremiumRecord;
import com.insurance.coinsurance.model.Setting;
import com.insurance.coinsurance.model.ValidationError;
import com.insurance.coinsurance.util.RocDateUtil;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 保費檔欄位檢核（R-VAL-01，20 條子規則）。
 *
 * <p>收集全部錯誤不中斷（DESIGN D7）。
 */
@Component
public class PremiumValidator {

    private static final int MAX_DEATH_DISABILITY_AMOUNT = 1_000_000;

    public List<ValidationError> validate(String fileName, List<PremiumRecord> records, Setting setting) {
        ErrorCollector collector = new ErrorCollector(fileName);
        Set<String> validCodes = setting.companies().stream()
                .map(company -> company.code())
                .collect(Collectors.toSet());

        for (PremiumRecord record : records) {
            validateRow(collector, record, validCodes);
        }
        return collector.errors();
    }

    private void validateRow(ErrorCollector collector, PremiumRecord record, Set<String> validCodes) {
        int row = record.rowNumber();

        // R-VAL-01-01 帳單年度
        String billYear = record.billYear();
        Long billYearValue = FieldRules.parseLongOrNull(billYear);
        if (!FieldRules.withinLength(billYear, 3) || billYearValue == null
                || billYearValue < CoInsuranceConstants.MIN_ROC_YEAR) {
            collector.add(row, PremiumColumn.BILL_YEAR.label(), billYear, "R-VAL-01-01",
                    "須為 3 碼以內之數值且大於等於 %d".formatted(CoInsuranceConstants.MIN_ROC_YEAR));
        }

        // R-VAL-01-02 帳單月份（匯入檔可為 1 碼，以數值判定）
        String billMonth = record.billMonth();
        Long billMonthValue = FieldRules.parseLongOrNull(billMonth);
        if (!FieldRules.withinLength(billMonth, 2) || billMonthValue == null
                || billMonthValue < 1 || billMonthValue > 12) {
            collector.add(row, PremiumColumn.BILL_MONTH.label(), billMonth, "R-VAL-01-02",
                    "須為 2 碼以內之數值且介於 1 至 12");
        }

        // R-VAL-01-03 公司
        String companyCode = record.companyCode();
        if (FieldRules.length(companyCode) != CoInsuranceConstants.COMPANY_CODE_LENGTH
                || !isValidPrefix(companyCode)) {
            collector.add(row, PremiumColumn.COMPANY_CODE.label(), companyCode, "R-VAL-01-03",
                    "須為 3 碼且首碼為 N（產險）或 L（壽險）");
        } else if (!validCodes.contains(companyCode)) {
            collector.add(row, PremiumColumn.COMPANY_CODE.label(), companyCode, "R-EXC-04",
                    "查無此公司代號於設定檔之共保公司清單");
        }

        // R-VAL-01-04 保單號碼
        validateRequiredText(collector, row, PremiumColumn.POLICY_NO, record.policyNo(), 20, "R-VAL-01-04");

        // R-VAL-01-05 批單號碼：批單起日或迄日非空白時不可空白
        String endorsementNo = record.endorsementNo();
        boolean endorsementDated = !FieldRules.isBlank(record.endorsementStartDate())
                || !FieldRules.isBlank(record.endorsementEndDate());
        collector.addIf(endorsementDated && FieldRules.isBlank(endorsementNo),
                row, PremiumColumn.ENDORSEMENT_NO.label(), endorsementNo, "R-VAL-01-05",
                "批單起日或迄日非空白時，批單號碼不可空白");
        collector.addIf(!FieldRules.withinLength(endorsementNo, 20),
                row, PremiumColumn.ENDORSEMENT_NO.label(), endorsementNo, "R-VAL-01-05",
                "長度不可超過 20");

        // R-VAL-01-06 年齡代號（空白以 0 計）
        collector.addIf(!FieldRules.withinLength(record.ageCode(), 1),
                row, PremiumColumn.AGE_CODE.label(), record.ageCode(), "R-VAL-01-06",
                "長度不可超過 1");

        // R-VAL-01-07 被保險人數
        String insuredCount = record.insuredCount();
        Long insuredCountValue = FieldRules.parseLongOrNull(insuredCount);
        if (!FieldRules.withinLength(insuredCount, 5) || insuredCountValue == null || insuredCountValue <= 0) {
            collector.add(row, PremiumColumn.INSURED_COUNT.label(), insuredCount, "R-VAL-01-07",
                    "須為 5 碼以內之數值且大於 0");
        }

        // R-VAL-01-08 / 09 保單起迄日（不可空白）
        validateRequiredRocDate(collector, row, PremiumColumn.POLICY_START_DATE,
                record.policyStartDate(), "R-VAL-01-08");
        validateRequiredRocDate(collector, row, PremiumColumn.POLICY_END_DATE,
                record.policyEndDate(), "R-VAL-01-09");

        // R-VAL-01-10 / 11 批單起迄日（可為空白；v1.2 已修訂）
        validateOptionalRocDate(collector, row, PremiumColumn.ENDORSEMENT_START_DATE,
                record.endorsementStartDate(), "R-VAL-01-10");
        validateOptionalRocDate(collector, row, PremiumColumn.ENDORSEMENT_END_DATE,
                record.endorsementEndDate(), "R-VAL-01-11");

        // R-VAL-01-12 身故殘廢(保額)
        String deathAmount = record.deathDisabilityAmount();
        Long deathAmountValue = FieldRules.numericOrZero(deathAmount);
        if (deathAmountValue == null) {
            collector.add(row, PremiumColumn.DEATH_DISABILITY_AMOUNT.label(), deathAmount, "R-VAL-01-12",
                    "須為數值");
        } else if (deathAmountValue > MAX_DEATH_DISABILITY_AMOUNT) {
            collector.add(row, PremiumColumn.DEATH_DISABILITY_AMOUNT.label(), deathAmount, "R-VAL-01-12",
                    "不可超過 %,d".formatted(MAX_DEATH_DISABILITY_AMOUNT));
        }

        // R-VAL-01-13~17 各保額／純保費欄（長度 <= 8，空白以 0 計）
        // 索引 12/13 之值不參與後續計算，僅需完成格式檢核
        validateNumeric(collector, row, PremiumColumn.MEDICAL_AMOUNT,
                record.medicalAmount(), "R-VAL-01-13");
        validateNumeric(collector, row, PremiumColumn.DAILY_AMOUNT,
                record.dailyAmount(), "R-VAL-01-14");
        Long deathPremium = validateNumeric(collector, row, PremiumColumn.DEATH_DISABILITY_PREMIUM,
                record.deathDisabilityPremium(), "R-VAL-01-15");
        Long medicalPremium = validateNumeric(collector, row, PremiumColumn.MEDICAL_PREMIUM,
                record.medicalPremium(), "R-VAL-01-16");
        Long dailyPremium = validateNumeric(collector, row, PremiumColumn.DAILY_PREMIUM,
                record.dailyPremium(), "R-VAL-01-17");

        // R-VAL-01-18 保費合計 = 身故殘廢 + 實支醫療 + 日額（純保費）
        String totalPremium = record.totalPremium();
        Long totalPremiumValue = FieldRules.parseLongOrNull(totalPremium);
        if (totalPremiumValue == null || !FieldRules.withinLength(totalPremium, 8)) {
            collector.add(row, PremiumColumn.TOTAL_PREMIUM.label(), totalPremium, "R-VAL-01-18",
                    "須為 8 碼以內之數值（可為負值）");
        } else if (deathPremium != null && medicalPremium != null && dailyPremium != null) {
            long expected = deathPremium + medicalPremium + dailyPremium;
            collector.addIf(totalPremiumValue != expected,
                    row, PremiumColumn.TOTAL_PREMIUM.label(), totalPremium, "R-VAL-01-18",
                    "須等於身故殘廢 + 實支醫療 + 日額（純保費）＝ %d".formatted(expected));
        }

        // R-VAL-01-19 備註(批改原因)
        collector.addIf(!FieldRules.withinLength(record.remark(), 1),
                row, PremiumColumn.REMARK.label(), record.remark(), "R-VAL-01-19",
                "長度不可超過 1");
    }

    private static boolean isValidPrefix(String companyCode) {
        if (FieldRules.isBlank(companyCode)) {
            return false;
        }
        char prefix = companyCode.charAt(0);
        return prefix == 'N' || prefix == 'L';
    }

    private void validateRequiredText(ErrorCollector collector, int row, PremiumColumn column,
                                      String value, int maxLength, String ruleId) {
        if (FieldRules.isBlank(value)) {
            collector.add(row, column.label(), value, ruleId, "不可空白");
        } else if (!FieldRules.withinLength(value, maxLength)) {
            collector.add(row, column.label(), value, ruleId, "長度不可超過 %d".formatted(maxLength));
        }
    }

    private void validateRequiredRocDate(ErrorCollector collector, int row, PremiumColumn column,
                                         String value, String ruleId) {
        if (FieldRules.isBlank(value)) {
            collector.add(row, column.label(), value, ruleId, "不可空白");
        } else if (!RocDateUtil.isValidRocDate(value)) {
            collector.add(row, column.label(), value, ruleId, "須為 yyymmdd 共 7 碼之合法民國年日期");
        }
    }

    private void validateOptionalRocDate(ErrorCollector collector, int row, PremiumColumn column,
                                         String value, String ruleId) {
        if (!FieldRules.isBlank(value) && !RocDateUtil.isValidRocDate(value)) {
            collector.add(row, column.label(), value, ruleId, "非空白時須為 yyymmdd 共 7 碼之合法民國年日期");
        }
    }

    private Long validateNumeric(ErrorCollector collector, int row, PremiumColumn column,
                                 String value, String ruleId) {
        if (!FieldRules.withinLength(value, 8)) {
            collector.add(row, column.label(), value, ruleId, "長度不可超過 8");
            return null;
        }
        Long parsed = FieldRules.numericOrZero(value);
        if (parsed == null) {
            collector.add(row, column.label(), value, ruleId, "須為數值（空白以 0 計）");
        }
        return parsed;
    }
}
