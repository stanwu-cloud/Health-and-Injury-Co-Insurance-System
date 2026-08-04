package com.insurance.coinsurance.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 全案<b>唯一</b>捨入入口，固定 {@link RoundingMode#HALF_UP}。
 *
 * <p>其他模組<b>禁止</b>自行呼叫 {@code setScale(...)} 或指定 {@link RoundingMode}：
 * {@link BigDecimal} 之預設捨入為 HALF_EVEN（銀行家捨入），誤用將產生 ±1 元帳差（TASK K3）。
 *
 * <p>HALF_UP 於負數為「遠離 0」：{@code round(-0.5) = -1}。
 */
public final class RoundingUtil {

    /** 全案固定之捨入模式。 */
    public static final RoundingMode MODE = RoundingMode.HALF_UP;

    private RoundingUtil() {
    }

    /** 四捨五入至整數元。 */
    public static long round(BigDecimal value) {
        return value.setScale(0, MODE).longValueExact();
    }

    /** 四捨五入至整數元。 */
    public static long round(double value) {
        return round(BigDecimal.valueOf(value));
    }

    /** {@code ROUND(base × rate, 0)}。 */
    public static long multiplyAndRound(long base, BigDecimal rate) {
        return round(BigDecimal.valueOf(base).multiply(rate));
    }
}
