package com.insurance.coinsurance.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TC-U-11 ~ TC-U-13：民國年換算、日期格式、個資遮蔽。 */
class UtilTest {

    @Test
    @DisplayName("民國年 115 → 西元年 2026；年月字串補零為 11505")
    void rocConversion() {
        assertEquals(2026, RocDateUtil.toAdYear(115));
        assertEquals("11505", RocDateUtil.rocYearMonth(115, 5));
        assertEquals("11512", RocDateUtil.rocYearMonth(115, 12));
    }

    @Test
    @DisplayName("出生日期僅接受 7 碼 yyymmdd，6 碼不合法")
    void rocDateAcceptsSevenDigitsOnly() {
        assertTrue(RocDateUtil.isValidRocDate("0671116"));
        assertTrue(RocDateUtil.isValidRocDate("1150604"));
        assertFalse(RocDateUtil.isValidRocDate("671116"), "6 碼不合法");
        assertFalse(RocDateUtil.isValidRocDate("11506041"), "8 碼不合法");
        assertFalse(RocDateUtil.isValidRocDate(""));
        assertFalse(RocDateUtil.isValidRocDate("1151301"), "月份 13 不合法");
        assertFalse(RocDateUtil.isValidRocDate("1150230"), "115 年 2 月無 30 日");
        assertTrue(RocDateUtil.isValidRocDate("1130229"), "民國 113 年＝西元 2024 為閏年");
    }

    @Test
    @DisplayName("個資遮蔽格式")
    void masking() {
        assertEquals("A12****789", MaskUtil.maskId("A123456789"));
        assertEquals("王＊＊", MaskUtil.maskName("王小明"));
        assertEquals("067****", MaskUtil.maskBirthDate("0671116"));
    }
}
