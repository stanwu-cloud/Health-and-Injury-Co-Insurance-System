package com.insurance.coinsurance.validator;

import com.insurance.coinsurance.config.AppConfig;
import com.insurance.coinsurance.constant.ClaimColumn;
import com.insurance.coinsurance.constant.CoInsuranceConstants;
import com.insurance.coinsurance.model.ClaimRecord;
import com.insurance.coinsurance.model.Setting;
import com.insurance.coinsurance.model.ValidationError;
import com.insurance.coinsurance.util.RocDateUtil;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 理賠檔欄位檢核（R-VAL-02，22 條子規則）。
 *
 * <p>錯誤訊息之 {@code actualValue} 若來自個資欄位，寫入前先經
 * {@link ClaimColumn#maskIfPersonalData(String)} 遮蔽（R-RUN-06）。
 */
@Component
public class ClaimValidator {

    private final AppConfig appConfig;

    public ClaimValidator(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public List<ValidationError> validate(String fileName, List<ClaimRecord> records, Setting setting) {
        ErrorCollector collector = new ErrorCollector(fileName);
        Set<String> validCodes = setting.companies().stream()
                .map(company -> company.code())
                .collect(Collectors.toSet());

        for (ClaimRecord record : records) {
            validateRow(collector, record, validCodes);
        }
        return collector.errors();
    }

    private void validateRow(ErrorCollector collector, ClaimRecord record, Set<String> validCodes) {
        int row = record.rowNumber();

        // R-VAL-02-01 帳單年度
        String billYear = record.billYear();
        Long billYearValue = FieldRules.parseLongOrNull(billYear);
        if (!FieldRules.withinLength(billYear, 3) || billYearValue == null
                || billYearValue < CoInsuranceConstants.MIN_ROC_YEAR) {
            add(collector, row, ClaimColumn.BILL_YEAR, billYear, "R-VAL-02-01",
                    "須為 3 碼以內之數值且大於等於 %d".formatted(CoInsuranceConstants.MIN_ROC_YEAR));
        }

        // R-VAL-02-02 帳單月份
        String billMonth = record.billMonth();
        Long billMonthValue = FieldRules.parseLongOrNull(billMonth);
        if (!FieldRules.withinLength(billMonth, 2) || billMonthValue == null
                || billMonthValue < 1 || billMonthValue > 12) {
            add(collector, row, ClaimColumn.BILL_MONTH, billMonth, "R-VAL-02-02",
                    "須為 2 碼以內之數值且介於 1 至 12");
        }

        // R-VAL-02-03 公司
        String companyCode = record.companyCode();
        if (FieldRules.length(companyCode) != CoInsuranceConstants.COMPANY_CODE_LENGTH
                || !isValidPrefix(companyCode)) {
            add(collector, row, ClaimColumn.COMPANY_CODE, companyCode, "R-VAL-02-03",
                    "須為 3 碼且首碼為 N（產險）或 L（壽險）");
        } else if (!validCodes.contains(companyCode)) {
            add(collector, row, ClaimColumn.COMPANY_CODE, companyCode, "R-EXC-04",
                    "查無此公司代號於設定檔之共保公司清單");
        }

        // R-VAL-02-04 / 05 賠案號碼、保單號碼
        validateRequiredText(collector, row, ClaimColumn.CLAIM_NO, record.claimNo(), 20, "R-VAL-02-04");
        validateRequiredText(collector, row, ClaimColumn.POLICY_NO, record.policyNo(), 20, "R-VAL-02-05");

        // R-VAL-02-06 簽單年度（報表核心篩選欄位）
        String underwritingYear = record.underwritingYear();
        Long underwritingYearValue = FieldRules.parseLongOrNull(underwritingYear);
        if (!FieldRules.withinLength(underwritingYear, 3) || underwritingYearValue == null
                || underwritingYearValue <= 0) {
            add(collector, row, ClaimColumn.UNDERWRITING_YEAR, underwritingYear, "R-VAL-02-06",
                    "須為 3 碼以內之民國年數值");
        }

        // R-VAL-02-07 / 08 保單起迄日
        validateRequiredRocDate(collector, row, ClaimColumn.POLICY_START_DATE,
                record.policyStartDate(), "R-VAL-02-07");
        validateRequiredRocDate(collector, row, ClaimColumn.POLICY_END_DATE,
                record.policyEndDate(), "R-VAL-02-08");

        // R-VAL-02-09 / 10 被保險人姓名、身分證號（個資）
        validateRequiredText(collector, row, ClaimColumn.INSURED_NAME, record.insuredName(), 20, "R-VAL-02-09");
        validateRequiredText(collector, row, ClaimColumn.INSURED_ID, record.insuredId(), 10, "R-VAL-02-10");

        // R-VAL-02-11 被保險人出生日期（僅接受 7 碼）
        validateRequiredRocDate(collector, row, ClaimColumn.INSURED_BIRTH_DATE,
                record.insuredBirthDate(), "R-VAL-02-11");

        // R-VAL-02-12 出險日
        validateRequiredRocDate(collector, row, ClaimColumn.ACCIDENT_DATE, record.accidentDate(), "R-VAL-02-12");

        // R-VAL-02-13 出險原因
        validateRequiredText(collector, row, ClaimColumn.ACCIDENT_REASON,
                record.accidentReason(), 50, "R-VAL-02-13");

        // R-VAL-02-14 決賠日
        validateRequiredRocDate(collector, row, ClaimColumn.SETTLEMENT_DATE,
                record.settlementDate(), "R-VAL-02-14");

        // R-VAL-02-15~21 各保額／賠款／理賠費用欄（長度 <= 8，空白以 0 計）
        validateNumeric(collector, row, ClaimColumn.DEATH_DISABILITY_AMOUNT,
                record.deathDisabilityAmount(), "R-VAL-02-15");
        validateNumeric(collector, row, ClaimColumn.MEDICAL_AMOUNT, record.medicalAmount(), "R-VAL-02-16");
        validateNumeric(collector, row, ClaimColumn.DAILY_AMOUNT, record.dailyAmount(), "R-VAL-02-17");
        Long deathClaim = validateNumeric(collector, row, ClaimColumn.DEATH_DISABILITY_CLAIM,
                record.deathDisabilityClaim(), "R-VAL-02-18");
        Long medicalClaim = validateNumeric(collector, row, ClaimColumn.MEDICAL_CLAIM,
                record.medicalClaim(), "R-VAL-02-19");
        Long dailyClaim = validateNumeric(collector, row, ClaimColumn.DAILY_CLAIM,
                record.dailyClaim(), "R-VAL-02-20");
        Long claimExpense = validateNumeric(collector, row, ClaimColumn.CLAIM_EXPENSE,
                record.claimExpense(), "R-VAL-02-21");

        // R-VAL-02-22 已決賠款 合計 = 身故殘廢 + 實支醫療 + 日額 + 理賠費用
        String totalSettledClaim = record.totalSettledClaim();
        Long totalValue = FieldRules.parseLongOrNull(totalSettledClaim);
        if (totalValue == null || !FieldRules.withinLength(totalSettledClaim, 8)) {
            add(collector, row, ClaimColumn.TOTAL_SETTLED_CLAIM, totalSettledClaim, "R-VAL-02-22",
                    "須為 8 碼以內之數值");
        } else if (deathClaim != null && medicalClaim != null && dailyClaim != null && claimExpense != null) {
            long expected = deathClaim + medicalClaim + dailyClaim + claimExpense;
            if (totalValue != expected) {
                add(collector, row, ClaimColumn.TOTAL_SETTLED_CLAIM, totalSettledClaim, "R-VAL-02-22",
                        "須等於身故殘廢 + 實支醫療 + 日額 + 理賠費用＝ %d".formatted(expected));
            }
        }
    }

    private static boolean isValidPrefix(String companyCode) {
        if (FieldRules.isBlank(companyCode)) {
            return false;
        }
        char prefix = companyCode.charAt(0);
        return prefix == 'N' || prefix == 'L';
    }

    private void validateRequiredText(ErrorCollector collector, int row, ClaimColumn column,
                                      String value, int maxLength, String ruleId) {
        if (FieldRules.isBlank(value)) {
            add(collector, row, column, value, ruleId, "不可空白");
        } else if (!FieldRules.withinLength(value, maxLength)) {
            add(collector, row, column, value, ruleId, "長度不可超過 %d".formatted(maxLength));
        }
    }

    private void validateRequiredRocDate(ErrorCollector collector, int row, ClaimColumn column,
                                         String value, String ruleId) {
        if (FieldRules.isBlank(value)) {
            add(collector, row, column, value, ruleId, "不可空白");
        } else if (!RocDateUtil.isValidRocDate(value)) {
            add(collector, row, column, value, ruleId, "須為 yyymmdd 共 7 碼之合法民國年日期");
        }
    }

    private Long validateNumeric(ErrorCollector collector, int row, ClaimColumn column,
                                 String value, String ruleId) {
        if (!FieldRules.withinLength(value, 8)) {
            add(collector, row, column, value, ruleId, "長度不可超過 8");
            return null;
        }
        Long parsed = FieldRules.numericOrZero(value);
        if (parsed == null) {
            add(collector, row, column, value, ruleId, "須為數值（空白以 0 計）");
        }
        return parsed;
    }

    /** 個資欄位之實際值先行遮蔽再寫入錯誤明細。 */
    private void add(ErrorCollector collector, int row, ClaimColumn column,
                     String value, String ruleId, String message) {
        String actual = appConfig.isMaskPersonalData() ? column.maskIfPersonalData(value) : value;
        collector.add(row, column.label(), actual, ruleId, message);
    }
}
