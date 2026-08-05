package com.insurance.coinsurance.calculator;

import com.insurance.coinsurance.constant.CoInsuranceConstants;
import com.insurance.coinsurance.model.ClaimRecord;
import com.insurance.coinsurance.model.CoInsuranceCompany;
import com.insurance.coinsurance.model.PremiumRecord;
import com.insurance.coinsurance.model.Setting;
import com.insurance.coinsurance.support.TestFixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** TC-U-04 ~ TC-U-10：以 115 年 5 月樣本驗證 M1~M8。 */
class CalculationTest {

    private static Setting setting;
    private static List<PremiumRecord> premiums;
    private static List<ClaimRecord> claims;

    private final PremiumCalculator premiumCalculator = new PremiumCalculator();
    private final ClaimCalculator claimCalculator = new ClaimCalculator();
    private final AllocationCalculator allocationCalculator = new AllocationCalculator();
    private final ManagementFeeCalculator managementFeeCalculator = new ManagementFeeCalculator();

    @BeforeAll
    static void loadSample() {
        setting = TestFixtures.sampleSetting();
        premiums = TestFixtures.samplePremiums();
        claims = TestFixtures.sampleClaims();
    }

    @Test
    @DisplayName("M1 共保保費 = 350,123（含負值、不去重）")
    void totalPremium() {
        assertEquals(2193, premiums.size());
        assertEquals(TestFixtures.TOTAL_PREMIUM, premiumCalculator.totalPremium(premiums));
        assertEquals(TestFixtures.TOTAL_PREMIUM,
                premiumCalculator.premiumByCompany(premiums).get("N05"));
    }

    @Test
    @DisplayName("M3 攤付共保賠款 = 126,931；未篩簽單年度會得 197,722")
    void totalClaimRequiresUnderwritingYearFilter() {
        assertEquals(14, claims.size());

        List<ClaimRecord> filtered = claimCalculator.filterByUnderwritingYear(claims, setting.year());
        assertEquals(TestFixtures.TOTAL_CLAIM, claimCalculator.totalClaim(filtered));
        assertEquals(TestFixtures.TOTAL_CLAIM, claimCalculator.claimByCompany(filtered).get("N05"));

        // 反向：不篩年度之加總不得等於正確值，且應為文件所載之錯誤值
        long unfiltered = claimCalculator.totalClaim(claims);
        assertEquals(TestFixtures.CLAIM_WITHOUT_YEAR_FILTER, unfiltered);
        assertNotEquals(TestFixtures.TOTAL_CLAIM, unfiltered, "漏篩簽單年度必須造成不同結果");
    }

    @Test
    @DisplayName("M5 應分配保費：非中央再保合計 308,105、中央再保採差額法得 42,018")
    void allocationUsesDifferentialMethodForReinsurer() {
        Map<String, Long> allocated = allocationCalculator.allocate(TestFixtures.TOTAL_PREMIUM, setting);

        long othersTotal = allocated.entrySet().stream()
                .filter(entry -> !CoInsuranceConstants.REINSURER_CODE.equals(entry.getKey()))
                .mapToLong(Map.Entry::getValue).sum();
        assertEquals(TestFixtures.NON_REINSURER_ALLOCATED_TOTAL, othersTotal);
        assertEquals(TestFixtures.REINSURER_ALLOCATED_PREMIUM,
                allocated.get(CoInsuranceConstants.REINSURER_CODE));

        // 差額法吸收 3 元尾差：帳面 12% 乘算為 42,015
        assertEquals(42_015L,
                RoundingUtil.multiplyAndRound(TestFixtures.TOTAL_PREMIUM, new BigDecimal("0.12")));
        assertEquals(TestFixtures.TOTAL_PREMIUM,
                allocated.values().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    @DisplayName("應攤配賠款之中央再保差額 = 15,228")
    void allocatedClaim() {
        Map<String, Long> allocated = allocationCalculator.allocate(TestFixtures.TOTAL_CLAIM, setting);
        assertEquals(TestFixtures.REINSURER_ALLOCATED_CLAIM,
                allocated.get(CoInsuranceConstants.REINSURER_CODE));
        assertEquals(TestFixtures.TOTAL_CLAIM,
                allocated.values().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    @DisplayName("中央再保依代號判定，設定檔列序改變不影響結果")
    void reinsurerIdentifiedByCodeNotRowPosition() {
        Map<String, Long> original = allocationCalculator.allocate(TestFixtures.TOTAL_PREMIUM, setting);

        List<CoInsuranceCompany> shuffled = new ArrayList<>(setting.companies());
        Collections.reverse(shuffled);
        Setting reordered = new Setting(setting.year(), setting.month(), shuffled);

        Map<String, Long> actual = allocationCalculator.allocate(TestFixtures.TOTAL_PREMIUM, reordered);
        assertEquals(original.get(CoInsuranceConstants.REINSURER_CODE),
                actual.get(CoInsuranceConstants.REINSURER_CODE));
        assertEquals(original, actual);
    }

    @Test
    @DisplayName("M6/M7/M8：管理費 17,504、N05 = 1,050、N19 = 2,101、Balance Due = 205,688")
    void managementFeeAndBalanceDue() {
        Map<String, Long> allocated = allocationCalculator.allocate(TestFixtures.TOTAL_PREMIUM, setting);
        Map<String, Long> fees = managementFeeCalculator.managementFeeByCompany(allocated, setting);

        assertEquals(1_050L, fees.get("N05"));
        assertEquals(2_101L, fees.get(CoInsuranceConstants.REINSURER_CODE));

        long total = managementFeeCalculator.totalManagementFee(fees);
        assertEquals(TestFixtures.TOTAL_MANAGEMENT_FEE, total);
        assertEquals(TestFixtures.BALANCE_DUE, managementFeeCalculator.balanceDue(
                TestFixtures.TOTAL_PREMIUM, TestFixtures.TOTAL_CLAIM, total));
    }

    @Test
    @DisplayName("管理費四捨五入順序不可調換：先加總再乘 5% 會得到不同結果")
    void managementFeeRoundingOrderMatters() {
        Map<String, Long> allocated = allocationCalculator.allocate(TestFixtures.TOTAL_PREMIUM, setting);
        long correct = managementFeeCalculator.totalManagementFee(
                managementFeeCalculator.managementFeeByCompany(allocated, setting));

        long sumFirst = RoundingUtil.multiplyAndRound(
                allocated.values().stream().mapToLong(Long::longValue).sum(),
                CoInsuranceConstants.MANAGEMENT_FEE_RATE);

        assertEquals(TestFixtures.TOTAL_MANAGEMENT_FEE, correct);
        assertNotEquals(correct, sumFirst, "先加總再乘 6% 之結果應與逐家捨入後加總不同");
    }
}
