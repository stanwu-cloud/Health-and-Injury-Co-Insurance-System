package com.insurance.coinsurance.validator;

/** 匯入檔欄位檢核之共用判斷（長度、數值、空白處理）。 */
public final class FieldRules {

    private FieldRules() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 是否為整數字串（允許前導負號）；空白視為非整數。 */
    public static boolean isInteger(String value) {
        if (isBlank(value)) {
            return false;
        }
        String text = value.trim();
        int start = (text.charAt(0) == '-' || text.charAt(0) == '+') ? 1 : 0;
        if (start >= text.length()) {
            return false;
        }
        for (int i = start; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** 解析為整數；非整數回傳 {@code null}。 */
    public static Long parseLongOrNull(String value) {
        if (!isInteger(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 數值欄位取值；<b>空白以 0 計</b>。非數值回傳 {@code null}。 */
    public static Long numericOrZero(String value) {
        return isBlank(value) ? 0L : parseLongOrNull(value);
    }

    /** 字串長度；null 視為 0。 */
    public static int length(String value) {
        return value == null ? 0 : value.trim().length();
    }

    /** 長度是否未超過上限（空白視為通過）。 */
    public static boolean withinLength(String value, int max) {
        return length(value) <= max;
    }
}
