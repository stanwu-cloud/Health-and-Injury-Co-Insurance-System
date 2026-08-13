package com.insurance.coinsurance.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M1~M10 計算結果（MAPPING §6）。
 *
 * @param totalPremium             M1 共保保費（全檔加總，含負值、不去重）
 * @param premiumByCompany         M2 各公司保費合計
 * @param totalClaim               M3 攤付共保賠款（<b>僅計簽單年度 = 設定年</b>）
 * @param claimByCompany           M4 各公司已決賠款合計（正值）
 * @param allocatedPremium         M5 各公司應分配保費（正值；N19 採差額法）
 * @param allocatedClaim           各公司應攤配賠款（正值；N19 採差額法）
 * @param managementFee            M6 各公司應繳共保管理會費
 * @param totalManagementFee       M7 共保管理費總額
 * @param balanceDue               M8 Balance Due = M1 − M3 − M7（可為負）
 * @param claimByUnderwritingYear  M9 各簽單年度之賠款合計（<b>全年度不篩選</b>，降冪）
 * @param reportYears              M10 應產出賠款 T 字帳之年度（降冪）。<b>僅在第一階段兩張報表會產出時
 *                                 才排除設定年</b>；保費檔缺檔時設定年一併列入，否則該年賠款無報表承載（P-13）
 */
public record CalculationResult(
        long totalPremium,
        Map<String, Long> premiumByCompany,
        long totalClaim,
        Map<String, Long> claimByCompany,
        Map<String, Long> allocatedPremium,
        Map<String, Long> allocatedClaim,
        Map<String, Long> managementFee,
        long totalManagementFee,
        long balanceDue,
        Map<Integer, Long> claimByUnderwritingYear,
        List<Integer> reportYears) {

    public CalculationResult {
        premiumByCompany = Map.copyOf(premiumByCompany);
        claimByCompany = Map.copyOf(claimByCompany);
        allocatedPremium = Map.copyOf(allocatedPremium);
        allocatedClaim = Map.copyOf(allocatedClaim);
        managementFee = Map.copyOf(managementFee);
        // M9 之順序即 report.json 之呈現順序，不可用 Map.copyOf（不保序）
        claimByUnderwritingYear = Collections.unmodifiableMap(new LinkedHashMap<>(claimByUnderwritingYear));
        reportYears = List.copyOf(reportYears);
    }

    /** 指定公司之保費合計；無資料回 0。 */
    public long premiumOf(String code) {
        return premiumByCompany.getOrDefault(code, 0L);
    }

    /** 指定公司之已決賠款合計；無資料回 0。 */
    public long claimOf(String code) {
        return claimByCompany.getOrDefault(code, 0L);
    }

    /** 指定簽單年度之賠款合計；無資料回 0。 */
    public long claimOfYear(int underwritingYear) {
        return claimByUnderwritingYear.getOrDefault(underwritingYear, 0L);
    }

    /**
     * 應產出之賠款 T 字帳彙總，順序同 {@link #reportYears()}（降冪）。
     *
     * <p>無可產出年度時回傳<b>空清單</b>，不拋例外（DESIGN D13）。
     */
    public List<ClaimYearSummary> claimYearSummaries() {
        return reportYears.stream()
                .map(year -> new ClaimYearSummary(year, claimOfYear(year)))
                .toList();
    }
}
