package com.insurance.coinsurance.model;

/**
 * 執行請求。
 *
 * @param year  處理年（民國年）；{@code null} 表示採用設定檔之 B1
 * @param month 處理月；{@code null} 表示採用設定檔之 B2
 */
public record ExecutionRequest(Integer year, Integer month) {

    /** 採用設定檔年月。 */
    public static ExecutionRequest useSettingFile() {
        return new ExecutionRequest(null, null);
    }

    /** 年月是否由外部參數（CLI 或 GUI）覆寫。 */
    public boolean overriddenByArgs() {
        return year != null || month != null;
    }
}
