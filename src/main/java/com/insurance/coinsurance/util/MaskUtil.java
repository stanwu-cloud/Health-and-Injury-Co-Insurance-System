package com.insurance.coinsurance.util;

/**
 * 個資遮蔽（R-RUN-06 / DESIGN §9.3）。於寫入<b>任何輸出通道前</b>套用。
 *
 * <p>身分證號格式為業務明確指定；姓名與出生日期之格式為設計提案（DESIGN D-03）。
 */
public final class MaskUtil {

    private MaskUtil() {
    }

    /** 身分證號：保留前 3 碼與後 3 碼，例 {@code A123456789 → A12****789}。 */
    public static String maskId(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() <= 6) {
            return "*".repeat(value.length());
        }
        int maskedLength = value.length() - 6;
        return value.substring(0, 3) + "*".repeat(maskedLength) + value.substring(value.length() - 3);
    }

    /** 姓名：保留首字，其餘以全形星號取代，例 {@code 王小明 → 王＊＊}。 */
    public static String maskName(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1) + "＊".repeat(value.length() - 1);
    }

    /** 出生日期：保留年，月日遮蔽，例 {@code 0671116 → 067****}。 */
    public static String maskBirthDate(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() <= 3) {
            return value;
        }
        return value.substring(0, 3) + "*".repeat(value.length() - 3);
    }
}
