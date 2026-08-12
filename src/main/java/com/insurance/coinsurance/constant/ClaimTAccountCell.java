package com.insurance.coinsurance.constant;

/**
 * 賠款 T 字帳輸出之儲存格常數（MAPPING §10，第二階段）。
 *
 * <p><b>本類別不得與 {@link TAccountCell} 混用</b>——兩者樣板僅差 A2 標題，儲存格位置看似相同，
 * 但有<b>五處語意落差</b>（依據：第二階段問題追蹤清單 P-02 / P-03 / P-04）：
 * <ol>
 *   <li>{@link #UY_YEAR}（{@code P4}）之年份來源為<b>簽單年度</b>，非設定年</li>
 *   <li>{@code G20} <b>留空、不寫入</b>；{@link TAccountCell#BALANCE_DUE} 用它放 Balance Due，
 *       故本類別<b>刻意不定義 G20 常數</b></li>
 *   <li>{@link #BALANCE_DUE}（{@code G21}）為 Balance Due；同格於第一階段是共保保費</li>
 *   <li>{@link #RIGHT_TOTAL_UPPER}（{@code O20}）為本階段新增，第一階段未使用該格</li>
 *   <li>{@link #PREMIUM} / {@link #MGMT_FEE} 固定寫 {@code 0}，非計算值</li>
 * </ol>
 *
 * <p><b>Balance Due 不套用 M8 算式</b>：本報表之 Balance Due 直接等於該簽單年度之賠款總和（正值）；
 * 誤套第一階段之 {@code M1 − M3 − M7} 會得符號相反之 −38,331（TASK K11）。
 */
public final class ClaimTAccountCell {

    private ClaimTAccountCell() {
    }

    /** 樣板檔名——與第一階段之 {@code PREMIUM_T_ACCOUNT.xlsx} 僅差 A2 標題，仍為不同檔。 */
    public static final String TEMPLATE_FILE_NAME = "CLAIM_T_ACCOUNT.xlsx";

    /** 樣板工作表名稱。 */
    public static final String TEMPLATE_SHEET_NAME = "工作表1";

    /** 輸出工作表名稱；N 份皆同名，<b>不帶年度</b>（R-OUT-05）。 */
    public static final String OUTPUT_SHEET_NAME = "共保賠款月帳單-T字帳";

    /** 輸出檔名樣式，第一個 {@code %s} 代入 YYYMM、第二個代入<b>簽單年度</b>（不補零）。 */
    public static final String OUTPUT_FILE_PATTERN = "共保理賠_當月賠款月帳單_T字帳報表%s_%s年.xlsx";

    /** 資料統計年月，<b>單格</b>寫入 {@code 115 年 05 月}；{@code J3} 不寫入（P-01）。 */
    public static final String PERIOD = "I3";

    /**
     * U/Y 之西元年，<b>來源為簽單年度 + 1911</b>（R-CALC-20）；
     * 標籤 {@code U/Y:} 為樣板既有之 O4，不覆寫。
     */
    public static final String UY_YEAR = "P4";

    /** 攤付共保賠款——該簽單年度之已決賠款合計（M9[y]）。 */
    public static final String CLAIM = "G6";

    /** 共保保費——本報表無保費概念，<b>固定寫 0</b>（P-05）。 */
    public static final String PREMIUM = "O6";

    /** 共保管理會費——同上，<b>固定寫 0</b>（P-05）。 */
    public static final String MGMT_FEE = "G14";

    /** Balance Due——<b>{@code G21} 而非 {@code G20}</b>，值為該年度賠款總和（P-03）。 */
    public static final String BALANCE_DUE = "G21";

    /** 左側合計（上）——規格書之稱呼，實際位於報表右半邊（P-04）。 */
    public static final String RIGHT_TOTAL_UPPER = "O20";

    /** 左側合計（下）——同上。 */
    public static final String RIGHT_TOTAL_LOWER = "O21";
}
