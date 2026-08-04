package com.insurance.coinsurance.constant;

import com.insurance.coinsurance.util.MaskUtil;

import java.util.function.UnaryOperator;

/**
 * 理賠匯入檔 VOLC{YYYMM}.csv 之欄位定義（MAPPING §4，共 22 欄）。
 *
 * <p><b>務必依位置索引取值，不可依欄名</b>：索引 16 與索引 19 之表頭皆為「日額」，
 * 且索引 21 之表頭「已決賠款 合計」中間含半形空白。
 */
public enum ClaimColumn {

    BILL_YEAR(0, "帳單年度", "帳單年度"),
    BILL_MONTH(1, "帳單月份", "帳單月份"),
    COMPANY_CODE(2, "公司", "公司"),
    CLAIM_NO(3, "賠案號碼", "賠案號碼"),
    POLICY_NO(4, "保單號碼", "保單號碼"),
    /** 報表核心篩選欄位：僅計簽單年度 = 設定年之列。 */
    UNDERWRITING_YEAR(5, "簽單年度", "簽單年度"),
    POLICY_START_DATE(6, "保單起日", "保單起日(民國年)"),
    POLICY_END_DATE(7, "保單迄日", "保單迄日(民國年)"),
    /** 個資，輸出前須遮蔽。 */
    INSURED_NAME(8, "被保險人姓名", "被保險人姓名", MaskUtil::maskName),
    /** 個資，輸出前須遮蔽。 */
    INSURED_ID(9, "被保險人身分證號", "被保險人身分證號", MaskUtil::maskId),
    /** 個資，輸出前須遮蔽；僅接受 yyymmdd 共 7 碼。 */
    INSURED_BIRTH_DATE(10, "被保險人出生日期", "被保險人出生日期", MaskUtil::maskBirthDate),
    ACCIDENT_DATE(11, "出險日", "出險日(民國年)"),
    ACCIDENT_REASON(12, "出險原因", "出險原因"),
    SETTLEMENT_DATE(13, "決賠日", "決賠日"),
    DEATH_DISABILITY_AMOUNT(14, "身故殘廢保險保額", "身故殘廢(保額)"),
    MEDICAL_AMOUNT(15, "實支醫療保險保額", "實支醫療(保額)"),
    /** 保額之日額——表頭與 {@link #DAILY_CLAIM}（索引 19）重複。 */
    DAILY_AMOUNT(16, "日額", "日額費用(保額)"),
    DEATH_DISABILITY_CLAIM(17, "身故殘廢賠款金額", "身故殘廢(賠款金額)"),
    MEDICAL_CLAIM(18, "實支醫療賠款金額", "實支醫療(賠款金額)"),
    /** 賠款金額之日額——表頭與 {@link #DAILY_AMOUNT}（索引 16）重複。 */
    DAILY_CLAIM(19, "日額", "日額費用(賠款金額)"),
    CLAIM_EXPENSE(20, "理賠費用", "理賠費用"),
    /** 唯一參與報表計算之金額欄；表頭含半形空白。 */
    TOTAL_SETTLED_CLAIM(21, "已決賠款 合計", "已決賠款 合計");

    /** 欄位總數。 */
    public static final int COLUMN_COUNT = values().length;

    private final int index;
    private final String header;
    private final String label;
    private final UnaryOperator<String> mask;

    ClaimColumn(int index, String header, String label) {
        this(index, header, label, null);
    }

    ClaimColumn(int index, String header, String label, UnaryOperator<String> mask) {
        this.index = index;
        this.header = header;
        this.label = label;
        this.mask = mask;
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

    /** 是否為個資欄位。 */
    public boolean isPersonalData() {
        return mask != null;
    }

    /** 依欄位屬性遮蔽；非個資欄位原值回傳。 */
    public String maskIfPersonalData(String value) {
        return mask == null ? value : mask.apply(value);
    }

    /** 依索引順序回傳預期表頭。 */
    public static String[] expectedHeaders() {
        ClaimColumn[] all = values();
        String[] headers = new String[all.length];
        for (ClaimColumn column : all) {
            headers[column.index] = column.header;
        }
        return headers;
    }
}
