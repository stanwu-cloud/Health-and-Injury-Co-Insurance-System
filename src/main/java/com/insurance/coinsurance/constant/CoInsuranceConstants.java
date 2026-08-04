package com.insurance.coinsurance.constant;

import java.math.BigDecimal;

/** 全案共用常數。 */
public final class CoInsuranceConstants {

    private CoInsuranceConstants() {
    }

    // ── 匯入檔 ──────────────────────────────────────────────

    /**
     * 匯入 CSV 之固定編碼（Big5 / CP950）。
     * <p>不做編碼偵測；以 UTF-8 解碼會使中文全部亂碼（TASK K9）。
     */
    public static final String CSV_CHARSET = "x-windows-950";

    /** CSV 欄位分隔字元（半形逗號）。 */
    public static final char CSV_DELIMITER = ',';

    /** 保費檔檔名樣式，`%s` 代入 YYYMM。 */
    public static final String PREMIUM_FILE_PATTERN = "VOLP%s.csv";

    /** 理賠檔檔名樣式，`%s` 代入 YYYMM；此檔可缺。 */
    public static final String CLAIM_FILE_PATTERN = "VOLC%s.csv";

    // ── 設定檔 ──────────────────────────────────────────────

    /** 設定檔工作表名稱。 */
    public static final String SETTING_SHEET_NAME = "工作表1";

    /** 設定年儲存格。 */
    public static final String SETTING_YEAR_CELL = "B1";

    /** 設定月儲存格。 */
    public static final String SETTING_MONTH_CELL = "B2";

    /** 公司清單起始列（1-based），往下動態讀至第一個空白列。 */
    public static final int SETTING_COMPANY_FIRST_ROW = 6;

    /** 公司名稱／代號／成分之欄號（1-based）。 */
    public static final int SETTING_NAME_COLUMN = 1;
    public static final int SETTING_CODE_COLUMN = 2;
    public static final int SETTING_SHARE_COLUMN = 3;

    /** 公司代號長度。 */
    public static final int COMPANY_CODE_LENGTH = 3;

    /** 認受成分合計必須等於 100%（業務決策 B02）。 */
    public static final BigDecimal REQUIRED_SHARE_TOTAL = new BigDecimal("1.00");

    // ── 業務規則 ────────────────────────────────────────────

    /**
     * 中央再保之公司代號。該公司之應分配保費／應攤配賠款採<b>差額法</b>：
     * 分攤金額 = 總額 − 其餘公司加總，用以吸收其餘公司四捨五入之尾差。
     * <p><b>依代號判定，不可依列號</b>（TASK K5）。
     */
    public static final String REINSURER_CODE = "N19";

    /** 共保管理費費率 5%。 */
    public static final BigDecimal MANAGEMENT_FEE_RATE = new BigDecimal("0.05");

    /** 民國年轉西元年之差值。 */
    public static final int ROC_YEAR_OFFSET = 1911;

    /** 匯入檔帳單年度之下限（民國年）。 */
    public static final int MIN_ROC_YEAR = 103;

    // ── 輸出 ────────────────────────────────────────────────

    /**
     * 金額格之會計格式。同時滿足：含千分號、負數以 {@code -} 前綴、值為 0 時顯示 {@code -}。
     */
    public static final String ACCOUNTING_FORMAT = "_-* #,##0_-;\\-* #,##0_-;_-* \"-\"??_-;_-@_-";

    /** 執行報告檔名。 */
    public static final String REPORT_JSON_FILE_NAME = "report.json";

    /** 備份目錄之時間戳樣式。 */
    public static final String BACKUP_TIMESTAMP_PATTERN = "yyyyMMddHHmmss";
}
