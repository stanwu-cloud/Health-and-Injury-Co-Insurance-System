package com.insurance.coinsurance.calculator;

import com.insurance.coinsurance.model.PremiumRecord;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 共保保費彙總（R-CALC-01 / R-CALC-02）。
 *
 * <p><b>全檔加總，含負值（批單沖銷）、不去重、不過濾</b>。
 */
@Component
public class PremiumCalculator {

    /** M1 共保保費：保費合計欄之全檔加總。 */
    public long totalPremium(List<PremiumRecord> records) {
        long total = 0L;
        for (PremiumRecord record : records) {
            total += record.totalPremiumValue();
        }
        return total;
    }

    /** M2 各公司保費：依公司代號分群加總。 */
    public Map<String, Long> premiumByCompany(List<PremiumRecord> records) {
        Map<String, Long> byCompany = new LinkedHashMap<>();
        for (PremiumRecord record : records) {
            byCompany.merge(record.companyCode(), record.totalPremiumValue(), Long::sum);
        }
        return byCompany;
    }
}
