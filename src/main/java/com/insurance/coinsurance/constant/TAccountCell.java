package com.insurance.coinsurance.constant;

/**
 * T 字帳輸出之儲存格常數（MAPPING §7）。
 *
 * <p><b>注意</b>：規格書 v1.0 之儲存格位置整體偏一列，本表為 v1.2 更正後之位置（TASK K6）。
 */
public final class TAccountCell {

    private TAccountCell() {
    }

    /** 樣板檔名。 */
    public static final String TEMPLATE_FILE_NAME = "PREMIUM_T_ACCOUNT.xlsx";

    /** 樣板工作表名稱。 */
    public static final String TEMPLATE_SHEET_NAME = "工作表1";

    /** 輸出工作表名稱。 */
    public static final String OUTPUT_SHEET_NAME = "共保月帳單-T字帳";

    /** 輸出檔名樣式，`%s` 代入 YYYMM。 */
    public static final String OUTPUT_FILE_PATTERN = "共保保費_當月共保月帳單_T字帳報表%s.xlsx";

    /** 資料統計年月，<b>單格</b>寫入 {@code 115 年 05 月}。 */
    public static final String PERIOD = "I3";

    /**
     * U/Y 之西元年，<b>僅寫年份不含標籤</b>；標籤 {@code U/Y:} 為樣板既有之 O4，不覆寫。
     */
    public static final String UY_YEAR = "P4";

    /** 攤付共保賠款（M3）。 */
    public static final String CLAIM = "G6";

    /** 共保保費（M1）。 */
    public static final String PREMIUM = "O6";

    /** 共保管理會費（M7）。 */
    public static final String MGMT_FEE = "G14";

    /** Balance Due（M8 = M1 − M3 − M7）；為負時填負數，不得為空。 */
    public static final String BALANCE_DUE = "G20";

    /** 左側合計（依規格書 v1.2 定義＝共保保費，非欄位加總）。 */
    public static final String LEFT_TOTAL = "G21";

    /** 右側合計（依規格書 v1.2 定義＝共保保費，非欄位加總）。 */
    public static final String RIGHT_TOTAL = "O21";
}
