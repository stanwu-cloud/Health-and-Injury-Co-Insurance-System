package com.insurance.coinsurance.calculator;

import com.insurance.coinsurance.model.CoInsuranceCompany;
import com.insurance.coinsurance.model.Setting;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 應分配保費與應攤配賠款（R-CALC-05 / R-CALC-06）。
 *
 * <p>非中央再保：{@code ROUND(基礎 × 認受成分)}（HALF_UP）。<br>
 * <b>中央再保（代號 N19）採差額法</b>：{@code 基礎 − 其餘公司加總}，用以吸收其餘公司
 * 四捨五入之尾差（115/5 樣本為 3 元），<b>不是</b> {@code 基礎 × 12%}。
 *
 * <p>中央再保<b>依代號判定，不依列號</b>——設定檔中 N19 位於第 14 列而非最末列（TASK K5）。
 *
 * <p>回傳皆為<b>正值</b>；寫入報表時再乘 −1。
 */
@Component
public class AllocationCalculator {

    /**
     * 依基礎金額與各公司認受成分計算分攤金額。
     *
     * @param base    分攤基礎（M1 共保保費或 M3 攤付共保賠款）
     * @param setting 設定檔內容
     * @return 公司代號 → 分攤金額（正值）
     */
    public Map<String, Long> allocate(long base, Setting setting) {
        Map<String, Long> allocated = new LinkedHashMap<>();
        long othersTotal = 0L;
        CoInsuranceCompany reinsurer = null;

        for (CoInsuranceCompany company : setting.companies()) {
            if (company.isReinsurer()) {
                reinsurer = company;
                continue;
            }
            long amount = RoundingUtil.multiplyAndRound(base, company.share());
            allocated.put(company.code(), amount);
            othersTotal += amount;
        }

        if (reinsurer != null) {
            // 差額法：吸收其餘公司之四捨五入尾差
            allocated.put(reinsurer.code(), base - othersTotal);
        }
        return allocated;
    }
}
