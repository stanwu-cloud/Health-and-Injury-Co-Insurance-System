package com.insurance.coinsurance.calculator;

import com.insurance.coinsurance.model.ClaimRecord;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 攤付共保賠款彙總（R-CALC-03 / R-CALC-04）與簽單年度分群（R-CALC-17 / R-CALC-18）。
 *
 * <p>第一階段之 M3 / M4 <b>必須先以「簽單年度 = 設定年」篩選再加總</b>；<b>只篩年、不篩月</b>。
 * 漏篩會使 115 年 5 月樣本由正確之 126,931 變成 197,722（TASK K2）。
 *
 * <p>第二階段之 M9 / M10 恰為互補：<b>全年度皆計不篩選</b>，再於 M10 排除設定年。
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

    /**
     * M9 各簽單年度之賠款合計（R-CALC-17）——<b>傳入全部理賠資料，不做任何篩選</b>。
     *
     * <p>樣本結果為 {@code {115: 126931, 114: 38331, 113: 32460}}，加總 197,722
     * 即理賠檔全部已決賠款；其中 {@code M9[設定年]} 恆等於 M3。
     *
     * @param records 全部理賠資料列（未經 {@link #filterByUnderwritingYear} 篩選）
     * @return 依年度<b>降冪</b>之分群結果；理賠檔缺檔時為空 Map（R-EXC-01）
     */
    public Map<Integer, Long> claimByUnderwritingYear(List<ClaimRecord> records) {
        Map<Integer, Long> byYear = new TreeMap<>(Comparator.reverseOrder());
        for (ClaimRecord record : records) {
            byYear.merge(record.underwritingYearValue(), record.totalSettledClaimValue(), Long::sum);
        }
        return new LinkedHashMap<>(byYear);
    }

    /**
     * M10 應產出賠款 T 字帳之年度清單（R-CALC-18）。
     *
     * <p><b>排除設定年是有條件的</b>（P-13）：設定年之賠款平時由第一階段報表的「攤付共保賠款」承載，
     * 故不必再出一份賠款 T 字帳。但保費檔缺檔時第一階段兩張報表<b>不產出</b>，那筆賠款就沒有任何
     * 報表承載——此時設定年<b>必須一併產出</b>，否則金額憑空消失。
     *
     * <p>由此得到全案不變式：{@code Σ M9 == (第一階段報表產出 ? M3 : 0) + Σ M10 各年度賠款}，
     * 即<b>每筆已決賠款必定且只被一張報表承載一次</b>（由 {@code verifyConsistency()} 把關）。
     *
     * <p>排除條件是「<b>等於</b>設定年」而非「小於設定年」——若出現大於設定年之簽單年度
     * （如設定年 115 之資料含 116），依規則仍須產出。
     *
     * @param premiumReportsProduced 第一階段兩張報表本次是否會產出（即保費檔是否存在）；
     *                               為 {@code false} 時<b>不排除</b>設定年
     * @return 依年度<b>降冪</b>之清單；理賠檔無任何資料時為空清單（R-OUT-09，屬正常業務狀態）
     */
    public List<Integer> reportYears(Map<Integer, Long> claimByUnderwritingYear, int configYear,
                                     boolean premiumReportsProduced) {
        return claimByUnderwritingYear.keySet().stream()
                .filter(year -> !premiumReportsProduced || year != configYear)
                .sorted(Comparator.reverseOrder())
                .toList();
    }
}
