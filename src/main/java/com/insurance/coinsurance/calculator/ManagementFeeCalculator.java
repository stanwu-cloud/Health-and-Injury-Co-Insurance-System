package com.insurance.coinsurance.calculator;

import com.insurance.coinsurance.constant.CoInsuranceConstants;
import com.insurance.coinsurance.model.CoInsuranceCompany;
import com.insurance.coinsurance.model.Setting;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 共保管理費（R-CALC-07 / R-CALC-08）。
 *
 * <p><b>四捨五入順序不可調換</b>：各公司分攤保費先四捨五入（已於
 * {@link AllocationCalculator} 完成）→ 再 × 6% → 再四捨五入 → 最後加總。
 * 先加總再乘 6% 會得到不同結果（TASK K4）。
 */
@Component
public class ManagementFeeCalculator {

    /** M6 各公司應繳共保管理會費 = {@code ROUND(應分配保費 × 6%)}。 */
    public Map<String, Long> managementFeeByCompany(Map<String, Long> allocatedPremium, Setting setting) {
        Map<String, Long> fees = new LinkedHashMap<>();
        for (CoInsuranceCompany company : setting.companies()) {
            long allocated = allocatedPremium.getOrDefault(company.code(), 0L);
            fees.put(company.code(),
                    RoundingUtil.multiplyAndRound(allocated, CoInsuranceConstants.MANAGEMENT_FEE_RATE));
        }
        return fees;
    }

    /** M7 共保管理費總額 = 各公司管理費之加總（<b>最後才加總</b>）。 */
    public long totalManagementFee(Map<String, Long> managementFeeByCompany) {
        return managementFeeByCompany.values().stream().mapToLong(Long::longValue).sum();
    }

    /** M8 Balance Due = 共保保費 − 攤付共保賠款 − 共保管理費；<b>可為負，負值照實寫入</b>。 */
    public long balanceDue(long totalPremium, long totalClaim, long totalManagementFee) {
        return totalPremium - totalClaim - totalManagementFee;
    }
}
