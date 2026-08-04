package com.insurance.coinsurance.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * TC-U-01 / TC-U-02：欄位索引與表頭字串須與 MAPPING §3 / §4 完全一致。
 *
 * <p>索引錯位將導致全案計算錯誤，故逐項斷言。
 */
class ColumnDefinitionTest {

    @Test
    @DisplayName("保費檔共 19 欄，索引連續且表頭與 MAPPING §3 一致")
    void premiumColumns() {
        assertEquals(19, PremiumColumn.COLUMN_COUNT);

        String[] expected = {
                "帳單年度", "帳單月份", "公司", "保單號碼", "批單號碼", "年齡代號", "被保險人數",
                "保單起日", "保單迄日", "批單起日", "批單迄日", "身故殘廢保額", "實支醫療保額", "日額",
                "身故殘廢保費", "實支醫療保費", "日額", "保費合計", "備註(批改原因)"};
        assertArrayEqualsWithIndex(expected, PremiumColumn.expectedHeaders());

        for (int i = 0; i < PremiumColumn.values().length; i++) {
            assertEquals(i, PremiumColumn.values()[i].index(),
                    "第 %d 個常數之索引須為 %d".formatted(i, i));
        }
        assertEquals(17, PremiumColumn.TOTAL_PREMIUM.index(), "保費合計為唯一參與計算之金額欄");
    }

    @Test
    @DisplayName("理賠檔共 22 欄，索引連續且表頭與 MAPPING §4 一致")
    void claimColumns() {
        assertEquals(22, ClaimColumn.COLUMN_COUNT);

        String[] expected = {
                "帳單年度", "帳單月份", "公司", "賠案號碼", "保單號碼", "簽單年度", "保單起日", "保單迄日",
                "被保險人姓名", "被保險人身分證號", "被保險人出生日期", "出險日", "出險原因", "決賠日",
                "身故殘廢保險保額", "實支醫療保險保額", "日額", "身故殘廢賠款金額", "實支醫療賠款金額",
                "日額", "理賠費用", "已決賠款 合計"};
        assertArrayEqualsWithIndex(expected, ClaimColumn.expectedHeaders());

        for (int i = 0; i < ClaimColumn.values().length; i++) {
            assertEquals(i, ClaimColumn.values()[i].index(),
                    "第 %d 個常數之索引須為 %d".formatted(i, i));
        }
        assertEquals(5, ClaimColumn.UNDERWRITING_YEAR.index(), "簽單年度為報表核心篩選欄位");
        assertEquals(21, ClaimColumn.TOTAL_SETTLED_CLAIM.index());
        assertEquals("已決賠款 合計", ClaimColumn.TOTAL_SETTLED_CLAIM.header(), "表頭含半形空白");
    }

    @Test
    @DisplayName("重複之「日額」欄名必須以索引區分，label 亦須可辨識")
    void duplicatedHeadersAreDistinguishedByIndex() {
        assertEquals(PremiumColumn.DAILY_AMOUNT.header(), PremiumColumn.DAILY_PREMIUM.header());
        assertNotEquals(PremiumColumn.DAILY_AMOUNT.index(), PremiumColumn.DAILY_PREMIUM.index());
        assertNotEquals(PremiumColumn.DAILY_AMOUNT.label(), PremiumColumn.DAILY_PREMIUM.label());

        assertEquals(ClaimColumn.DAILY_AMOUNT.header(), ClaimColumn.DAILY_CLAIM.header());
        assertNotEquals(ClaimColumn.DAILY_AMOUNT.index(), ClaimColumn.DAILY_CLAIM.index());
        assertNotEquals(ClaimColumn.DAILY_AMOUNT.label(), ClaimColumn.DAILY_CLAIM.label());
    }

    @Test
    @DisplayName("個資欄位標記正確")
    void personalDataColumns() {
        assertEquals(3, Arrays.stream(ClaimColumn.values()).filter(ClaimColumn::isPersonalData).count());
        for (ClaimColumn column : new ClaimColumn[]{
                ClaimColumn.INSURED_NAME, ClaimColumn.INSURED_ID, ClaimColumn.INSURED_BIRTH_DATE}) {
            org.junit.jupiter.api.Assertions.assertTrue(column.isPersonalData(), column.name());
        }
    }

    private static void assertArrayEqualsWithIndex(String[] expected, String[] actual) {
        assertEquals(expected.length, actual.length, "欄位數不符");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], "索引 %d 之表頭不符".formatted(i));
        }
    }
}
