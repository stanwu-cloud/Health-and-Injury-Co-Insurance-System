package com.insurance.coinsurance.constant;

/** 彙整表輸出之欄列常數（MAPPING §8）。 */
public final class SummaryCell {

    private SummaryCell() {
    }

    /** 樣板檔名。 */
    public static final String TEMPLATE_FILE_NAME = "PREMIUM_SUMMARY.xlsx";

    /** 樣板工作表名稱。 */
    public static final String TEMPLATE_SHEET_NAME = "工作表2";

    /** 輸出工作表名稱。 */
    public static final String OUTPUT_SHEET_NAME = "共保月帳單-彙整表";

    /** 輸出檔名樣式，`%s` 代入 YYYMM。 */
    public static final String OUTPUT_FILE_PATTERN = "共保保費_當月共保月帳單_彙整表%s.xlsx";

    /** U/Y 表頭（全形Ｕ、全形冒號）。 */
    public static final String UY = "A3";

    /** 資料統計年月表頭。 */
    public static final String PERIOD = "E3";

    /** 明細區起始列（1-based）。 */
    public static final int DETAIL_FIRST_ROW = 7;

    /** 明細區欄號（0-based，供 POI 使用）。 */
    public static final int COL_COMPANY_NAME = 0;   // A 公司名稱（不覆寫）
    public static final int COL_PREMIUM = 1;        // B 保費
    public static final int COL_CLAIM = 2;          // C 已付賠款
    public static final int COL_NET_RECEIVABLE = 3; // D 應收（付）淨額
    public static final int COL_SHARE = 4;          // E 共保成分
    public static final int COL_ALLOCATED_PREMIUM = 5; // F 應分配保費
    public static final int COL_ALLOCATED_CLAIM = 6;   // G 應攤配賠款
    public static final int COL_NET_ALLOCATED = 7;     // H 應攤收(付)淨額
    public static final int COL_MGMT_FEE = 8;          // I 應繳共保管理會費
    public static final int COL_NET_PREMIUM = 9;       // J 淨收(付)共保費

    /** 明細區最末欄（0-based）。 */
    public static final int COL_LAST = COL_NET_PREMIUM;

    /** 共保管理費費率，供公式字串使用。 */
    public static final String MGMT_FEE_RATE = "0.06";

    /**
     * 範例檔之手誤殘值位置，<b>不得寫入</b>。
     */
    public static final String FORBIDDEN_CELL = "F27";
}
