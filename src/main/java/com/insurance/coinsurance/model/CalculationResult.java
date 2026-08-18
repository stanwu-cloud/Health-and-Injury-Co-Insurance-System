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
 * @param claimByYearAndCompany    M12 各簽單年度 × 各公司之賠款合計（<b>全年度全公司不篩選</b>，年度降冪）。
 *                                 供賠款彙總表之 {@code C} 欄使用；<b>不得用於 {@code G} 欄之分攤</b>——
 *                                 分攤基準是該年度合計（{@code C23}），非各公司自身金額（TASK K20）
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
        List<Integer> reportYears,
        Map<Integer, Map<String, Long>> claimByYearAndCompany) {

    public CalculationResult {
        premiumByCompany = Map.copyOf(premiumByCompany);
        claimByCompany = Map.copyOf(claimByCompany);
        allocatedPremium = Map.copyOf(allocatedPremium);
        allocatedClaim = Map.copyOf(allocatedClaim);
        managementFee = Map.copyOf(managementFee);
        // M9 之順序即 report.json 之呈現順序，不可用 Map.copyOf（不保序）
        claimByUnderwritingYear = Collections.unmodifiableMap(new LinkedHashMap<>(claimByUnderwritingYear));
        reportYears = List.copyOf(reportYears);
        // M12 之年度順序比照 M9（降冪），內層每年之公司分群不保序
        Map<Integer, Map<String, Long>> copiedByYear = new LinkedHashMap<>();
        claimByYearAndCompany.forEach((year, byCompany) -> copiedByYear.put(year, Map.copyOf(byCompany)));
        claimByYearAndCompany = Collections.unmodifiableMap(copiedByYear);
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
     * 指定簽單年度、指定公司之已決賠款合計；無資料回 0（M12）。
     *
     * <p><b>僅供賠款彙總表之 {@code C} 欄使用。</b>該報表 {@code G} 欄（應攤配賠款）之分攤基準是
     * 該年度合計 {@link #claimOfYear(int)}，<b>不是</b>本方法之逐家金額——誤用時中央再保之差額法
     * 會吸收全部差異，三個合計仍然正確，只有逐家金額會歸零（TASK K20）。
     */
    public long claimOf(int underwritingYear, String code) {
        return claimByYearAndCompany.getOrDefault(underwritingYear, Map.of()).getOrDefault(code, 0L);
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
