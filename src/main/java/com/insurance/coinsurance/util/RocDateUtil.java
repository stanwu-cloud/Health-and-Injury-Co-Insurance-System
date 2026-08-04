package com.insurance.coinsurance.util;

import com.insurance.coinsurance.constant.CoInsuranceConstants;

/** 民國年與 {@code yyymmdd} 日期字串之工具。 */
public final class RocDateUtil {

    private RocDateUtil() {
    }

    /** 民國年 → 西元年。 */
    public static int toAdYear(int rocYear) {
        return rocYear + CoInsuranceConstants.ROC_YEAR_OFFSET;
    }

    /** 民國年月 → {@code YYYMM}（月份補零 2 位）。 */
    public static String rocYearMonth(int rocYear, int month) {
        return "%d%02d".formatted(rocYear, month);
    }

    /**
     * 是否為合法之民國年日期字串。
     * <p><b>僅接受 7 碼</b> {@code yyymmdd}；6 碼（如 {@code 671116}）不合法。
     */
    public static boolean isValidRocDate(String value) {
        if (value == null || value.length() != 7) {
            return false;
        }
        for (int i = 0; i < 7; i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        int year = Integer.parseInt(value.substring(0, 3));
        int month = Integer.parseInt(value.substring(3, 5));
        int day = Integer.parseInt(value.substring(5, 7));
        if (year < 1 || month < 1 || month > 12 || day < 1) {
            return false;
        }
        return day <= daysInMonth(toAdYear(year), month);
    }

    private static int daysInMonth(int adYear, int month) {
        return switch (month) {
            case 4, 6, 9, 11 -> 30;
            case 2 -> isLeapYear(adYear) ? 29 : 28;
            default -> 31;
        };
    }

    private static boolean isLeapYear(int adYear) {
        return (adYear % 4 == 0 && adYear % 100 != 0) || adYear % 400 == 0;
    }
}
