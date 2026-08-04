package com.insurance.coinsurance.model;

import java.util.Map;

/**
 * M1~M8 計算結果（MAPPING §6）。
 *
 * @param totalPremium       M1 共保保費（全檔加總，含負值、不去重）
 * @param premiumByCompany   M2 各公司保費合計
 * @param totalClaim         M3 攤付共保賠款（<b>僅計簽單年度 = 設定年</b>）
 * @param claimByCompany     M4 各公司已決賠款合計（正值）
 * @param allocatedPremium   M5 各公司應分配保費（正值；N19 採差額法）
 * @param allocatedClaim     各公司應攤配賠款（正值；N19 採差額法）
 * @param managementFee      M6 各公司應繳共保管理會費
 * @param totalManagementFee M7 共保管理費總額
 * @param balanceDue         M8 Balance Due = M1 − M3 − M7（可為負）
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
        long balanceDue) {

    public CalculationResult {
        premiumByCompany = Map.copyOf(premiumByCompany);
        claimByCompany = Map.copyOf(claimByCompany);
        allocatedPremium = Map.copyOf(allocatedPremium);
        allocatedClaim = Map.copyOf(allocatedClaim);
        managementFee = Map.copyOf(managementFee);
    }

    /** 指定公司之保費合計；無資料回 0。 */
    public long premiumOf(String code) {
        return premiumByCompany.getOrDefault(code, 0L);
    }

    /** 指定公司之已決賠款合計；無資料回 0。 */
    public long claimOf(String code) {
        return claimByCompany.getOrDefault(code, 0L);
    }
}
