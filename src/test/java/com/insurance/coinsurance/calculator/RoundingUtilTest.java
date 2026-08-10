package com.insurance.coinsurance.calculator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** TC-U-03：全案捨入固定 HALF_UP，不得為 BigDecimal 預設之 HALF_EVEN。 */
class RoundingUtilTest {

    @Test
    @DisplayName("0.5 進位為 1、1.5 進位為 2（HALF_EVEN 會得 2 與 2）")
    void roundsHalfUpNotHalfEven() {
        assertEquals(1L, RoundingUtil.round(new BigDecimal("0.5")));
        assertEquals(2L, RoundingUtil.round(new BigDecimal("1.5")));
        assertEquals(3L, RoundingUtil.round(new BigDecimal("2.5")));

        // 對照組：HALF_EVEN 會把 2.5 捨為 2，足以區分兩種模式
        assertNotEquals(RoundingUtil.round(new BigDecimal("2.5")),
                new BigDecimal("2.5").setScale(0, RoundingMode.HALF_EVEN).longValueExact());
    }

    @Test
    @DisplayName("負數為遠離 0：-0.5 得 -1")
    void roundsAwayFromZeroForNegatives() {
        assertEquals(-1L, RoundingUtil.round(new BigDecimal("-0.5")));
        assertEquals(-2L, RoundingUtil.round(new BigDecimal("-1.5")));
    }

    @Test
    @DisplayName("乘以成分後捨入")
    void multiplyAndRound() {
        assertEquals(26259L, RoundingUtil.multiplyAndRound(350123L, new BigDecimal("0.075")));
        assertEquals(24509L, RoundingUtil.multiplyAndRound(350123L, new BigDecimal("0.07")));
        assertEquals(21007L, RoundingUtil.multiplyAndRound(350123L, new BigDecimal("0.06")));
        assertEquals(RoundingMode.HALF_UP, RoundingUtil.MODE);
    }
}
