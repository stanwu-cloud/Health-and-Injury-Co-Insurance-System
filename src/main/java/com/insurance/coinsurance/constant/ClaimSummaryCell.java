package com.insurance.coinsurance.constant;

/**
 * 賠款彙總表輸出之欄列常數（MAPPING §11，第二階段·第四張報表）。
 *
 * <p><b>本類別不得與 {@link SummaryCell} 混用</b>——兩份樣板除 A2 標題外逐格相同，
 * 欄索引與明細列號也完全一樣，但有<b>四處語意落差</b>
 * （依據：第二階段問題追蹤清單 P-15 / P-16 / P-17 / P-19 / P-21）：
 * <ol>
 *   <li>{@link #UY}（{@code A3}）之年份來源為<b>簽單年度</b>，非設定年（P-15）</li>
 *   <li>{@code C} 欄取<b>該報表之簽單年度</b>之已決賠款，非設定年（P-19）</li>
 *   <li>{@code B} / {@code F} / {@code I} 三欄<b>恆為 0</b>——本報表不讀保費匯入檔（P-17）</li>
 *   <li>{@link #OUTPUT_SHEET_NAME} 與 {@link #OUTPUT_FILE_PATTERN} 皆不同，
 *       且檔名多帶一個<b>簽單年度</b>後綴（P-16 / P-21）</li>
 * </ol>
 *
 * <p><b>常數值大量相同不代表可以共用類別</b>（DESIGN D20）：共用會讓「{@code A3} 該填哪一年」
 * 變成呼叫端的責任，而呼叫端寫錯不會編譯失敗、只會輸出錯誤年度。
 * 反之，<b>公式字串刻意共用</b>——見 {@code SummarySheetPainter}。
 *
 * <p><b>刻意不定義 {@code FORBIDDEN_CELL}</b>：{@link SummaryCell#FORBIDDEN_CELL}（{@code F27}）
 * 是第一階段<b>範例檔</b>之手誤殘值防護，{@code CLAIM_SUMMARY.xlsx} 經實測無此殘值。
 */
public final class ClaimSummaryCell {

    private ClaimSummaryCell() {
    }

    /** 樣板檔名——與第一階段之 {@code PREMIUM_SUMMARY.xlsx} 僅差 A2 標題，仍為不同檔。 */
    public static final String TEMPLATE_FILE_NAME = "CLAIM_SUMMARY.xlsx";

    /** 樣板工作表名稱。 */
    public static final String TEMPLATE_SHEET_NAME = "工作表2";

    /** 輸出工作表名稱；N 份皆同名，<b>不帶年度</b>（P-21，比照 P-09 命名法）。 */
    public static final String OUTPUT_SHEET_NAME = "共保賠款月帳單-彙總表";

    /**
     * 輸出檔名樣式，第一個 {@code %s} 代入 YYYMM、第二個代入<b>簽單年度</b>（不補零）。
     *
     * <p>報表中文名為「彙<b>總</b>表」，與第一階段之「彙<b>整</b>表」刻意區別（P-16）。
     */
    public static final String OUTPUT_FILE_PATTERN = "共保理賠_當月賠款月帳單_彙總表%s_%s年.xlsx";

    /**
     * U/Y 表頭（全形Ｕ、全形冒號），<b>來源為簽單年度 + 1911</b>（R-CALC-23）。
     *
     * <p>與同年度之賠款 T 字帳 {@code P4} 恆為同值；若誤取設定年，同一批報表會對同一件事給出兩個年份。
     */
    public static final String UY = "A3";

    /** 資料統計年月表頭——<b>仍取設定檔年月</b>，不隨簽單年度變動。 */
    public static final String PERIOD = "E3";

    /** 明細區起始列（1-based）；與 {@link SummaryCell#DETAIL_FIRST_ROW} 同值。 */
    public static final int DETAIL_FIRST_ROW = SummaryCell.DETAIL_FIRST_ROW;

    /** 保費欄之固定值——本報表不讀保費匯入檔（P-17）。 */
    public static final long PREMIUM_VALUE = 0L;
}
