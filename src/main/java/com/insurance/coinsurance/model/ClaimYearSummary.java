package com.insurance.coinsurance.model;

import com.insurance.coinsurance.constant.CoInsuranceConstants;

/**
 * 單一簽單年度之賠款彙總（第二階段，MAPPING §10）——賠款 T 字帳每產出一份即對應一筆。
 *
 * @param underwritingYear 簽單年度（民國年，取自理賠檔索引 5）
 * @param claim            該年度之已決賠款合計（M9[y]，正值）
 */
public record ClaimYearSummary(int underwritingYear, long claim) {

    /**
     * U/Y 之西元年：<b>簽單年度</b> + 1911（R-CALC-20）。
     *
     * <p>與第一階段之 {@code Setting.adYear()} 換算式相同但<b>來源不同</b>——
     * 此處是簽單年度（113 → 2024），非設定年（115 → 2026）。
     */
    public int adYear() {
        return underwritingYear + CoInsuranceConstants.ROC_YEAR_OFFSET;
    }
}
