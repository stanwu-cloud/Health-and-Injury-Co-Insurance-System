package com.insurance.coinsurance.model;

import com.insurance.coinsurance.constant.PremiumColumn;

import java.util.List;

/**
 * 保費匯入檔之一列（19 欄，原始字串）。
 *
 * <p>取值一律經 {@link PremiumColumn} 之<b>位置索引</b>——本檔索引 13 與 16 之表頭皆為「日額」，
 * 依欄名取值必然取錯（TASK K1）。數值轉換延後至檢核通過後，以免解析失敗中斷錯誤蒐集。
 *
 * @param rowNumber CSV 實體行號（1-based，表頭為第 1 行）
 * @param values    19 個原始欄位值（已 trim）
 */
public record PremiumRecord(int rowNumber, List<String> values) {

    public PremiumRecord {
        values = List.copyOf(values);
    }

    /** 依欄位定義取原始值。 */
    public String value(PremiumColumn column) {
        return values.get(column.index());
    }

    public String billYear() {
        return value(PremiumColumn.BILL_YEAR);
    }

    public String billMonth() {
        return value(PremiumColumn.BILL_MONTH);
    }

    public String companyCode() {
        return value(PremiumColumn.COMPANY_CODE);
    }

    public String policyNo() {
        return value(PremiumColumn.POLICY_NO);
    }

    public String endorsementNo() {
        return value(PremiumColumn.ENDORSEMENT_NO);
    }

    public String ageCode() {
        return value(PremiumColumn.AGE_CODE);
    }

    public String insuredCount() {
        return value(PremiumColumn.INSURED_COUNT);
    }

    public String policyStartDate() {
        return value(PremiumColumn.POLICY_START_DATE);
    }

    public String policyEndDate() {
        return value(PremiumColumn.POLICY_END_DATE);
    }

    public String endorsementStartDate() {
        return value(PremiumColumn.ENDORSEMENT_START_DATE);
    }

    public String endorsementEndDate() {
        return value(PremiumColumn.ENDORSEMENT_END_DATE);
    }

    public String deathDisabilityAmount() {
        return value(PremiumColumn.DEATH_DISABILITY_AMOUNT);
    }

    public String medicalAmount() {
        return value(PremiumColumn.MEDICAL_AMOUNT);
    }

    public String dailyAmount() {
        return value(PremiumColumn.DAILY_AMOUNT);
    }

    public String deathDisabilityPremium() {
        return value(PremiumColumn.DEATH_DISABILITY_PREMIUM);
    }

    public String medicalPremium() {
        return value(PremiumColumn.MEDICAL_PREMIUM);
    }

    public String dailyPremium() {
        return value(PremiumColumn.DAILY_PREMIUM);
    }

    public String totalPremium() {
        return value(PremiumColumn.TOTAL_PREMIUM);
    }

    public String remark() {
        return value(PremiumColumn.REMARK);
    }

    /**
     * 保費合計之數值（唯一參與報表計算之金額欄）。
     * <b>可為負值</b>（批單沖銷），加總時不可過濾。
     */
    public long totalPremiumValue() {
        return Long.parseLong(totalPremium().trim());
    }
}
