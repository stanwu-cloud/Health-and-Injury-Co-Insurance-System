package com.insurance.coinsurance.constant;

/**
 * 保費匯入檔 VOLP{YYYMM}.csv 之欄位定義（MAPPING §3，共 19 欄）。
 *
 * <p><b>務必依位置索引取值，不可依欄名</b>：索引 13 與索引 16 之表頭皆為「日額」。
 * {@link #header()} 為 CSV 表頭實際值（供表頭驗證），{@link #label()} 為規格書 v1.2 名稱
 * （供錯誤訊息，可區分兩個「日額」）。
 */
public enum PremiumColumn {

    BILL_YEAR(0, "帳單年度", "帳單年度"),
    BILL_MONTH(1, "帳單月份", "帳單月份"),
    COMPANY_CODE(2, "公司", "公司"),
    POLICY_NO(3, "保單號碼", "保單號碼"),
    ENDORSEMENT_NO(4, "批單號碼", "批單號碼"),
    AGE_CODE(5, "年齡代號", "年齡代號"),
    INSURED_COUNT(6, "被保險人數", "被保險人數"),
    POLICY_START_DATE(7, "保單起日", "保單起日(民國年)"),
    POLICY_END_DATE(8, "保單迄日", "保單迄日(民國年)"),
    ENDORSEMENT_START_DATE(9, "批單起日", "批單起日(民國年)"),
    ENDORSEMENT_END_DATE(10, "批單迄日", "批單迄日(民國年)"),
    DEATH_DISABILITY_AMOUNT(11, "身故殘廢保額", "身故殘廢(保額)"),
    MEDICAL_AMOUNT(12, "實支醫療保額", "實支醫療(保額)"),
    /** 保額之日額——表頭與 {@link #DAILY_PREMIUM}（索引 16）重複。 */
    DAILY_AMOUNT(13, "日額", "日額費用(保額)"),
    DEATH_DISABILITY_PREMIUM(14, "身故殘廢保費", "身故殘廢(純保費)"),
    MEDICAL_PREMIUM(15, "實支醫療保費", "實支醫療(純保費)"),
    /** 純保費之日額——表頭與 {@link #DAILY_AMOUNT}（索引 13）重複。 */
    DAILY_PREMIUM(16, "日額", "日額費用(純保費)"),
    /** 唯一參與報表計算之金額欄；可為負值（批單沖銷）。 */
    TOTAL_PREMIUM(17, "保費合計", "保費合計(純保費)"),
    REMARK(18, "備註(批改原因)", "備註(批改原因)");

    /** 欄位總數。 */
    public static final int COLUMN_COUNT = values().length;

    private final int index;
    private final String header;
    private final String label;

    PremiumColumn(int index, String header, String label) {
        this.index = index;
        this.header = header;
        this.label = label;
    }

    public int index() {
        return index;
    }

    /** 供 R-VAL-00 表頭驗證使用之 CSV 表頭實際值。 */
    public String header() {
        return header;
    }

    /** 供錯誤訊息使用之規格書 v1.2 欄位名稱。 */
    public String label() {
        return label;
    }

    /** 依索引順序回傳預期表頭。 */
    public static String[] expectedHeaders() {
        PremiumColumn[] all = values();
        String[] headers = new String[all.length];
        for (PremiumColumn column : all) {
            headers[column.index] = column.header;
        }
        return headers;
    }
}
