package com.insurance.coinsurance.calculator;

import com.insurance.coinsurance.model.ClaimRecord;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 攤付共保賠款彙總（R-CALC-03 / R-CALC-04）。
 *
 * <p><b>必須先以「簽單年度 = 設定年」篩選再加總</b>；<b>只篩年、不篩月</b>。
 * 漏篩會使 115 年 5 月樣本由正確之 126,931 變成 197,722（TASK K2）。
 */
@Component
public class ClaimCalculator {

    /** 篩出簽單年度 = 設定年之理賠資料列。 */
    public List<ClaimRecord> filterByUnderwritingYear(List<ClaimRecord> records, int configYear) {
        return records.stream()
                .filter(record -> record.underwritingYearValue() == configYear)
                .toList();
    }

    /** M3 攤付共保賠款：已篩選之已決賠款合計加總（正值）。 */
    public long totalClaim(List<ClaimRecord> filtered) {
        long total = 0L;
        for (ClaimRecord record : filtered) {
            total += record.totalSettledClaimValue();
        }
        return total;
    }

    /** M4 各公司已付賠款：已篩選之資料依公司代號分群加總（正值）。 */
    public Map<String, Long> claimByCompany(List<ClaimRecord> filtered) {
        Map<String, Long> byCompany = new LinkedHashMap<>();
        for (ClaimRecord record : filtered) {
            byCompany.merge(record.companyCode(), record.totalSettledClaimValue(), Long::sum);
        }
        return byCompany;
    }
}
