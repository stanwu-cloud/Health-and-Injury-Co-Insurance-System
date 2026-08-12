package com.insurance.coinsurance;

import com.insurance.coinsurance.calculator.ClaimCalculator;
import com.insurance.coinsurance.calculator.ManagementFeeCalculator;
import com.insurance.coinsurance.constant.ClaimTAccountCell;
import com.insurance.coinsurance.model.ClaimRecord;
import com.insurance.coinsurance.model.ClaimYearSummary;
import com.insurance.coinsurance.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TC-U-15 ~ TC-U-18：第二階段賠款 T 字帳之分群、年度清單、金額配置與 U/Y 年。 */
class ClaimTAccountTest {

    private final ClaimCalculator claimCalculator = new ClaimCalculator();

    @Test
    @DisplayName("TC-U-15 簽單年度分群（M9）——全年度皆計不篩選，Σ 等於理賠檔全部賠款")
    void groupsClaimByUnderwritingYear() {
        List<ClaimRecord> claims = TestFixtures.sampleClaims();

        Map<Integer, Long> byYear = claimCalculator.claimByUnderwritingYear(claims);

        assertEquals(Map.of(115, TestFixtures.TOTAL_CLAIM,
                        114, TestFixtures.CLAIM_YEAR_114,
                        113, TestFixtures.CLAIM_YEAR_113), byYear);
        assertEquals(TestFixtures.CLAIM_WITHOUT_YEAR_FILTER,
                byYear.values().stream().mapToLong(Long::longValue).sum(),
                "Σ M9 須等於理賠檔全部已決賠款");
        assertEquals(List.of(115, 114, 113), new ArrayList<>(byYear.keySet()), "M9 須為年度降冪");

        // M9[設定年] 與 M3 為兩條獨立路徑，須得到同一個數字
        assertEquals(claimCalculator.totalClaim(
                        claimCalculator.filterByUnderwritingYear(claims, TestFixtures.YEAR)),
                byYear.get(TestFixtures.YEAR));
    }

    @Test
    @DisplayName("TC-U-16 應產出年度清單（M10）——排除設定年並降冪")
    void listsReportYearsInDescendingOrder() {
        Map<Integer, Long> byYear = claimCalculator.claimByUnderwritingYear(TestFixtures.sampleClaims());

        List<Integer> years = claimCalculator.reportYears(byYear, TestFixtures.YEAR);

        assertEquals(List.of(114, 113), years, "順序即為斷言之一，不得為 [113, 114]");
    }

    @Test
    @DisplayName("TC-U-16 追加：排除條件是「等於設定年」而非「小於設定年」")
    void keepsYearsGreaterThanConfigYear() {
        Map<Integer, Long> byYear = new LinkedHashMap<>();
        byYear.put(116, 100L);
        byYear.put(115, 200L);

        assertEquals(List.of(116), claimCalculator.reportYears(byYear, 115),
                "116 大於設定年，仍須產出");
    }

    @Test
    @DisplayName("TC-U-17 金額配置——四格同值、O6/G14 為 0，且未誤套 M8")
    void amountsShareOneValueAndDoNotUseBalanceDueFormula() {
        ClaimYearSummary summary = new ClaimYearSummary(114, TestFixtures.CLAIM_YEAR_114);

        assertEquals(TestFixtures.CLAIM_YEAR_114, summary.claim(),
                "G6 / G21 / O20 / O21 皆取自同一個 M9[y]");

        // 反向斷言（TASK K11）：以第一階段之 M8 算式（保費 0、管理費 0）會得到符號相反的值
        long byPhaseOneFormula = new ManagementFeeCalculator()
                .balanceDue(0L, TestFixtures.CLAIM_YEAR_114, 0L);
        assertEquals(-TestFixtures.CLAIM_YEAR_114, byPhaseOneFormula);
        assertNotEquals(summary.claim(), byPhaseOneFormula, "Balance Due 不得套用 M8 算式");
    }

    @Test
    @DisplayName("TC-U-17 儲存格常數——Balance Due 在 G21、新增 O20，且不定義 G20")
    void cellConstantsDifferFromPhaseOne() throws IllegalAccessException {
        assertEquals("G21", ClaimTAccountCell.BALANCE_DUE);
        assertEquals("O20", ClaimTAccountCell.RIGHT_TOTAL_UPPER);
        assertEquals("O21", ClaimTAccountCell.RIGHT_TOTAL_LOWER);

        // G20 為第一階段之 Balance Due，本階段須留空；定義了常數就有被寫入的風險（TASK K12）
        List<String> references = new ArrayList<>();
        for (Field field : ClaimTAccountCell.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                references.add((String) field.get(null));
            }
        }
        assertFalse(references.contains("G20"), "ClaimTAccountCell 不得定義 G20 常數");
        assertTrue(references.contains("G21"));
    }

    @Test
    @DisplayName("TC-U-18 U/Y 西元年——來源為簽單年度而非設定年")
    void underwritingYearDrivesAdYear() {
        assertEquals(2024, new ClaimYearSummary(113, TestFixtures.CLAIM_YEAR_113).adYear());
        assertEquals(2025, new ClaimYearSummary(114, TestFixtures.CLAIM_YEAR_114).adYear());

        int configAdYear = TestFixtures.sampleSetting().adYear();
        assertEquals(2026, configAdYear);
        assertNotEquals(configAdYear, new ClaimYearSummary(113, 0L).adYear(),
                "P4 之來源不得為設定年");
    }
}
