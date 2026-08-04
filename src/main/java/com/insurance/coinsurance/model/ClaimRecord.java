package com.insurance.coinsurance.model;

import com.insurance.coinsurance.constant.ClaimColumn;

import java.util.List;

/**
 * 理賠匯入檔之一列（22 欄，原始字串）。
 *
 * <p>取值一律經 {@link ClaimColumn} 之<b>位置索引</b>——本檔索引 16 與 19 之表頭皆為「日額」，
 * 且索引 21 之表頭「已決賠款 合計」含半形空白（TASK K1）。
 *
 * @param rowNumber CSV 實體行號（1-based，表頭為第 1 行）
 * @param values    22 個原始欄位值（已 trim）
 */
public record ClaimRecord(int rowNumber, List<String> values) {

    public ClaimRecord {
        values = List.copyOf(values);
    }

    /** 依欄位定義取原始值。 */
    public String value(ClaimColumn column) {
        return values.get(column.index());
    }

    public String billYear() {
        return value(ClaimColumn.BILL_YEAR);
    }

    public String billMonth() {
        return value(ClaimColumn.BILL_MONTH);
    }

    public String companyCode() {
        return value(ClaimColumn.COMPANY_CODE);
    }

    public String claimNo() {
        return value(ClaimColumn.CLAIM_NO);
    }

    public String policyNo() {
        return value(ClaimColumn.POLICY_NO);
    }

    /** 簽單年度——報表核心篩選欄位。 */
    public String underwritingYear() {
        return value(ClaimColumn.UNDERWRITING_YEAR);
    }

    public String policyStartDate() {
        return value(ClaimColumn.POLICY_START_DATE);
    }

    public String policyEndDate() {
        return value(ClaimColumn.POLICY_END_DATE);
    }

    public String insuredName() {
        return value(ClaimColumn.INSURED_NAME);
    }

    public String insuredId() {
        return value(ClaimColumn.INSURED_ID);
    }

    public String insuredBirthDate() {
        return value(ClaimColumn.INSURED_BIRTH_DATE);
    }

    public String accidentDate() {
        return value(ClaimColumn.ACCIDENT_DATE);
    }

    public String accidentReason() {
        return value(ClaimColumn.ACCIDENT_REASON);
    }

    public String settlementDate() {
        return value(ClaimColumn.SETTLEMENT_DATE);
    }

    public String deathDisabilityAmount() {
        return value(ClaimColumn.DEATH_DISABILITY_AMOUNT);
    }

    public String medicalAmount() {
        return value(ClaimColumn.MEDICAL_AMOUNT);
    }

    public String dailyAmount() {
        return value(ClaimColumn.DAILY_AMOUNT);
    }

    public String deathDisabilityClaim() {
        return value(ClaimColumn.DEATH_DISABILITY_CLAIM);
    }

    public String medicalClaim() {
        return value(ClaimColumn.MEDICAL_CLAIM);
    }

    public String dailyClaim() {
        return value(ClaimColumn.DAILY_CLAIM);
    }

    public String claimExpense() {
        return value(ClaimColumn.CLAIM_EXPENSE);
    }

    public String totalSettledClaim() {
        return value(ClaimColumn.TOTAL_SETTLED_CLAIM);
    }

    /** 簽單年度之數值。 */
    public int underwritingYearValue() {
        return Integer.parseInt(underwritingYear().trim());
    }

    /** 已決賠款合計之數值（唯一參與報表計算之金額欄）。 */
    public long totalSettledClaimValue() {
        return Long.parseLong(totalSettledClaim().trim());
    }
}
